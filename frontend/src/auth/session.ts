import type { components } from '../api/schema'

type AuthResponse = components['schemas']['AuthResponse']
type MeResponse = components['schemas']['MeResponse']

/** Utilisateur connecté, tel que `GET /me` et les réponses de session le décrivent. */
export type Me = {
  id: string
  email: string
  fullName: string
  role: string
  permissions: string[]
  mustChangePassword: boolean
}

type Session = { accessToken: string; user: Me }

/** Fin de session : `logged-out` pour une déconnexion demandée dans cet onglet, `expired` sinon. */
export type SessionEnd = 'expired' | 'logged-out'

// Session de l'onglet, en mémoire seulement (spec §12.2) : jamais dans localStorage ni sessionStorage. Le refresh token
// reste dans son cookie HttpOnly, hors de portée du JavaScript.
let current: Session | null = null
let lastEnd: SessionEnd | null = null
const listeners = new Set<() => void>()

export function getAccessToken(): string | null {
  return current?.accessToken ?? null
}

export function getUser(): Me | null {
  return current?.user ?? null
}

/** Ouvre ou renouvelle la session ; une réponse incomplète est refusée plutôt que de laisser un utilisateur partiel. */
export function setSession(response: AuthResponse): Me {
  const user = response.user && toMe(response.user)
  if (!response.accessToken || !user) {
    throw new Error("Réponse d'authentification incomplète.")
  }
  current = { accessToken: response.accessToken, user }
  lastEnd = null
  notify()
  return user
}

/** L'API a répondu `password-change-required` : le changement devient imposé pour la suite de la session. */
export function markPasswordChangeRequired(): void {
  if (current !== null && !current.user.mustChangePassword) {
    current = { ...current, user: { ...current.user, mustChangePassword: true } }
    notify()
  }
}

export function clearSession(reason: SessionEnd = 'expired'): void {
  lastEnd = reason
  if (current !== null) {
    current = null
    notify()
  }
}

/** Raison de la dernière fin de session, `null` si une session est ouverte ou n'a jamais existé. */
export function sessionEnd(): SessionEnd | null {
  return lastEnd
}

/** Abonnement aux changements de session, au format attendu par `useSyncExternalStore`. */
export function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

function notify() {
  listeners.forEach((listener) => listener())
}

// Le contrat OpenAPI déclare facultatifs tous les champs de réponse : ils sont vérifiés une fois, ici.
function toMe(user: MeResponse): Me | null {
  const { id, email, fullName, role, permissions, mustChangePassword } = user
  if (id === undefined || email === undefined || fullName === undefined || role === undefined
    || permissions === undefined || mustChangePassword === undefined) {
    return null
  }
  return { id, email, fullName, role, permissions, mustChangePassword }
}
