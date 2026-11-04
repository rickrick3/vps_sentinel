import type { Middleware } from 'openapi-fetch'
import { refreshOnce } from '../auth/refreshCoordinator'
import { getAccessToken } from '../auth/session'
import { isProblem } from './problem'
import type { components } from './schema'

type AuthResponse = components['schemas']['AuthResponse']

export type AuthMiddlewareOptions = {
  /** Renouvelle la session (`POST /auth/refresh`) ; `null` si elle a pris fin. */
  refresh: () => Promise<AuthResponse | null>
  onSessionLost: () => void
  onPasswordChangeRequired: () => void
}

// Endpoints de session : ils ignorent le Bearer, et un 401 y est une réponse métier. Les soumettre au refresh ferait
// boucler (spec §12.2).
const SESSION_ENDPOINTS = new Set(['/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/auth/logout'])

const PASSWORD_CHANGE_REQUIRED = 'urn:sentinel:problem:password-change-required'

function isSessionEndpoint(request: Request): boolean {
  return SESSION_ENDPOINTS.has(new URL(request.url).pathname)
}

async function requiresPasswordChange(response: Response): Promise<boolean> {
  try {
    const body: unknown = await response.clone().json()
    return isProblem(body) && body.type === PASSWORD_CHANGE_REQUIRED
  } catch {
    return false
  }
}

/**
 * Authentification des appels à l'API (spec §12.2) :
 *
 * - ajoute `Authorization: Bearer` ;
 * - sur un 401, renouvelle la session par un refresh partagé ({@link refreshOnce}), puis rejoue la requête une seule
 *   fois ; si le refresh échoue, ou si la requête rejouée est encore refusée, la session est perdue ;
 * - sur un 403 `password-change-required`, signale le changement de mot de passe imposé.
 *
 * `/auth/change-password` reste soumis à cette logique : un jeton expiré pendant la saisie est renouvelé.
 */
export function authMiddleware(options: AuthMiddlewareOptions): Middleware {
  // Copie de chaque requête avant envoi, par identifiant : le corps de la requête envoyée est consommé.
  const replays = new Map<string, Request>()

  return {
    onRequest({ request, id }) {
      if (isSessionEndpoint(request)) {
        return undefined
      }
      const token = getAccessToken()
      if (token !== null) {
        request.headers.set('Authorization', `Bearer ${token}`)
      }
      replays.set(id, request.clone())
      return request
    },

    async onResponse({ request, response, id, options: client }) {
      const replay = replays.get(id)
      replays.delete(id)
      if (isSessionEndpoint(request) || replay === undefined) {
        return undefined
      }
      let result = response
      if (response.status === 401) {
        const session = await refreshOnce(options.refresh)
        const token = session === null ? null : getAccessToken()
        if (token === null) {
          options.onSessionLost()
          return undefined
        }
        replay.headers.set('Authorization', `Bearer ${token}`)
        result = await client.fetch(replay)
        if (result.status === 401) {
          options.onSessionLost()
          return result
        }
      }
      if (result.status === 403 && (await requiresPasswordChange(result))) {
        options.onPasswordChangeRequired()
      }
      return result === response ? undefined : result
    },

    onError({ id }) {
      replays.delete(id)
      return undefined
    },
  }
}
