import {
  ActionIcon,
  AppShell,
  Burger,
  Button,
  Group,
  Menu,
  NavLink,
  Title,
  useComputedColorScheme,
  useMantineColorScheme,
} from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { Link, Outlet, useLocation } from 'react-router'
import { useAuth } from '../auth/AuthProvider'
import { can, Permission } from '../auth/permissions'

type NavItem = { to: string; label: string; permission?: Permission }

// Utilisateurs et Audit n'apparaissent qu'avec leur permission (spec §12.4).
const NAVIGATION: NavItem[] = [
  { to: '/', label: 'Tableau de bord' },
  { to: '/servers', label: 'VPS', permission: Permission.SERVER_READ },
  { to: '/projects', label: 'Projets', permission: Permission.PROJECT_READ },
  { to: '/users', label: 'Utilisateurs', permission: Permission.USER_READ },
  { to: '/audit', label: 'Audit', permission: Permission.AUDIT_READ },
]

function isActive(pathname: string, to: string): boolean {
  return to === '/' ? pathname === '/' : pathname === to || pathname.startsWith(`${to}/`)
}

/** Mise en page des pages authentifiées (spec §12.4) : navigation, menu utilisateur, bascule clair/sombre. */
export function AppLayout() {
  const { user, logout } = useAuth()
  const { pathname } = useLocation()
  const [navigationOpened, { toggle: toggleNavigation, close: closeNavigation }] = useDisclosure()
  const { setColorScheme } = useMantineColorScheme()
  const colorScheme = useComputedColorScheme('light')

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{ width: 240, breakpoint: 'sm', collapsed: { mobile: !navigationOpened } }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            <Burger
              opened={navigationOpened}
              onClick={toggleNavigation}
              hiddenFrom="sm"
              size="sm"
              aria-label="Afficher la navigation"
            />
            <Title order={4} component="span">
              VPS Sentinel
            </Title>
          </Group>
          <Group gap="xs" wrap="nowrap">
            <ActionIcon
              variant="default"
              size="lg"
              aria-label="Basculer entre thème clair et sombre"
              onClick={() => setColorScheme(colorScheme === 'light' ? 'dark' : 'light')}
            >
              <span aria-hidden="true">{colorScheme === 'light' ? '☾' : '☀'}</span>
            </ActionIcon>
            <Menu position="bottom-end" width={220}>
              <Menu.Target>
                <Button variant="subtle">{user?.fullName}</Button>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Label>{user?.email}</Menu.Label>
                <Menu.Item component={Link} to="/profile">
                  Profil
                </Menu.Item>
                <Menu.Item color="red" onClick={() => void logout()}>
                  Se déconnecter
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </AppShell.Header>
      <AppShell.Navbar p="xs" component="nav" aria-label="Navigation principale">
        {NAVIGATION.filter((item) => item.permission === undefined || can(user, item.permission)).map((item) => (
          <NavLink
            key={item.to}
            component={Link}
            to={item.to}
            label={item.label}
            active={isActive(pathname, item.to)}
            onClick={closeNavigation}
          />
        ))}
      </AppShell.Navbar>
      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  )
}
