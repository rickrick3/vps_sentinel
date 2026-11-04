import type { RouteObject } from 'react-router'
import { ChangePasswordPage } from './auth/ChangePasswordPage'
import { RequireAuth, RequirePermission } from './auth/guards'
import { LoginPage } from './auth/LoginPage'
import { Permission } from './auth/permissions'
import { ProfilePage } from './auth/ProfilePage'
import { AppLayout } from './layout/AppLayout'
import { PlaceholderPage } from './layout/PlaceholderPage'
import { NotFoundPage, RouteErrorPage } from './layout/StatusPages'

/** Routes de l'application (spec §12.3). Les pages des fonctionnalités remplaceront les pages provisoires. */
export const routes: RouteObject[] = [
  {
    errorElement: <RouteErrorPage />,
    children: [
      { path: '/login', element: <LoginPage /> },
      {
        path: '/change-password',
        element: (
          <RequireAuth>
            <ChangePasswordPage />
          </RequireAuth>
        ),
      },
      {
        path: '/',
        element: (
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        ),
        children: [
          { index: true, element: <PlaceholderPage title="Tableau de bord" /> },
          {
            path: 'servers/*',
            element: (
              <RequirePermission permission={Permission.SERVER_READ}>
                <PlaceholderPage title="VPS" />
              </RequirePermission>
            ),
          },
          {
            path: 'projects/*',
            element: (
              <RequirePermission permission={Permission.PROJECT_READ}>
                <PlaceholderPage title="Projets" />
              </RequirePermission>
            ),
          },
          {
            path: 'users',
            element: (
              <RequirePermission permission={Permission.USER_READ}>
                <PlaceholderPage title="Utilisateurs" />
              </RequirePermission>
            ),
          },
          {
            path: 'audit',
            element: (
              <RequirePermission permission={Permission.AUDIT_READ}>
                <PlaceholderPage title="Journal d'audit" />
              </RequirePermission>
            ),
          },
          { path: 'profile', element: <ProfilePage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]
