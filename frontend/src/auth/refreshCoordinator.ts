import type { components } from '../api/schema'

type AuthResponse = components['schemas']['AuthResponse']

const LOCK_NAME = 'sentinel-refresh'
const LOCK_TIMEOUT_MS = 10_000

let inFlight: Promise<AuthResponse | null> | null = null

/**
 * Un seul refresh à la fois (spec §12.2). Le refresh token tourne à chaque usage et un jeton déjà tourné révoque toute
 * la session (spec §8.3) : deux refresh concurrents avec le même cookie déconnecteraient l'utilisateur.
 *
 * - Dans l'onglet, les appelants simultanés partagent la même promesse.
 * - Entre onglets, le refresh s'exécute sous le verrou Web Locks `sentinel-refresh` : l'onglet suivant attend, puis
 *   tourne à son tour le cookie déjà renouvelé. L'attente du verrou est abandonnée après 10 s (résultat `null`).
 * - Sans Web Locks (contexte non sécurisé, navigateur ancien), seule la mutualisation dans l'onglet s'applique.
 */
export function refreshOnce(doRefresh: () => Promise<AuthResponse | null>): Promise<AuthResponse | null> {
  inFlight ??= underLock(doRefresh).finally(() => {
    inFlight = null
  })
  return inFlight
}

async function underLock(doRefresh: () => Promise<AuthResponse | null>): Promise<AuthResponse | null> {
  const locks = globalThis.navigator?.locks
  if (!locks) {
    return doRefresh()
  }
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), LOCK_TIMEOUT_MS)
  try {
    return await locks.request(LOCK_NAME, { signal: controller.signal }, () => {
      // Le délai ne porte que sur l'attente du verrou.
      clearTimeout(timeout)
      return doRefresh()
    })
  } catch (error) {
    if (controller.signal.aborted) {
      return null
    }
    throw error
  } finally {
    clearTimeout(timeout)
  }
}
