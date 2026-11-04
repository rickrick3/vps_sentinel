import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { auditLog, page, signInAs } from '../../test/fixtures'
import { renderApp } from '../../test/render'
import { server } from '../../test/server'

function logs(captured: URL[] = [], items = [auditLog()]) {
  return http.get('/api/v1/audit-logs', ({ request }) => {
    captured.push(new URL(request.url))
    return HttpResponse.json(page(items))
  })
}

test('audit filters are sent as query params', async () => {
  signInAs('INFRA_ADMIN')
  const requests: URL[] = []
  server.use(logs(requests))

  renderApp('/audit?action=SERVER_CREATED&targetType=SERVER&targetId=srv-1')

  expect(await screen.findByText('VPS créé')).toBeInTheDocument()
  const query = requests.at(-1)?.searchParams
  expect(query?.get('action')).toBe('SERVER_CREATED')
  expect(query?.get('targetType')).toBe('SERVER')
  expect(query?.get('targetId')).toBe('srv-1')
  expect(query?.get('sort')).toBe('occurredAt,desc')
})

test('choosing a filter updates the URL and the request', async () => {
  signInAs('INFRA_ADMIN')
  const requests: URL[] = []
  server.use(logs(requests))
  const user = userEvent.setup()
  const { router } = renderApp('/audit')

  await user.click(await screen.findByRole('combobox', { name: 'Cible' }))
  await user.click(await screen.findByRole('option', { name: 'VPS' }))

  await waitFor(() => expect(new URLSearchParams(router.state.location.search).get('targetType')).toBe('SERVER'))
  await waitFor(() => expect(requests.at(-1)?.searchParams.get('targetType')).toBe('SERVER'))
})

test('a date range is sent as ISO-8601 instants', async () => {
  signInAs('INFRA_ADMIN')
  const requests: URL[] = []
  server.use(logs(requests))
  renderApp('/audit')

  // Saisie d'une date et heure locales : user-event ne sait pas taper dans un champ datetime-local de jsdom.
  fireEvent.change(await screen.findByLabelText('Du'), { target: { value: '2026-11-01T08:30' } })

  const expected = new Date('2026-11-01T08:30').toISOString()
  await waitFor(() => expect(requests.at(-1)?.searchParams.get('from')).toBe(expected))
  expect(screen.getByLabelText('Du')).toHaveValue('2026-11-01T08:30')
})

test('actors are named, and details are shown as plain text', async () => {
  signInAs('INFRA_ADMIN')
  server.use(logs([], [
    auditLog({ id: 'log-1', actorType: 'SYSTEM', actorEmail: null, actorUserId: null, details: { name: '<img src=x onerror=alert(1)>' } }),
    auditLog({ id: 'log-2', actorType: 'ANONYMOUS', actorEmail: null, actorUserId: null, action: 'LOGIN_FAILED' }),
  ]))
  const user = userEvent.setup()
  renderApp('/audit')

  const table = await screen.findByRole('table', { name: "Journal d'audit" })
  expect(within(table).getByText('Système')).toBeInTheDocument()
  expect(within(table).getByText('Anonyme')).toBeInTheDocument()
  await user.click(within(table).getAllByRole('button', { name: 'Détails' })[0]!)

  const dialog = await screen.findByRole('dialog')
  expect(within(dialog).getByText(/<img src=x onerror=alert\(1\)>/)).toBeInTheDocument()
  expect(dialog.querySelector('img')).toBeNull()
})
