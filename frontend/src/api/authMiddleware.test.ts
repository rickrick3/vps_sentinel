import { http, HttpResponse } from 'msw'
import { beforeEach, expect, test, vi } from 'vitest'
import { setSession } from '../auth/session'
import { authResponse, me, problem } from '../test/fixtures'
import { server } from '../test/server'
import { authMiddleware } from './authMiddleware'
import { createApiClient } from './client'

function setup({ refreshSucceeds = true } = {}) {
  const refresh = vi.fn(async () => {
    if (!refreshSucceeds) {
      return null
    }
    await new Promise((resolve) => setTimeout(resolve, 5))
    const response = authResponse('new-token')
    setSession(response)
    return response
  })
  const onSessionLost = vi.fn()
  const onPasswordChangeRequired = vi.fn()
  const api = createApiClient([authMiddleware({ refresh, onSessionLost, onPasswordChangeRequired })])
  return { api, refresh, onSessionLost, onPasswordChangeRequired }
}

/** `GET /me` n'accepte que ce jeton ; renvoie l'en-tête Authorization reçu à chaque appel. */
function meAcceptingOnly(token: string): (string | null)[] {
  const received: (string | null)[] = []
  server.use(http.get('/api/v1/me', ({ request }) => {
    const authorization = request.headers.get('Authorization')
    received.push(authorization)
    return authorization === `Bearer ${token}` ? HttpResponse.json(me()) : problem(401, 'unauthorized')
  }))
  return received
}

beforeEach(() => {
  setSession(authResponse('old-token'))
})

test('adds the bearer token', async () => {
  const received = meAcceptingOnly('old-token')
  const { api, refresh } = setup()

  const { data } = await api.GET('/api/v1/me')

  expect(data?.email).toBe('alice@exemple.fr')
  expect(received).toEqual(['Bearer old-token'])
  expect(refresh).not.toHaveBeenCalled()
})

test('retries once after refresh on 401', async () => {
  const received = meAcceptingOnly('new-token')
  const { api, refresh } = setup()

  const { data, response } = await api.GET('/api/v1/me')

  expect(response.status).toBe(200)
  expect(data?.email).toBe('alice@exemple.fr')
  expect(refresh).toHaveBeenCalledOnce()
  expect(received).toEqual(['Bearer old-token', 'Bearer new-token'])
})

test('three simultaneous 401s trigger a single refresh', async () => {
  meAcceptingOnly('new-token')
  const { api, refresh } = setup()

  const responses = await Promise.all([api.GET('/api/v1/me'), api.GET('/api/v1/me'), api.GET('/api/v1/me')])

  expect(refresh).toHaveBeenCalledOnce()
  expect(responses.map(({ response }) => response.status)).toEqual([200, 200, 200])
})

test('the replayed request carries the same body', async () => {
  const bodies: unknown[] = []
  server.use(http.post('/api/v1/servers', async ({ request }) => {
    bodies.push(await request.json())
    return request.headers.get('Authorization') === 'Bearer new-token'
      ? HttpResponse.json({ id: 'srv-1' }, { status: 201 })
      : problem(401, 'unauthorized')
  }))
  const { api } = setup()

  const { response } = await api.POST('/api/v1/servers', { body: { name: 'VPS-01', tags: ['web'] } })

  expect(response.status).toBe(201)
  expect(bodies).toEqual([{ name: 'VPS-01', tags: ['web'] }, { name: 'VPS-01', tags: ['web'] }])
})

test('login 401 does not trigger refresh', async () => {
  let authorization: string | null = 'unset'
  server.use(http.post('/api/v1/auth/login', ({ request }) => {
    authorization = request.headers.get('Authorization')
    return problem(401, 'unauthorized')
  }))
  const { api, refresh, onSessionLost } = setup()

  const { response } = await api.POST('/api/v1/auth/login', { body: { email: 'a@exemple.fr', password: 'x' } })

  expect(response.status).toBe(401)
  expect(refresh).not.toHaveBeenCalled()
  expect(onSessionLost).not.toHaveBeenCalled()
  // Les endpoints de session ignorent le Bearer : il n'est pas envoyé.
  expect(authorization).toBeNull()
})

test('refresh and logout are excluded too', async () => {
  server.use(
    http.post('/api/v1/auth/refresh', () => problem(401, 'unauthorized')),
    http.post('/api/v1/auth/logout', () => problem(401, 'unauthorized')),
  )
  const { api, refresh } = setup()

  await api.POST('/api/v1/auth/refresh')
  await api.POST('/api/v1/auth/logout')

  expect(refresh).not.toHaveBeenCalled()
})

test('change-password 401 does trigger refresh', async () => {
  server.use(http.post('/api/v1/auth/change-password', ({ request }) =>
    request.headers.get('Authorization') === 'Bearer new-token'
      ? HttpResponse.json(authResponse('newer-token'))
      : problem(401, 'unauthorized')))
  const { api, refresh } = setup()

  const { response } = await api.POST('/api/v1/auth/change-password', {
    body: { currentPassword: 'ancien', newPassword: 'un nouveau mot de passe' },
  })

  expect(response.status).toBe(200)
  expect(refresh).toHaveBeenCalledOnce()
})

test('session lost when refresh fails', async () => {
  const received = meAcceptingOnly('new-token')
  const { api, onSessionLost } = setup({ refreshSucceeds: false })

  const { response } = await api.GET('/api/v1/me')

  expect(response.status).toBe(401)
  expect(onSessionLost).toHaveBeenCalledOnce()
  expect(received).toHaveLength(1)
})

test('a request still refused after the refresh is not retried again', async () => {
  const received = meAcceptingOnly('never-valid')
  const { api, refresh, onSessionLost } = setup()

  const { response } = await api.GET('/api/v1/me')

  expect(response.status).toBe(401)
  expect(refresh).toHaveBeenCalledOnce()
  expect(received).toEqual(['Bearer old-token', 'Bearer new-token'])
  expect(onSessionLost).toHaveBeenCalledOnce()
})

test('a 403 password-change-required is reported, a plain 403 is not', async () => {
  server.use(
    http.get('/api/v1/servers', () => problem(403, 'password-change-required')),
    http.get('/api/v1/projects', () => problem(403, 'forbidden')),
  )
  const { api, onPasswordChangeRequired } = setup()

  const servers = await api.GET('/api/v1/servers')
  await api.GET('/api/v1/projects')

  expect(onPasswordChangeRequired).toHaveBeenCalledOnce()
  // Le corps reste lisible par l'appelant.
  expect(servers.error).toMatchObject({ type: 'urn:sentinel:problem:password-change-required' })
})
