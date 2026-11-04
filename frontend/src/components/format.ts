const DATE_TIME = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'short' })
const DATE_TIME_SECONDS = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'medium' })

/** Date et heure locales ; `—` si la valeur manque. */
export function formatDateTime(value: string | null | undefined, { seconds = false } = {}): string {
  if (!value) {
    return '—'
  }
  return (seconds ? DATE_TIME_SECONDS : DATE_TIME).format(new Date(value))
}

/** Valeur affichée telle quelle, ou `—` si elle manque. */
export function orDash(value: string | number | null | undefined): string {
  return value === null || value === undefined || value === '' ? '—' : String(value)
}
