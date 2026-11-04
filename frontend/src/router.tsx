import { Outlet, type RouteObject } from 'react-router'
import { ChangePasswordPage } from './auth/ChangePasswordPage'
import { RequireAuth, RequirePermission } from './auth/guards'
import { LoginPage } from './auth/LoginPage'
import { Permission } from './auth/permissions'
import { ProfilePage } from './auth/ProfilePage'
import { AuditPage } from './features/audit/AuditPage'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { ProjectDetailPage } from './features/projects/ProjectDetailPage'
import { ProjectsListPage } from './features/projects/ProjectsListPage'
import { ServerDetailPage } from './features/servers/ServerDetailPage'
import { ServerFormPage } from './features/servers/ServerFormPage'
import { ServersListPage } from './features/servers/ServersListPage'
import { UsersPage } from './features/users/UsersPage'
import { AppLayout } from './layout/AppLayout'
import { NotFoundPage, RouteErrorPage } from './layout/StatusPages'

/** Routes de l'application (spec §12.3). */
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
          { index: true, element: <DashboardPage /> },
          {
            path: 'servers',
            element: (
              <RequirePermission permission={Permission.SERVER_READ}>
                <Outlet />
              </RequirePermission>
            ),
            children: [
              { index: true, element: <ServersListPage /> },
              {
                path: 'new',
                element: (
                  <RequirePermission permission={Permission.SERVER_WRITE}>
                    <ServerFormPage />
                  </RequirePermission>
                ),
              },
              { path: ':id', element: <ServerDetailPage /> },
              {
                path: ':id/edit',
                element: (
                  <RequirePermission permission={Permission.SERVER_WRITE}>
                    <ServerFormPage />
                  </RequirePermission>
                ),
              },
            ],
          },
          {
            path: 'projects',
            element: (
              <RequirePermission permission={Permission.PROJECT_READ}>
                <Outlet />
              </RequirePermission>
            ),
            children: [
              { index: true, element: <ProjectsListPage /> },
              { path: ':id', element: <ProjectDetailPage /> },
            ],
          },
          {
            path: 'users',
            element: (
              <RequirePermission permission={Permission.USER_READ}>
                <UsersPage />
              </RequirePermission>
            ),
          },
          {
            path: 'audit',
            element: (
              <RequirePermission permission={Permission.AUDIT_READ}>
                <AuditPage />
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
