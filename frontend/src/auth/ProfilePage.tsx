import { Badge, Button, Group, Paper, Stack, Text, Title } from '@mantine/core'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { useAuth } from './AuthProvider'
import { roleLabel } from './permissions'

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Group gap="md" wrap="nowrap" align="flex-start">
      <Text fw={500} w={120} style={{ flexShrink: 0 }}>
        {label}
      </Text>
      <div>{children}</div>
    </Group>
  )
}

/** Profil de l'utilisateur connecté (spec §12.3). */
export function ProfilePage() {
  const { user } = useAuth()
  if (user === null) {
    return null
  }
  return (
    <Stack gap="md" maw={640}>
      <Title order={2}>Profil</Title>
      <Paper withBorder p="md">
        <Stack gap="sm">
          <Field label="Nom">{user.fullName}</Field>
          <Field label="Email">{user.email}</Field>
          <Field label="Rôle">{roleLabel(user.role)}</Field>
          <Field label="Permissions">
            <Group gap={6}>
              {user.permissions.map((permission) => (
                <Badge key={permission} variant="light">
                  {permission}
                </Badge>
              ))}
            </Group>
          </Field>
        </Stack>
      </Paper>
      <Group>
        <Button component={Link} to="/change-password" variant="light">
          Changer le mot de passe
        </Button>
      </Group>
    </Stack>
  )
}
