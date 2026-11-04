import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { clearSession } from '../auth/session'
import { server } from './server'

// Toute requête non simulée fait échouer le test : aucun appel réseau implicite.
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  clearSession()
})
afterAll(() => server.close())

class NoopResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

// jsdom n'implémente ni matchMedia, ni ResizeObserver, ni scrollIntoView, utilisés par Mantine. Absent des tests qui
// tournent dans l'environnement Node (sans window).
if (typeof window !== 'undefined') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }),
  })
  window.ResizeObserver = NoopResizeObserver
  window.HTMLElement.prototype.scrollIntoView = () => {}
}
