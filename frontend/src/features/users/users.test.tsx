import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { formatDateTime } from '../../components/format'
import { me, page, problem, signInAs, userResponse } from '../../test/fixtures'
import { renderApp } from '../../test/render'
import { server } from '../../test/server'

const BOB = userResponse({ id: 'usr-bob', fullName: 'Bob Durand', email: 'bob@exemple.fr', role: 'DEVELOPER', version: 3 })
const SELF = userResponse({ id: me().id, fullName: 'Alice Martin', email: 'alice@exemple.fr', role: 'SUPER_ADMIN' })
const TEMPORARY = 'Tmp-Pass-Kq7wXz2'
const EXPIRES = '2026-11-07T10:00:00Z'

function list(captured: URL[] = []) {
  return http.get('/api/v1/users', ({ request }) => {
    captured.push(new URL(request.url))
    return HttpResponse.json(page([SELF, BOB]))
  })
}

test('temporary password is shown once with its expiry and dropped on close', async () => {
  signInAs('SUPER_ADMIN')
  let sent: unknown
  server.use(list(), http.post('/api/v1/users', async ({ request }) => {
    sent = await request.json()
    return HttpResponse.json(
      { user: userResponse({ id: 'usr-new', fullName: 'Chloé Petit' }), temporaryPassword: TEMPORARY, temporaryPasswordExpiresAt: EXPIRES },
      { status: 201 },
    )
  }))
  const user = userEvent.setup()
  const { queryClient } = renderApp('/users')

  await user.click(await screen.findByRole('button', { name: 'Nouvel utilisateur' }))
  const form = await screen.findByRole('dialog')
  await user.type(within(form).getByLabelText(/^Email/), 'chloe@exemple.fr')
  await user.type(within(form).getByLabelText(/^Nom complet/), 'Chloé Petit')
  await user.click(within(form).getByRole('combobox', { name: /^Rôle/ }))
  await user.click(await screen.findByRole('option', { name: 'DevOps' }))
  await user.click(within(form).getByRole('button', { name: 'Créer' }))

  const shown = await screen.findByRole('dialog', { name: 'Mot de passe temporaire' })
  expect(within(shown).getByText(TEMPORARY)).toBeInTheDocument()
  expect(within(shown).getByRole('button', { name: 'Copier' })).toBeInTheDocument()
  expect(within(shown).getByText(new RegExp(formatDateTime(EXPIRES)))).toBeInTheDocument()
  expect(sent).toEqual({ email: 'chloe@exemple.fr', fullName: 'Chloé Petit', role: 'DEVOPS' })

  await user.click(within(shown).getByRole('button', { name: "J'ai noté le mot de passe" }))

  await waitFor(() => expect(screen.queryByText(TEMPORARY)).toBeNull())
  // Plus aucune trace dans le cache des mutations (spec §12.4).
  await waitFor(() => {
    const cached = JSON.stringify(queryClient.getMutationCache().getAll().map((mutation) => mutation.state.data))
    expect(cached).not.toContain(TEMPORARY)
  })
})

test('patch sends only changed fields and version', async () => {
  signInAs('SUPER_ADMIN')
  let sent: unknown
  server.use(list(), http.patch('/api/v1/users/usr-bob', async ({ request }) => {
    sent = await request.json()
    return HttpResponse.json({ ...BOB, role: 'DEVOPS', version: 4 })
  }))
  const user = userEvent.setup()
  renderApp('/users')

  await user.click(await screen.findByRole('button', { name: 'Modifier Bob Durand' }))
  const dialog = await screen.findByRole('dialog')
  await user.click(within(dialog).getByRole('combobox', { name: /^Rôle/ }))
  await user.click(await screen.findByRole('option', { name: 'DevOps' }))
  await user.click(within(dialog).getByRole('button', { name: 'Enregistrer' }))

  await waitFor(() => expect(sent).toEqual({ role: 'DEVOPS', version: 3 }))
})

test('reset password asks for confirmation, then shows the temporary password', async () => {
  signInAs('SUPER_ADMIN')
  let resets = 0
  server.use(list(), http.post('/api/v1/users/usr-bob/reset-password', () => {
    resets++
    return HttpResponse.json({ temporaryPassword: TEMPORARY, temporaryPasswordExpiresAt: EXPIRES })
  }))
  const user = userEvent.setup()
  renderApp('/users')

  await user.click(await screen.findByRole('button', { name: 'Réinitialiser le mot de passe de Bob Durand' }))
  expect(resets).toBe(0)
  await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmer' }))

  const shown = await screen.findByRole('dialog', { name: 'Mot de passe temporaire' })
  expect(within(shown).getByText(TEMPORARY)).toBeInTheDocument()
  expect(resets).toBe(1)
})

test('a guardrail refusal shows the API message', async () => {
  signInAs('SUPER_ADMIN')
  server.use(list(), http.delete('/api/v1/users/usr-bob', () =>
    problem(409, 'last-super-admin', { detail: 'Impossible de retirer le dernier SUPER_ADMIN actif.' })))
  const user = userEvent.setup()
  renderApp('/users')

  await user.click(await screen.findByRole('button', { name: 'Supprimer Bob Durand' }))
  await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmer' }))

  expect(await screen.findByText('Impossible de retirer le dernier SUPER_ADMIN actif.')).toBeInTheDocument()
})

test('user:read without user:manage lists users without any action', async () => {
  signInAs('INFRA_ADMIN')
  server.use(list())

  renderApp('/users')

  expect(await screen.findByText('Bob Durand')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Nouvel utilisateur' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Modifier Bob Durand' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Supprimer Bob Durand' })).toBeNull()
})

test('own account: no delete, and role and status cannot be changed', async () => {
  signInAs('SUPER_ADMIN')
  server.use(list())
  const user = userEvent.setup()
  renderApp('/users')

  await user.click(await screen.findByRole('button', { name: 'Modifier Alice Martin' }))
  const dialog = await screen.findByRole('dialog')

  expect(screen.queryByRole('button', { name: 'Supprimer Alice Martin' })).toBeNull()
  expect(within(dialog).getByRole('combobox', { name: /^Rôle/ })).toBeDisabled()
  expect(within(dialog).getByRole('combobox', { name: /^Statut/ })).toBeDisabled()
})

test('filters from the URL are sent to the API', async () => {
  signInAs('SUPER_ADMIN')
  const requests: URL[] = []
  server.use(list(requests))

  renderApp('/users?role=DEVOPS&status=DISABLED&q=bob')

  await screen.findByText('Bob Durand')
  const query = requests.at(-1)?.searchParams
  expect(query?.get('role')).toBe('DEVOPS')
  expect(query?.get('status')).toBe('DISABLED')
  expect(query?.get('q')).toBe('bob')
  expect(query?.get('sort')).toBe('fullName,asc')
})
