import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import { vi } from 'vitest'
import { createApiClient } from '../api/client'
import { AuthContext, AuthProvider, type AuthContextValue } from '../auth/AuthProvider'
import type { Me } from '../auth/session'
import { routes } from '../router'
import { theme } from '../theme'
import { me } from './fixtures'

function testQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function Providers({ queryClient, children }: { queryClient: QueryClient; children: ReactNode }) {
  return (
    <MantineProvider theme={theme} env="test">
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </MantineProvider>
  )
}

/**
 * Un composant seul, sous un utilisateur donné (`null` : personne n'est connecté). Les actions d'authentification
 * sont des espions, renvoyés pour les assertions.
 */
export function renderWithUser(ui: ReactElement, user: Partial<Me> | null = {}, { route = '/' } = {}) {
  const auth: AuthContextValue = {
    status: 'ready',
    user: user === null ? null : me(user),
    api: createApiClient([]),
    login: vi.fn(),
    logout: vi.fn(),
    changePassword: vi.fn(),
  }
  const router = createMemoryRouter(
    [{ path: '*', element: <AuthContext.Provider value={auth}>{ui}</AuthContext.Provider> }],
    { initialEntries: [route] },
  )
  const queryClient = testQueryClient()
  const result = render(
    <Providers queryClient={queryClient}>
      <RouterProvider router={router} />
    </Providers>,
  )
  return { ...result, auth, router, queryClient }
}

/** L'application complète, avec le vrai AuthProvider et ses routes, ouverte à l'URL donnée ; l'API est simulée par MSW. */
export function renderApp(path = '/', appRoutes: RouteObject[] = routes) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] })
  const queryClient = testQueryClient()
  const result = render(
    <Providers queryClient={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </Providers>,
  )
  return { ...result, router, queryClient }
}
