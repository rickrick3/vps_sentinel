import { notifications } from '@mantine/notifications'
import { useQueryClient } from '@tanstack/react-query'
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react'
import { authMiddleware } from '../api/authMiddleware'
import { createApiClient, type ApiClient } from '../api/client'
import { ApiError, asProblem } from '../api/problem'
import { broadcastLogout, onLogout } from './authChannel'
import { refreshOnce } from './refreshCoordinator'
import { clearSession, getUser, markPasswordChangeRequired, setSession, subscribe, type Me } from './session'

export type AuthStatus = 'loading' | 'ready'

export type AuthContextValue = {
  /** `loading` tant que la session n'a pas été restaurée au chargement de la page. */
  status: AuthStatus
  user: Me | null
  api: ApiClient
  /** Lève une {@link ApiError} si l'API refuse (401, 429…). */
  login: (email: string, password: string) => Promise<Me>
  logout: () => Promise<void>
  /** Lève une {@link ApiError} si l'API refuse (400, 429…). */
  changePassword: (currentPassword: string, newPassword: string) => Promise<Me>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (value === null) {
    throw new Error('useAuth doit être appelé sous AuthProvider.')
  }
  return value
}

/** Client de l'API authentifié par la session courante. */
export function useApi(): ApiClient {
  return useAuth().api
}

/** Renouvelle la session par le cookie ; `null` si elle a pris fin (401). Une autre erreur (réseau…) est levée. */
async function refreshSession(api: ApiClient) {
  const { data, response } = await api.POST('/api/v1/auth/refresh')
  if (data) {
    setSession(data)
    return data
  }
  if (response.status === 401) {
    clearSession()
    return null
  }
  throw new Error(`Renouvellement de la session impossible (${response.status}).`)
}

/**
 * Session de l'utilisateur (spec §12.2). Au chargement, la session est restaurée par un refresh. Ensuite, les gardes
 * de routes suivent l'état de la session : une session perdue renvoie au login, un changement de mot de passe imposé
 * à `/change-password`. Les données en cache sont vidées à chaque changement d'utilisateur.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const user = useSyncExternalStore(subscribe, getUser)
  const [status, setStatus] = useState<AuthStatus>('loading')

  const [api] = useState<ApiClient>(() => {
    const client: ApiClient = createApiClient([
      authMiddleware({
        refresh: () => refreshSession(client),
        onSessionLost: () => {
          queryClient.clear()
          clearSession()
          notifications.show({ color: 'orange', title: 'Session expirée', message: 'Reconnectez-vous pour continuer.' })
        },
        onPasswordChangeRequired: markPasswordChangeRequired,
      }),
    ])
    return client
  })

  useEffect(() => {
    let active = true
    refreshOnce(() => refreshSession(api))
      .catch(() => null)
      .finally(() => {
        if (active) {
          setStatus('ready')
        }
      })
    return () => {
      active = false
    }
  }, [api])

  // Déconnexion dans un autre onglet : la session de celui-ci prend fin aussi, sans nouvel appel à l'API.
  useEffect(
    () =>
      onLogout(() => {
        queryClient.clear()
        clearSession()
      }),
    [queryClient],
  )

  const login = useCallback(
    async (email: string, password: string) => {
      const { data, error, response } = await api.POST('/api/v1/auth/login', { body: { email, password } })
      if (!data) {
        throw new ApiError(asProblem(error, response.status), response)
      }
      queryClient.clear()
      return setSession(data)
    },
    [api, queryClient],
  )

  const logout = useCallback(async () => {
    try {
      await api.POST('/api/v1/auth/logout')
    } catch {
      // API injoignable : la session de l'onglet prend fin quand même ; le cookie expirera.
    }
    broadcastLogout()
    queryClient.clear()
    clearSession('logged-out')
  }, [api, queryClient])

  const changePassword = useCallback(
    async (currentPassword: string, newPassword: string) => {
      const { data, error, response } = await api.POST('/api/v1/auth/change-password', {
        body: { currentPassword, newPassword },
      })
      if (!data) {
        throw new ApiError(asProblem(error, response.status), response)
      }
      return setSession(data)
    },
    [api],
  )

  const value = useMemo(
    () => ({ status, user, api, login, logout, changePassword }),
    [status, user, api, login, logout, changePassword],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
