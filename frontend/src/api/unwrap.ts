import { ApiError, asProblem } from './problem'

type FetchResult<T> = { data?: T; error?: unknown; response: Response }

/**
 * Résultat d'un appel openapi-fetch pour TanStack Query : les données, ou une {@link ApiError} levée pour toute
 * réponse en erreur. Une réponse 204 donne `undefined`.
 */
export async function unwrap<T>(request: Promise<FetchResult<T>>): Promise<T> {
  const { data, error, response } = await request
  if (!response.ok) {
    throw new ApiError(asProblem(error, response.status), response)
  }
  return data as T
}
