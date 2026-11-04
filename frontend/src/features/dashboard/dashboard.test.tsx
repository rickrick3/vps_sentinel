import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { auditLog, page, signInAs } from '../../test/fixtures'
import { renderApp } from '../../test/render'
import { server } from '../../test/server'

const COUNTS: Record<string, number> = { ACTIVE: 7, MAINTENANCE: 2, DECOMMISSIONED: 1 }

function counters(serverRequests: URL[] = []) {
  return [
    http.get('/api/v1/servers', ({ request }) => {
      const url = new URL(request.url)
      serverRequests.push(url)
      return HttpResponse.json(page([], { size: 1, totalItems: COUNTS[url.searchParams.get('adminStatus') ?? ''] ?? 0 }))
    }),
    http.get('/api/v1/projects', () => HttpResponse.json(page([], { size: 1, totalItems: 4 }))),
  ]
}

test('dashboard counters equal totalItems', async () => {
  signInAs('DEVOPS')
  const requests: URL[] = []
  server.use(...counters(requests))

  renderApp('/')

  expect(await within(await screen.findByRole('region', { name: 'VPS actifs' })).findByText('7')).toBeInTheDocument()
  expect(await within(screen.getByRole('region', { name: 'VPS en maintenance' })).findByText('2')).toBeInTheDocument()
  expect(await within(screen.getByRole('region', { name: 'VPS décommissionnés' })).findByText('1')).toBeInTheDocument()
  expect(await within(screen.getByRole('region', { name: 'Projets' })).findByText('4')).toBeInTheDocument()
  // Une ligne suffit : seul totalItems est lu.
  expect(requests.every((url) => url.searchParams.get('size') === '1')).toBe(true)
  expect(requests.map((url) => url.searchParams.get('adminStatus')).sort()).toEqual(['ACTIVE', 'DECOMMISSIONED', 'MAINTENANCE'])
})

test('dashboard never calls audit-logs without audit:read', async () => {
  signInAs('DEVOPS')
  let auditCalls = 0
  server.use(...counters(), http.get('/api/v1/audit-logs', () => {
    auditCalls++
    return HttpResponse.json(page([]))
  }))

  renderApp('/')

  await within(await screen.findByRole('region', { name: 'Projets' })).findByText('4')
  expect(screen.queryByRole('heading', { name: 'Dernières actions' })).toBeNull()
  expect(auditCalls).toBe(0)
})

test('the last ten actions are shown with audit:read', async () => {
  signInAs('INFRA_ADMIN')
  let requested: URL | undefined
  server.use(...counters(), http.get('/api/v1/audit-logs', ({ request }) => {
    requested = new URL(request.url)
    return HttpResponse.json(page([auditLog({ action: 'PROJECT_CREATED', actorEmail: 'bob@exemple.fr' })], { size: 10 }))
  }))

  renderApp('/')

  expect(await screen.findByRole('heading', { name: 'Dernières actions' })).toBeInTheDocument()
  expect(await screen.findByText('Projet créé')).toBeInTheDocument()
  expect(screen.getByText('bob@exemple.fr')).toBeInTheDocument()
  expect(requested?.searchParams.get('size')).toBe('10')
  expect(requested?.searchParams.get('sort')).toBe('occurredAt,desc')
  expect(screen.getByRole('link', { name: 'Voir le journal' })).toHaveAttribute('href', '/audit')
})
