import '@testing-library/jest-dom/vitest'
import { notifications } from '@mantine/notifications'
import { cleanup, configure } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { clearSession } from '../auth/session'
import { server } from './server'

// Le premier rendu d'un fichier (Mantine, routeur, restauration de session) peut prendre plusieurs secondes quand les
// fichiers tournent en parallèle sur un poste chargé ou une CI. L'attente ne coûte qu'en cas d'échec : un élément
// trouvé est rendu aussitôt.
configure({ asyncUtilTimeout: 10_000 })

// Toute requête non simulée fait échouer le test : aucun appel réseau implicite.
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  clearSession()
  // Le magasin des notifications est global : il survivrait au démontage.
  notifications.clean()
})
afterAll(() => server.close())

class NoopResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

// jsdom n'implémente ni matchMedia, ni ResizeObserver, ni scrollIntoView, ni document.fonts, utilisés par Mantine.
// Absent des tests qui tournent dans l'environnement Node (sans window).
if (typeof window !== 'undefined') {
  if (!('fonts' in document)) {
    Object.defineProperty(document, 'fonts', {
      value: { addEventListener: () => {}, removeEventListener: () => {}, ready: Promise.resolve() },
    })
  }
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
