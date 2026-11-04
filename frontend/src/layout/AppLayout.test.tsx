import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test } from 'vitest'
import { ROLE_PERMISSIONS } from '../test/fixtures'
import { renderWithUser } from '../test/render'
import { AppLayout } from './AppLayout'

test('hides Users and Audit for a DEVELOPER', () => {
  renderWithUser(<AppLayout />, { permissions: ['server:read', 'project:read', 'project:write'] })

  expect(screen.queryByRole('link', { name: 'Utilisateurs' })).toBeNull()
  expect(screen.queryByRole('link', { name: 'Audit' })).toBeNull()
  expect(screen.getByRole('link', { name: 'VPS' })).toBeInTheDocument()
})

test('shows Users and Audit for INFRA_ADMIN', () => {
  renderWithUser(<AppLayout />, { role: 'INFRA_ADMIN', permissions: ROLE_PERMISSIONS.INFRA_ADMIN })

  expect(screen.getByRole('link', { name: 'Tableau de bord' })).toHaveAttribute('href', '/')
  expect(screen.getByRole('link', { name: 'VPS' })).toHaveAttribute('href', '/servers')
  expect(screen.getByRole('link', { name: 'Projets' })).toHaveAttribute('href', '/projects')
  expect(screen.getByRole('link', { name: 'Utilisateurs' })).toHaveAttribute('href', '/users')
  expect(screen.getByRole('link', { name: 'Audit' })).toHaveAttribute('href', '/audit')
})

test('marks the current section as active', () => {
  renderWithUser(<AppLayout />, {}, { route: '/servers/42' })

  expect(screen.getByRole('link', { name: 'VPS' })).toHaveAttribute('data-active')
  expect(screen.getByRole('link', { name: 'Tableau de bord' })).not.toHaveAttribute('data-active')
})

test('user menu links to the profile and logs out', async () => {
  const user = userEvent.setup()
  const { auth } = renderWithUser(<AppLayout />, { fullName: 'Alice Martin' })

  await user.click(screen.getByRole('button', { name: 'Alice Martin' }))
  expect(screen.getByRole('menuitem', { name: 'Profil' })).toHaveAttribute('href', '/profile')
  await user.click(screen.getByRole('menuitem', { name: 'Se déconnecter' }))

  expect(auth.logout).toHaveBeenCalledOnce()
})

test('toggles between light and dark themes', async () => {
  const user = userEvent.setup()
  renderWithUser(<AppLayout />)
  const before = document.documentElement.getAttribute('data-mantine-color-scheme')

  await user.click(screen.getByRole('button', { name: 'Basculer entre thème clair et sombre' }))

  expect(document.documentElement.getAttribute('data-mantine-color-scheme')).not.toBe(before)
})
