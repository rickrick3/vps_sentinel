import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { authResponse, problem } from '../test/fixtures'
import { renderApp } from '../test/render'
import { server } from '../test/server'

beforeEach(() => {
  // Aucune session à restaurer au chargement.
  server.use(http.post('/api/v1/auth/refresh', () => problem(401, 'unauthorized')))
})

afterEach(() => {
  vi.useRealTimers()
})

async function submitLogin(user: ReturnType<typeof userEvent.setup>, email = 'alice@exemple.fr', password = 'un mot de passe') {
  await user.type(await screen.findByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Mot de passe'), password)
  await user.click(screen.getByRole('button', { name: 'Se connecter' }))
}

test('login shows generic message on 401', async () => {
  server.use(http.post('/api/v1/auth/login', () => problem(401, 'unauthorized', { detail: 'Bad credentials' })))
  const user = userEvent.setup()
  renderApp('/login')

  await submitLogin(user)

  expect(await screen.findByText('Identifiants invalides.')).toBeInTheDocument()
  expect(screen.queryByText('Bad credentials')).toBeNull()
})

test('login shows countdown on 429', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  server.use(http.post('/api/v1/auth/login', () => problem(429, 'login-throttled', {}, { 'Retry-After': '30' })))
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
  renderApp('/login')

  await submitLogin(user)

  expect(await screen.findByText('Trop de tentatives. Réessayez dans 30 secondes.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Se connecter' })).toBeDisabled()
  await act(() => vi.advanceTimersByTimeAsync(1_000))
  expect(screen.getByText('Trop de tentatives. Réessayez dans 29 secondes.')).toBeInTheDocument()
  await act(() => vi.advanceTimersByTimeAsync(29_000))
  expect(screen.queryByText(/Réessayez dans/)).toBeNull()
  expect(screen.getByRole('button', { name: 'Se connecter' })).toBeEnabled()
})

test('login submit disabled while pending', async () => {
  let release!: () => void
  server.use(http.post('/api/v1/auth/login', async () => {
    await new Promise<void>((resolve) => {
      release = resolve
    })
    return problem(401, 'unauthorized')
  }))
  const user = userEvent.setup()
  renderApp('/login')

  await submitLogin(user)

  await waitFor(() => expect(screen.getByRole('button', { name: 'Se connecter' })).toBeDisabled())
  release()
  await screen.findByText('Identifiants invalides.')
  expect(screen.getByRole('button', { name: 'Se connecter' })).toBeEnabled()
})

test('requires both fields before calling the API', async () => {
  const user = userEvent.setup()
  renderApp('/login')

  await user.click(await screen.findByRole('button', { name: 'Se connecter' }))

  expect(screen.getByText('Saisissez votre email.')).toBeInTheDocument()
  expect(screen.getByText('Saisissez votre mot de passe.')).toBeInTheDocument()
})

test('maps validation errors to the fields', async () => {
  server.use(http.post('/api/v1/auth/login', () =>
    problem(400, 'validation-error', { errors: [{ field: 'email', message: '254 caractères maximum' }] })))
  const user = userEvent.setup()
  renderApp('/login')

  await submitLogin(user)

  expect(await screen.findByText('254 caractères maximum')).toBeInTheDocument()
})

test('successful login opens the requested page', async () => {
  server.use(http.post('/api/v1/auth/login', async ({ request }) => {
    const body = await request.json()
    return body && typeof body === 'object' && 'email' in body && body.email === 'alice@exemple.fr'
      ? HttpResponse.json(authResponse('t'))
      : problem(401, 'unauthorized')
  }))
  const user = userEvent.setup()
  renderApp('/servers')

  await submitLogin(user)

  expect(await screen.findByRole('heading', { name: 'VPS' })).toBeInTheDocument()
})

test('a first login goes to the password change', async () => {
  server.use(http.post('/api/v1/auth/login', () => HttpResponse.json(authResponse('t', { mustChangePassword: true }))))
  const user = userEvent.setup()
  renderApp('/login')

  await submitLogin(user)

  expect(await screen.findByRole('heading', { name: 'Changer le mot de passe' })).toBeInTheDocument()
})

test('an authenticated user is sent to the dashboard', async () => {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse('t'))))

  renderApp('/login')

  expect(await screen.findByRole('heading', { name: 'Tableau de bord' })).toBeInTheDocument()
})
