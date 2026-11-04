const DEFAULT_RETRY_SECONDS = 60

/** Délai d'attente d'une réponse 429 (spec §8.4, §6.4) : `Retry-After` en secondes, ou en date HTTP. */
export function retryAfterSeconds(response: Response): number {
  const header = response.headers.get('Retry-After')
  if (header === null) {
    return DEFAULT_RETRY_SECONDS
  }
  const seconds = Number(header)
  if (Number.isFinite(seconds) && seconds >= 0) {
    return Math.ceil(seconds)
  }
  const date = Date.parse(header)
  return Number.isNaN(date) ? DEFAULT_RETRY_SECONDS : Math.max(0, Math.ceil((date - Date.now()) / 1000))
}

export function waitMessage(seconds: number): string {
  return `Trop de tentatives. Réessayez dans ${seconds} seconde${seconds > 1 ? 's' : ''}.`
}
