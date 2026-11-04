import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, test, vi } from 'vitest'
import { routes } from '../router'
import { authResponse, problem } from '../test/fixtures'
import { renderApp } from '../test/render'
import { server } from '../test/server'
import { useApi } from './AuthProvider'
import { ChangePasswordPage } from './ChangePasswordPage'
import { RequireAuth } from './guards'
import { LoginPage } from './LoginPage'
import { getUser } from './session'

/** Page de test : appelle l'API par le client de l'AuthProvider. */
function Probe({ path }: { path: '/api/v1/me' | '/api/v1/servers' }) {
  const api = useApi()
  return (
    <button type="button" onClick={() => void api.GET(path)}>
      Appeler
    </button>
  )
}

function renderProbe(path: '/api/v1/me' | '/api/v1/servers') {
  return renderApp('/probe', [
    { path: '/login', element: <LoginPage /> },
    { path: '/change-password', element: <RequireAuth><ChangePasswordPage /></RequireAuth> },
    { path: '/probe', element: <RequireAuth><Probe path={path} /></RequireAuth> },
  ])
}

test('restores the session on load', async () => {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse('t', { fullName: 'Alice Martin' }))))

  renderApp('/', routes)

  expect(await screen.findByRole('button', { name: 'Alice Martin' })).toBeInTheDocument()
  expect(getUser()?.fullName).toBe('Alice Martin')
})

test('logout calls the API, clears the session and tells the other tabs', async () => {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse('t', { fullName: 'Alice Martin' }))))
  const logoutCalls = vi.fn()
  server.use(http.post('/api/v1/auth/logout', () => {
    logoutCalls()
    return new HttpResponse(null, { status: 204 })
  }))
  const otherTab = new BroadcastChannel('sentinel-auth')
  const received = vi.fn()
  otherTab.onmessage = (event: MessageEvent) => received(event.data)
  const user = userEvent.setup()
  renderApp('/')

  await user.click(await screen.findByRole('button', { name: 'Alice Martin' }))
  await user.click(screen.getByRole('menuitem', { name: 'Se déconnecter' }))

  expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
  expect(logoutCalls).toHaveBeenCalledOnce()
  expect(getUser()).toBeNull()
  await vi.waitFor(() => expect(received).toHaveBeenCalledWith(expect.objectContaining({ type: 'logout' })))
  otherTab.close()
})

test('logout drops the cached data of the previous user', async () => {
  server.use(
    http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse('t', { fullName: 'Alice Martin' }))),
    http.post('/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),
  )
  const user = userEvent.setup()
  const { queryClient } = renderApp('/')
  await user.click(await screen.findByRole('button', { name: 'Alice Martin' }))
  queryClient.setQueryData(['users'], [{ email: 'secret@exemple.fr' }])

  await user.click(screen.getByRole('menuitem', { name: 'Se déconnecter' }))

  await screen.findByRole('heading', { name: 'Connexion' })
  expect(queryClient.getQueryData(['users'])).toBeUndefined()
})

test('a lost session drops the cached data too', async () => {
  let refreshes = 0
  server.use(
    http.post('/api/v1/auth/refresh', () => (++refreshes === 1 ? HttpResponse.json(authResponse('t')) : problem(401, 'unauthorized'))),
    http.get('/api/v1/me', () => problem(401, 'unauthorized')),
  )
  const user = userEvent.setup()
  const { queryClient } = renderProbe('/api/v1/me')
  await screen.findByRole('button', { name: 'Appeler' })
  queryClient.setQueryData(['users'], [{ email: 'secret@exemple.fr' }])

  await user.click(screen.getByRole('button', { name: 'Appeler' }))

  await screen.findByRole('heading', { name: 'Connexion' })
  expect(queryClient.getQueryData(['users'])).toBeUndefined()
})

test('logout still ends the local session when the API is unreachable', async () => {
  server.use(
    http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse('t', { fullName: 'Alice Martin' }))),
    http.post('/api/v1/auth/logout', () => HttpResponse.error()),
  )
  const user = userEvent.setup()
  renderApp('/')

  await user.click(await screen.findByRole('button', { name: 'Alice Martin' }))
  await user.click(screen.getByRole('menuitem', { name: 'Se déconnecter' }))

  expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
  expect(getUser()).toBeNull()
})

test('a session lost during use leads back to the login page', async () => {
  let refreshes = 0
  server.use(
    // Le premier refresh restaure la session ; le suivant échoue (session révoquée entre-temps).
    http.post('/api/v1/auth/refresh', () => (++refreshes === 1 ? HttpResponse.json(authResponse('t')) : problem(401, 'unauthorized'))),
    http.get('/api/v1/me', () => problem(401, 'unauthorized')),
  )
  const user = userEvent.setup()
  const { router } = renderProbe('/api/v1/me')

  await user.click(await screen.findByRole('button', { name: 'Appeler' }))

  expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
  expect(getUser()).toBeNull()
  // Après reconnexion, l'utilisateur revient sur la page qu'il quittait.
  expect(router.state.location.state).toMatchObject({ from: { pathname: '/probe' } })
})

test('a 403 password-change-required leads to the password change', async () => {
  server.use(
    http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse('t'))),
    http.get('/api/v1/servers', () => problem(403, 'password-change-required')),
  )
  const user = userEvent.setup()
  renderProbe('/api/v1/servers')

  await user.click(await screen.findByRole('button', { name: 'Appeler' }))

  expect(await screen.findByRole('heading', { name: 'Changer le mot de passe' })).toBeInTheDocument()
  // L'API fait foi : le changement devient imposé, même si la session ne l'annonçait pas.
  expect(screen.getByText('Votre mot de passe doit être changé avant de continuer.')).toBeInTheDocument()
  expect(getUser()?.mustChangePassword).toBe(true)
})
