import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { ForbiddenPage, LoadingPage } from '../layout/StatusPages'
import { useAuth } from './AuthProvider'
import { can, type Permission } from './permissions'
import { sessionEnd } from './session'

const CHANGE_PASSWORD = '/change-password'

/**
 * Page réservée aux utilisateurs connectés. Sans session, renvoi au login ; un changement de mot de passe imposé passe
 * avant toute autre page (spec §8.1, §12.2).
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, user } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <LoadingPage />
  }
  if (user === null) {
    // Après une expiration, la reconnexion ramène à la page quittée ; après une déconnexion volontaire, à l'accueil.
    const state = sessionEnd() === 'logged-out' ? undefined : { from: location }
    return <Navigate to="/login" replace state={state} />
  }
  if (user.mustChangePassword && location.pathname !== CHANGE_PASSWORD) {
    return <Navigate to={CHANGE_PASSWORD} replace />
  }
  return children
}

/** Page réservée à une permission (spec §12.3). L'API refuserait de toute façon les données : c'est du confort. */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const { user } = useAuth()
  return can(user, permission) ? children : <ForbiddenPage />
}
