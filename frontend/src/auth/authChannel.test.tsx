import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, test, vi } from 'vitest'
import { authResponse } from '../test/fixtures'
import { renderApp } from '../test/render'
import { server } from '../test/server'
import { broadcastLogout, onLogout } from './authChannel'
import { getUser } from './session'

/** Canal d'un autre onglet, qui émet et reçoit sous sa propre identité. */
function otherTab() {
  return new BroadcastChannel('sentinel-auth')
}

test('broadcastLogout reaches the other tabs', async () => {
  const tab = otherTab()
  const received = new Promise<unknown>((resolve) => {
    tab.onmessage = (event: MessageEvent) => resolve(event.data)
  })

  broadcastLogout()

  await expect(received).resolves.toMatchObject({ type: 'logout' })
  tab.close()
})

test("onLogout reacts to the other tabs, not to this tab's own logout", async () => {
  const callback = vi.fn()
  const stop = onLogout(callback)
  const tab = otherTab()

  broadcastLogout()
  tab.postMessage({ type: 'logout', sender: 'other-tab' })

  await vi.waitFor(() => expect(callback).toHaveBeenCalledOnce())
  await new Promise((resolve) => setTimeout(resolve, 50))
  expect(callback).toHaveBeenCalledOnce()
  tab.close()
  stop()
})

test('ignores unrelated messages', async () => {
  const callback = vi.fn()
  const stop = onLogout(callback)
  const tab = otherTab()

  tab.postMessage({ type: 'something-else', sender: 'other-tab' })
  tab.postMessage({ type: 'logout', sender: 'other-tab' })

  await vi.waitFor(() => expect(callback).toHaveBeenCalledOnce())
  tab.close()
  stop()
})

test('works without BroadcastChannel', () => {
  vi.stubGlobal('BroadcastChannel', undefined)

  expect(() => broadcastLogout()).not.toThrow()
  const stop = onLogout(vi.fn())
  expect(() => stop()).not.toThrow()
})

test('logout in one tab clears the other', async () => {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse('t', { fullName: 'Alice Martin' }))))
  renderApp('/')
  await screen.findByRole('button', { name: 'Alice Martin' })
  const tab = otherTab()

  // Déconnexion dans un autre onglet : seul le message arrive ici, sans appel à l'API.
  tab.postMessage({ type: 'logout', sender: 'other-tab' })

  expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
  expect(getUser()).toBeNull()
  tab.close()
})
