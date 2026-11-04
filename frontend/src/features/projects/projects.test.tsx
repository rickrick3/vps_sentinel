import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { deferred } from '../../test/deferred'
import { environmentDetail, page, problem, projectDetail, serverResponse, signInAs } from '../../test/fixtures'
import { renderApp } from '../../test/render'
import { server } from '../../test/server'

const PROJECT = projectDetail({
  id: 'prj-1',
  name: 'Demo',
  environments: [
    environmentDetail({ id: 'env-prod', name: 'prod', kind: 'PRODUCTION' }),
    environmentDetail({ id: 'env-staging', name: 'staging', kind: 'STAGING' }),
  ],
})

const getProject = (project = PROJECT) => http.get('/api/v1/projects/prj-1', () => HttpResponse.json(project))

test('renders one tab per environment', async () => {
  signInAs('VIEWER')
  server.use(getProject())

  renderApp('/projects/prj-1')

  const tabs = await screen.findAllByRole('tab')
  expect(tabs.map((tab) => tab.textContent)).toEqual(['prod', 'staging'])
})

test('adding a primary domain marks it as primary', async () => {
  signInAs('DEVOPS')
  let project = PROJECT
  let sent: unknown
  server.use(
    http.get('/api/v1/projects/prj-1', () => HttpResponse.json(project)),
    http.post('/api/v1/environments/env-prod/domains', async ({ request }) => {
      sent = await request.json()
      const domain = { id: 'dom-1', hostname: 'demo.exemple.fr', isPrimary: true, createdAt: '2026-11-04T10:00:00Z' }
      project = { ...PROJECT, environments: [{ ...PROJECT.environments[0]!, domains: [domain] }, PROJECT.environments[1]!] }
      return HttpResponse.json(domain, { status: 201 })
    }),
  )
  const user = userEvent.setup()
  renderApp('/projects/prj-1')

  await user.click(await screen.findByRole('button', { name: 'Ajouter un domaine' }))
  const dialog = await screen.findByRole('dialog')
  await user.type(within(dialog).getByLabelText(/^Nom de domaine/), 'demo.exemple.fr')
  await user.click(within(dialog).getByLabelText('Domaine principal'))
  await user.click(within(dialog).getByRole('button', { name: 'Ajouter' }))

  const domains = await screen.findByRole('table', { name: 'Domaines de prod' })
  expect(within(domains).getByText('demo.exemple.fr')).toBeInTheDocument()
  expect(within(domains).getByText('Principal')).toBeInTheDocument()
  expect(sent).toEqual({ hostname: 'demo.exemple.fr', isPrimary: true })
})

test('deployment-exists error is shown in the modal', async () => {
  signInAs('DEVOPS')
  let requested: URL | undefined
  server.use(
    getProject(),
    http.get('/api/v1/servers', ({ request }) => {
      requested = new URL(request.url)
      return HttpResponse.json(page([
        serverResponse({ id: 'srv-1', name: 'VPS-01' }),
        serverResponse({ id: 'srv-old', name: 'VPS-OLD', adminStatus: 'DECOMMISSIONED' }),
      ]))
    }),
    http.post('/api/v1/environments/env-prod/deployments', () => problem(409, 'deployment-exists', {
      detail: 'Cette valeur est déjà utilisée.',
      errors: [{ field: 'serverId', message: 'valeur déjà utilisée' }],
    })),
  )
  const user = userEvent.setup()
  renderApp('/projects/prj-1')

  await user.click(await screen.findByRole('button', { name: 'Rattacher un VPS' }))
  const dialog = await screen.findByRole('dialog')
  await user.click(within(dialog).getByRole('combobox', { name: /^VPS/ }))
  // Un VPS décommissionné n'est pas proposé : l'API le refuserait.
  expect(screen.queryByRole('option', { name: /VPS-OLD/ })).toBeNull()
  await user.click(await screen.findByRole('option', { name: 'VPS-01' }))
  await user.click(within(dialog).getByRole('button', { name: 'Rattacher' }))

  expect(await within(dialog).findByText('Ce VPS est déjà rattaché à cet environnement.')).toBeInTheDocument()
  expect(requested?.searchParams.get('size')).toBe('100')
})

test('developer can add an environment but has no delete action', async () => {
  signInAs('DEVELOPER')
  server.use(getProject())

  renderApp('/projects/prj-1')

  expect(await screen.findByRole('button', { name: 'Ajouter un environnement' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Ajouter un domaine' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Supprimer le projet' })).toBeNull()
  expect(screen.queryByRole('button', { name: "Supprimer l'environnement" })).toBeNull()
})

test('viewer has no write action at all', async () => {
  signInAs('VIEWER')
  server.use(getProject())

  renderApp('/projects/prj-1')

  await screen.findAllByRole('tab')
  expect(screen.queryByRole('button', { name: 'Ajouter un environnement' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Ajouter un domaine' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Rattacher un VPS' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Modifier le projet' })).toBeNull()
})

test('repository link opens with rel="noopener noreferrer"', async () => {
  signInAs('VIEWER')
  server.use(getProject())

  renderApp('/projects/prj-1')

  const link = await screen.findByRole('link', { name: 'https://github.com/exemple/demo' })
  expect(link).toHaveAttribute('href', 'https://github.com/exemple/demo')
  expect(link).toHaveAttribute('target', '_blank')
  expect(link).toHaveAttribute('rel', 'noopener noreferrer')
})

test('a repository URL that is not http(s) is shown as text, never as a link', async () => {
  signInAs('VIEWER')
  server.use(getProject({ ...PROJECT, repositoryUrl: 'javascript:alert(1)' }))

  renderApp('/projects/prj-1')

  expect(await screen.findByText('javascript:alert(1)')).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'javascript:alert(1)' })).toBeNull()
})

test('submit is disabled while saving in modals', async () => {
  signInAs('DEVOPS')
  let calls = 0
  let sent: unknown
  const response = deferred()
  server.use(getProject(), http.post('/api/v1/projects/prj-1/environments', async ({ request }) => {
    calls++
    sent = await request.json()
    await response.promise
    return HttpResponse.json({ id: 'env-dev', projectId: 'prj-1', name: 'dev', kind: 'DEVELOPMENT', version: 0 }, { status: 201 })
  }))
  const user = userEvent.setup()
  renderApp('/projects/prj-1')

  await user.click(await screen.findByRole('button', { name: 'Ajouter un environnement' }))
  const dialog = await screen.findByRole('dialog')
  await user.type(within(dialog).getByLabelText(/^Nom/), 'dev')
  await user.click(within(dialog).getByRole('combobox', { name: /^Nature/ }))
  await user.click(await screen.findByRole('option', { name: 'Développement' }))
  const save = within(dialog).getByRole('button', { name: 'Enregistrer' })
  await user.click(save)
  await waitFor(() => expect(save).toBeDisabled())
  await user.click(save)
  response.release()

  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  expect(calls).toBe(1)
  expect(sent).toEqual({ name: 'dev', kind: 'DEVELOPMENT' })
})

test('a new project from the list opens its page', async () => {
  signInAs('DEVOPS')
  let sent: unknown
  server.use(
    http.get('/api/v1/projects', () => HttpResponse.json(page([]))),
    http.post('/api/v1/projects', async ({ request }) => {
      sent = await request.json()
      return HttpResponse.json(projectDetail({ id: 'prj-2', name: 'Boutique', environments: [] }), { status: 201 })
    }),
    http.get('/api/v1/projects/prj-2', () => HttpResponse.json(projectDetail({ id: 'prj-2', name: 'Boutique', environments: [] }))),
  )
  const user = userEvent.setup()
  renderApp('/projects')

  await user.click(await screen.findByRole('button', { name: 'Nouveau projet' }))
  const dialog = await screen.findByRole('dialog')
  await user.type(within(dialog).getByLabelText(/^Nom/), 'Boutique')
  await user.click(within(dialog).getByRole('button', { name: 'Enregistrer' }))

  expect(await screen.findByRole('heading', { name: 'Boutique' })).toBeInTheDocument()
  expect(screen.getByText('Aucun environnement.')).toBeInTheDocument()
  expect(sent).toEqual({ name: 'Boutique', description: null, repositoryUrl: null })
})

test('deleting a project asks for confirmation, then returns to the list', async () => {
  signInAs('DEVOPS')
  let deletes = 0
  server.use(
    getProject(),
    http.get('/api/v1/projects', () => HttpResponse.json(page([]))),
    http.delete('/api/v1/projects/prj-1', () => {
      deletes++
      return new HttpResponse(null, { status: 204 })
    }),
  )
  const user = userEvent.setup()
  const { router } = renderApp('/projects/prj-1')

  await user.click(await screen.findByRole('button', { name: 'Supprimer le projet' }))
  expect(deletes).toBe(0)
  await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmer' }))

  await waitFor(() => expect(router.state.location.pathname).toBe('/projects'))
  expect(deletes).toBe(1)
})

test('detaching a deployment asks for confirmation', async () => {
  signInAs('DEVELOPER')
  let detached = false
  const deployment = {
    id: 'dep-1', environmentId: 'env-prod', serverId: 'srv-1', serverName: 'VPS-01', serverAdminStatus: 'ACTIVE',
    role: 'web', createdAt: '2026-11-04T10:00:00Z',
  }
  server.use(
    getProject({ ...PROJECT, environments: [{ ...PROJECT.environments[0]!, deployments: [deployment] }] }),
    http.delete('/api/v1/deployments/dep-1', () => {
      detached = true
      return new HttpResponse(null, { status: 204 })
    }),
  )
  const user = userEvent.setup()
  renderApp('/projects/prj-1')

  const deployments = await screen.findByRole('table', { name: 'Déploiements de prod' })
  expect(within(deployments).getByRole('link', { name: 'VPS-01' })).toHaveAttribute('href', '/servers/srv-1')
  await user.click(within(deployments).getByRole('button', { name: 'Détacher VPS-01' }))
  await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmer' }))

  await waitFor(() => expect(detached).toBe(true))
})

test('list search goes to the URL and the API', async () => {
  signInAs('VIEWER')
  const requests: URL[] = []
  server.use(http.get('/api/v1/projects', ({ request }) => {
    requests.push(new URL(request.url))
    return HttpResponse.json(page([{ id: 'prj-1', name: 'Demo', environmentCount: 2, createdAt: '2026-11-02T10:30:00Z', version: 0 }]))
  }))
  const user = userEvent.setup()
  renderApp('/projects')

  expect(await screen.findByRole('link', { name: 'Demo' })).toHaveAttribute('href', '/projects/prj-1')
  await user.type(screen.getByLabelText('Rechercher'), 'dem')

  await waitFor(() => expect(requests.at(-1)?.searchParams.get('q')).toBe('dem'))
})
