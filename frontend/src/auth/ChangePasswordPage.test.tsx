import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { authResponse, problem } from '../test/fixtures'
import { renderApp } from '../test/render'
import { server } from '../test/server'

function sessionOf(mustChangePassword: boolean) {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse('t', { mustChangePassword }))))
}

async function fill(user: ReturnType<typeof userEvent.setup>, current: string, next: string, confirmation = next) {
  await user.type(await screen.findByLabelText('Mot de passe actuel'), current)
  await user.type(screen.getByLabelText('Nouveau mot de passe'), next)
  await user.type(screen.getByLabelText('Confirmation du nouveau mot de passe'), confirmation)
  await user.click(screen.getByRole('button', { name: 'Changer le mot de passe' }))
}

test('maps 400 currentPassword error to the field', async () => {
  sessionOf(false)
  server.use(http.post('/api/v1/auth/change-password', () =>
    problem(400, 'validation-error', { errors: [{ field: 'currentPassword', message: 'mot de passe actuel incorrect' }] })))
  const user = userEvent.setup()
  renderApp('/change-password')

  await fill(user, 'mauvais', 'un nouveau mot de passe solide')

  expect(await screen.findByText('mot de passe actuel incorrect')).toBeInTheDocument()
  // L'erreur est reliée au champ : un lecteur d'écran l'annonce.
  expect(screen.getByLabelText('Mot de passe actuel')).toHaveAttribute('aria-invalid', 'true')
  expect(screen.getByLabelText('Mot de passe actuel')).toHaveAccessibleDescription('mot de passe actuel incorrect')
})

test('checks the confirmation before calling the API', async () => {
  sessionOf(false)
  const user = userEvent.setup()
  renderApp('/change-password')

  await fill(user, 'ancien mot de passe', 'un nouveau mot de passe solide', 'une autre saisie')

  expect(screen.getByText('Les deux saisies ne correspondent pas.')).toBeInTheDocument()
})

test('checks the minimum length before calling the API', async () => {
  sessionOf(false)
  const user = userEvent.setup()
  renderApp('/change-password')

  await fill(user, 'ancien mot de passe', 'court')

  expect(screen.getByText('12 caractères minimum.')).toBeInTheDocument()
})

test('a forced change explains why and opens the application once done', async () => {
  sessionOf(true)
  let sent: unknown
  server.use(http.post('/api/v1/auth/change-password', async ({ request }) => {
    sent = await request.json()
    return HttpResponse.json(authResponse('t2', { mustChangePassword: false }))
  }))
  const user = userEvent.setup()
  renderApp('/')

  expect(await screen.findByText('Votre mot de passe doit être changé avant de continuer.')).toBeInTheDocument()
  await fill(user, 'Temporaire-2026', 'un nouveau mot de passe solide')

  expect(await screen.findByRole('heading', { name: 'Tableau de bord' })).toBeInTheDocument()
  expect(sent).toEqual({ currentPassword: 'Temporaire-2026', newPassword: 'un nouveau mot de passe solide' })
})

test('a forced change can be abandoned by logging out', async () => {
  sessionOf(true)
  let loggedOut = false
  server.use(http.post('/api/v1/auth/logout', () => {
    loggedOut = true
    return new HttpResponse(null, { status: 204 })
  }))
  const user = userEvent.setup()
  renderApp('/change-password')

  await user.click(await screen.findByRole('button', { name: 'Se déconnecter' }))

  expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
  expect(loggedOut).toBe(true)
})

test('a voluntary change can be cancelled', async () => {
  sessionOf(false)
  const user = userEvent.setup()
  renderApp('/change-password')

  await user.click(await screen.findByRole('link', { name: 'Annuler' }))

  expect(await screen.findByRole('heading', { name: 'Profil' })).toBeInTheDocument()
})
