/** Natures d'environnement (spec §9.4). */
export const ENVIRONMENT_KINDS = [
  { value: 'PRODUCTION', label: 'Production', color: 'red' },
  { value: 'STAGING', label: 'Préproduction', color: 'orange' },
  { value: 'DEVELOPMENT', label: 'Développement', color: 'blue' },
  { value: 'OTHER', label: 'Autre', color: 'gray' },
] as const

export function environmentKind(value: string | null | undefined) {
  return ENVIRONMENT_KINDS.find((kind) => kind.value === value) ?? { value, label: value ?? '—', color: 'gray' }
}

/** Seules les URL http(s) deviennent des liens : une valeur `javascript:` resterait du texte (défense en profondeur). */
export function isHttpUrl(value: string | null | undefined): value is string {
  return typeof value === 'string' && /^https?:\/\//i.test(value)
}
