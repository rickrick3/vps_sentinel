import { screen } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { authResponse, problem, ROLE_PERMISSIONS } from '../test/fixtures'
import { renderApp } from '../test/render'
import { server } from '../test/server'

function sessionOf(user: Parameters<typeof authResponse>[1]) {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse('t', user))))
}

test('redirects to /login without a session', async () => {
  server.use(http.post('/api/v1/auth/refresh', () => problem(401, 'unauthorized')))

  const { router } = renderApp('/servers')

  expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
  expect(router.state.location.pathname).toBe('/login')
})

test('shows a loader, not the login form, while the session is restored', async () => {
  server.use(http.post('/api/v1/auth/refresh', async () => {
    await delay(50)
    return HttpResponse.json(authResponse('t'))
  }))

  renderApp('/')

  expect(screen.getByLabelText('Chargement')).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Connexion' })).toBeNull()
  expect(await screen.findByRole('heading', { name: 'Tableau de bord' })).toBeInTheDocument()
})

test('redirects to /change-password when mustChangePassword', async () => {
  sessionOf({ mustChangePassword: true })

  const { router } = renderApp('/profile')

  expect(await screen.findByRole('heading', { name: 'Changer le mot de passe' })).toBeInTheDocument()
  expect(router.state.location.pathname).toBe('/change-password')
})

test('refuses a page outside the role', async () => {
  sessionOf({ role: 'VIEWER', permissions: ROLE_PERMISSIONS.VIEWER })

  renderApp('/users')

  expect(await screen.findByRole('heading', { name: 'Accès refusé' })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Utilisateurs' })).toBeNull()
})

test('opens a page granted to the role', async () => {
  sessionOf({ role: 'INFRA_ADMIN', permissions: ROLE_PERMISSIONS.INFRA_ADMIN })

  renderApp('/audit')

  expect(await screen.findByRole('heading', { name: "Journal d'audit" })).toBeInTheDocument()
})

test('every authenticated user reaches the profile', async () => {
  sessionOf({ fullName: 'Alice Martin', role: 'VIEWER', permissions: ROLE_PERMISSIONS.VIEWER })

  renderApp('/profile')

  expect(await screen.findByRole('heading', { name: 'Profil' })).toBeInTheDocument()
  expect(screen.getByText('Lecteur')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Changer le mot de passe' })).toHaveAttribute('href', '/change-password')
})
