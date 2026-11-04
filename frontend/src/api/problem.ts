/** Erreur de l'API au format Problem Details (spec §11), y compris celles du gateway. */
export type Problem = {
  type: string
  title?: string
  status: number
  detail?: string
  correlationId?: string
  errors?: { field: string; message: string }[]
}

const PREFIX = 'urn:sentinel:problem:'

/** Code attribué côté client à une réponse d'erreur qui n'est pas un Problem Details (page HTML d'un proxy…). */
export const UNEXPECTED_RESPONSE = `${PREFIX}unexpected-response`

export function isProblem(value: unknown): value is Problem {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const candidate = value as Record<string, unknown>
  return typeof candidate.type === 'string' && candidate.type.startsWith(PREFIX) && typeof candidate.status === 'number'
}

/** Suffixe de l'URN : `version-conflict` pour `urn:sentinel:problem:version-conflict`. */
export function problemCode(problem: Problem): string {
  return problem.type.startsWith(PREFIX) ? problem.type.slice(PREFIX.length) : problem.type
}

/** Erreurs de champ, prêtes pour `form.setErrors` ; plusieurs messages d'un même champ sont réunis. */
export function fieldErrors(problem: Problem): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const { field, message } of problem.errors ?? []) {
    errors[field] = field in errors ? `${errors[field]} ; ${message}` : message
  }
  return errors
}

/** Le corps d'une réponse d'erreur, tel quel s'il s'agit d'un Problem Details, sinon une erreur générique. */
export function asProblem(body: unknown, status: number): Problem {
  return isProblem(body)
    ? body
    : { type: UNEXPECTED_RESPONSE, status, detail: `Le serveur a renvoyé une réponse inattendue (${status}).` }
}

/** Échec d'un appel à l'API : le Problem Details et la réponse, pour ses en-têtes (`Retry-After`). */
export class ApiError extends Error {
  readonly problem: Problem
  readonly response: Response

  constructor(problem: Problem, response: Response) {
    super(problem.detail ?? problem.title ?? `Erreur ${problem.status}`)
    this.name = 'ApiError'
    this.problem = problem
    this.response = response
  }
}
