import createClient, { type Client, type Middleware } from 'openapi-fetch'
import type { paths } from './schema'

export type ApiClient = Client<paths>

/**
 * Client typé de l'API (spec §12.1), dont les types sont générés depuis `contracts/openapi/core-api.yaml`.
 *
 * - L'API est servie sur la même origine que la page (gateway, ou proxy Vite en dev) : le cookie de session suit.
 *   L'URL de base est cette origine plutôt qu'une chaîne vide, que le constructeur `Request` de Node (tests) refuse.
 * - Le `fetch` global est lu à chaque appel : MSW, installé après la création du client, peut alors l'intercepter.
 */
export function createApiClient(middleware: Middleware[]): ApiClient {
  const client = createClient<paths>({
    baseUrl: window.location.origin,
    fetch: (request) => globalThis.fetch(request),
  })
  client.use(...middleware)
  return client
}
