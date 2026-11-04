import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { deferred } from '../../test/deferred'
import { page, problem, serverResponse, signInAs } from '../../test/fixtures'
import { renderApp } from '../../test/render'
import { server } from '../../test/server'

const VPS = serverResponse({ id: 'srv-1', name: 'VPS-01' })

function list(captured: URL[] = [], items = [VPS], totalItems = items.length) {
  return http.get('/api/v1/servers', ({ request }) => {
    const url = new URL(request.url)
    captured.push(url)
    return HttpResponse.json(page(items, { page: Number(url.searchParams.get('page') ?? 0), totalItems }))
  })
}

const facets = http.get('/api/v1/servers/facets', () =>
  HttpResponse.json({ providers: ['OVH', 'Scaleway'], regions: ['GRA'], tags: ['web', 'db'] }))

const detail = http.get('/api/v1/servers/srv-1', () => HttpResponse.json(VPS))

const hosted = http.get('/api/v1/servers/srv-1/deployments', () =>
  HttpResponse.json([{ deploymentId: 'dep-1', environmentId: 'env-prod', environmentName: 'prod', projectId: 'prj-1', projectName: 'Demo', role: 'web' }]))

test('list reads filters from the URL', async () => {
  signInAs('INFRA_ADMIN')
  const requests: URL[] = []
  server.use(list(requests, [VPS], 61), facets)

  renderApp('/servers?q=web&page=2')

  expect(await screen.findByRole('link', { name: 'VPS-01' })).toBeInTheDocument()
  const query = requests.at(-1)?.searchParams
  expect(query?.get('q')).toBe('web')
  expect(query?.get('page')).toBe('2')
  expect(query?.get('sort')).toBe('name,asc')
  expect(screen.getByLabelText('Rechercher')).toHaveValue('web')
})

test('changing sort updates the URL', async () => {
  signInAs('INFRA_ADMIN')
  const requests: URL[] = []
  server.use(list(requests), facets)
  const user = userEvent.setup()
  const { router } = renderApp('/servers?page=3')

  await user.click(await screen.findByRole('button', { name: 'Statut' }))

  await waitFor(() => expect(new URLSearchParams(router.state.location.search).get('sort')).toBe('adminStatus,asc'))
  // Un nouveau tri repart de la première page.
  expect(new URLSearchParams(router.state.location.search).get('page')).toBeNull()
  await waitFor(() => expect(requests.at(-1)?.searchParams.get('sort')).toBe('adminStatus,asc'))

  await user.click(screen.getByRole('button', { name: 'Statut' }))
  await waitFor(() => expect(new URLSearchParams(router.state.location.search).get('sort')).toBe('adminStatus,desc'))
})

test('search and filters go to the URL', async () => {
  signInAs('INFRA_ADMIN')
  const requests: URL[] = []
  server.use(list(requests), facets)
  const user = userEvent.setup()
  const { router } = renderApp('/servers')

  await user.type(await screen.findByLabelText('Rechercher'), '203.0.113')

  await waitFor(() => expect(new URLSearchParams(router.state.location.search).get('q')).toBe('203.0.113'))
  await waitFor(() => expect(requests.at(-1)?.searchParams.get('q')).toBe('203.0.113'))
})

test('create defaults to ACTIVE and opens the new server', async () => {
  signInAs('INFRA_ADMIN')
  let sent: unknown
  server.use(
    facets,
    http.post('/api/v1/servers', async ({ request }) => {
      sent = await request.json()
      return HttpResponse.json(serverResponse({ id: 'srv-2', name: 'VPS-02' }), { status: 201 })
    }),
    http.get('/api/v1/servers/srv-2', () => HttpResponse.json(serverResponse({ id: 'srv-2', name: 'VPS-02' }))),
    http.get('/api/v1/servers/srv-2/deployments', () => HttpResponse.json([])),
  )
  const user = userEvent.setup()
  renderApp('/servers/new')

  await user.type(await screen.findByLabelText(/^Nom/), 'VPS-02')
  await user.click(screen.getByRole('button', { name: 'Enregistrer' }))

  expect(await screen.findByRole('heading', { name: 'VPS-02' })).toBeInTheDocument()
  expect(sent).toMatchObject({ name: 'VPS-02', adminStatus: 'ACTIVE', hostname: null, tags: [] })
  expect(sent).not.toHaveProperty('version')
})

test('edit sends a full PUT with cleared fields as null and the version', async () => {
  signInAs('INFRA_ADMIN')
  let sent: unknown
  server.use(
    detail,
    hosted,
    facets,
    http.put('/api/v1/servers/srv-1', async ({ request }) => {
      sent = await request.json()
      return HttpResponse.json({ ...VPS, hostname: null, version: 4 })
    }),
  )
  const user = userEvent.setup()
  renderApp('/servers/srv-1/edit')

  await user.clear(await screen.findByLabelText('Hostname'))
  await user.click(screen.getByRole('button', { name: 'Enregistrer' }))

  await waitFor(() => expect(sent).toBeDefined())
  expect(sent).toEqual({
    name: 'VPS-01',
    hostname: null,
    publicIp: '203.0.113.10',
    privateIp: null,
    provider: 'OVH',
    region: 'GRA',
    osFamily: 'UBUNTU',
    osVersion: '24.04',
    cpuCores: 4,
    ramMb: 8192,
    diskGb: 160,
    adminStatus: 'ACTIVE',
    description: null,
    tags: ['web'],
    version: 3,
  })
})

test('server field errors are shown on inputs', async () => {
  signInAs('INFRA_ADMIN')
  server.use(
    facets,
    http.post('/api/v1/servers', () => problem(409, 'duplicate', {
      detail: 'Cette valeur est déjà utilisée.',
      errors: [{ field: 'name', message: 'valeur déjà utilisée' }],
    })),
  )
  const user = userEvent.setup()
  renderApp('/servers/new')

  await user.type(await screen.findByLabelText(/^Nom/), 'vps-01')
  await user.click(screen.getByRole('button', { name: 'Enregistrer' }))

  expect(await screen.findByText('valeur déjà utilisée')).toBeInTheDocument()
  expect(screen.getByLabelText(/^Nom/)).toHaveAttribute('aria-invalid', 'true')
})

test('version conflict shows the exact message', async () => {
  signInAs('INFRA_ADMIN')
  server.use(detail, facets, http.put('/api/v1/servers/srv-1', () => problem(409, 'version-conflict')))
  const user = userEvent.setup()
  renderApp('/servers/srv-1/edit')

  await user.click(await screen.findByRole('button', { name: 'Enregistrer' }))

  expect(await screen.findByText("Cet élément a été modifié par quelqu'un d'autre. Rechargez la page.")).toBeInTheDocument()
})

test('submit is disabled while saving', async () => {
  signInAs('INFRA_ADMIN')
  let calls = 0
  const response = deferred()
  server.use(detail, hosted, facets, http.put('/api/v1/servers/srv-1', async () => {
    calls++
    await response.promise
    return HttpResponse.json({ ...VPS, version: 4 })
  }))
  const user = userEvent.setup()
  renderApp('/servers/srv-1/edit')

  const save = await screen.findByRole('button', { name: 'Enregistrer' })
  await user.click(save)
  await waitFor(() => expect(save).toBeDisabled())
  await user.click(save)
  response.release()

  expect(await screen.findByRole('heading', { name: 'VPS-01' })).toBeInTheDocument()
  expect(calls).toBe(1)
})

test('delete of a server in use shows "VPS utilisé par des déploiements"', async () => {
  signInAs('INFRA_ADMIN')
  server.use(detail, hosted, http.delete('/api/v1/servers/srv-1', () =>
    problem(409, 'server-in-use', { detail: 'VPS utilisé par des déploiements' })))
  const user = userEvent.setup()
  const { router } = renderApp('/servers/srv-1')

  await user.click(await screen.findByRole('button', { name: 'Supprimer' }))
  await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmer' }))

  expect(await screen.findByText('VPS utilisé par des déploiements')).toBeInTheDocument()
  expect(router.state.location.pathname).toBe('/servers/srv-1')
})

test('delete asks for confirmation before calling the API', async () => {
  signInAs('INFRA_ADMIN')
  let deletes = 0
  server.use(detail, hosted, list(), facets, http.delete('/api/v1/servers/srv-1', () => {
    deletes++
    return new HttpResponse(null, { status: 204 })
  }))
  const user = userEvent.setup()
  const { router } = renderApp('/servers/srv-1')

  await user.click(await screen.findByRole('button', { name: 'Supprimer' }))
  expect(within(await screen.findByRole('dialog')).getByText(/VPS-01/)).toBeInTheDocument()
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Annuler' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  expect(deletes).toBe(0)

  await user.click(screen.getByRole('button', { name: 'Supprimer' }))
  await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmer' }))

  await waitFor(() => expect(router.state.location.pathname).toBe('/servers'))
  expect(deletes).toBe(1)
})

test('viewer sees no create, edit or delete actions', async () => {
  signInAs('VIEWER')
  server.use(list(), facets, detail, hosted)
  const user = userEvent.setup()
  renderApp('/servers')

  await user.click(await screen.findByRole('link', { name: 'VPS-01' }))

  expect(await screen.findByRole('heading', { name: 'VPS-01' })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Modifier' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Supprimer' })).toBeNull()
  await user.click(screen.getByRole('link', { name: 'VPS' }))
  expect(await screen.findByRole('link', { name: 'VPS-01' })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Nouveau VPS' })).toBeNull()
})

test('detail lists hosted environments', async () => {
  signInAs('DEVOPS')
  server.use(detail, hosted)

  renderApp('/servers/srv-1')

  const table = await screen.findByRole('table', { name: 'Environnements hébergés' })
  expect(within(table).getByRole('link', { name: 'Demo' })).toHaveAttribute('href', '/projects/prj-1')
  expect(within(table).getByText('prod')).toBeInTheDocument()
  expect(within(table).getByText('web')).toBeInTheDocument()
})

test('an unknown server shows a not-found message', async () => {
  signInAs('VIEWER')
  server.use(
    http.get('/api/v1/servers/srv-x', () => problem(404, 'not-found', { detail: 'VPS introuvable.' })),
    http.get('/api/v1/servers/srv-x/deployments', () => problem(404, 'not-found')),
  )

  renderApp('/servers/srv-x')

  expect(await screen.findByText('VPS introuvable.')).toBeInTheDocument()
})
