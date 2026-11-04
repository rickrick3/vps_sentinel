import { Badge, Button, Group, Select, Stack, TextInput, Title } from '@mantine/core'
import { useDebouncedCallback, useDisclosure } from '@mantine/hooks'
import { notifications } from '@mantine/notifications'
import { useState } from 'react'
import { applyError } from '../../api/applyProblem'
import { useAuth } from '../../auth/AuthProvider'
import { can, Permission, roleLabel } from '../../auth/permissions'
import { ConfirmDelete } from '../../components/ConfirmDelete'
import { formatDateTime } from '../../components/format'
import { PaginatedTable, type Column } from '../../components/PaginatedTable'
import { useListParams } from '../../components/useListParams'
import { useCreateUser, useDeleteUser, useResetPassword, userQuery, useUsers, type User } from './api'
import { ROLE_OPTIONS, STATUS_OPTIONS, statusLabel } from './labels'
import { TemporaryPasswordModal } from './TemporaryPasswordModal'
import { UserFormModal } from './UserFormModal'

type PendingAction = { kind: 'reset' | 'delete'; user: User }

/** Utilisateurs (spec §12.3) : lecture avec `user:read`, création, modification et réinitialisation avec `user:manage`. */
export function UsersPage() {
  const { user: current } = useAuth()
  const canManage = can(current, Permission.USER_MANAGE)
  const { params, setParams } = useListParams({ sort: 'fullName,asc' })
  const users = useUsers(userQuery(params))
  const createUser = useCreateUser()
  const resetPassword = useResetPassword()
  const deleteUser = useDeleteUser()
  const [creating, { open: openCreate, close: closeCreate }] = useDisclosure(false)
  const [editing, setEditing] = useState<User | null>(null)
  const [pending, setPending] = useState<PendingAction | null>(null)
  const [search, setSearch] = useState(params.filters.q ?? '')
  const applySearch = useDebouncedCallback((value: string) => setParams({ filters: { q: value.trim() } }), 300)

  const columns: Column<User>[] = [
    { key: 'fullName', header: 'Nom', sortKey: 'fullName', render: (user) => user.fullName },
    { key: 'email', header: 'Email', sortKey: 'email', render: (user) => user.email },
    { key: 'role', header: 'Rôle', render: (user) => roleLabel(user.role ?? '') },
    {
      key: 'status',
      header: 'Statut',
      render: (user) => (
        <Group gap={4}>
          <Badge color={user.status === 'ACTIVE' ? 'green' : 'gray'} variant="light">
            {statusLabel(user.status)}
          </Badge>
          {user.mustChangePassword && (
            <Badge color="orange" variant="outline" size="sm">
              Mot de passe à changer
            </Badge>
          )}
        </Group>
      ),
    },
    {
      key: 'lastLoginAt',
      header: 'Dernière connexion',
      sortKey: 'lastLoginAt',
      render: (user) => formatDateTime(user.lastLoginAt),
    },
  ]
  if (canManage) {
    columns.push({
      key: 'actions',
      header: 'Actions',
      render: (user) => (
        <Group gap={4} wrap="nowrap">
          <Button size="xs" variant="subtle" aria-label={`Modifier ${user.fullName}`} onClick={() => setEditing(user)}>
            Modifier
          </Button>
          <Button
            size="xs"
            variant="subtle"
            aria-label={`Réinitialiser le mot de passe de ${user.fullName}`}
            onClick={() => setPending({ kind: 'reset', user })}
          >
            Réinitialiser
          </Button>
          {/* Un compte ne peut pas se supprimer lui-même (spec §8.8). */}
          {user.id !== current?.id && (
            <Button
              size="xs"
              variant="subtle"
              color="red"
              aria-label={`Supprimer ${user.fullName}`}
              onClick={() => setPending({ kind: 'delete', user })}
            >
              Supprimer
            </Button>
          )}
        </Group>
      ),
    })
  }

  const confirmPending = () => {
    if (pending === null) {
      return
    }
    const onError = (error: unknown) => {
      setPending(null)
      applyError(error)
    }
    const id = pending.user.id ?? ''
    if (pending.kind === 'reset') {
      resetPassword.mutate(id, { onSuccess: () => setPending(null), onError })
    } else {
      deleteUser.mutate(id, {
        onSuccess: () => {
          setPending(null)
          notifications.show({ color: 'green', message: `Utilisateur « ${pending.user.fullName} » supprimé.` })
        },
        onError,
      })
    }
  }

  // Mot de passe temporaire porté par la mutation qui l'a obtenu ; `reset()` le retire de l'écran et du cache.
  const temporary = createUser.data
    ? { password: createUser.data.temporaryPassword, expiresAt: createUser.data.temporaryPasswordExpiresAt, clear: createUser.reset }
    : resetPassword.data
      ? { password: resetPassword.data.temporaryPassword, expiresAt: resetPassword.data.temporaryPasswordExpiresAt, clear: resetPassword.reset }
      : null

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Utilisateurs</Title>
        {canManage && <Button onClick={openCreate}>Nouvel utilisateur</Button>}
      </Group>
      <Group align="flex-end" gap="sm">
        <TextInput
          label="Rechercher"
          placeholder="Nom ou email"
          value={search}
          onChange={(event) => {
            setSearch(event.currentTarget.value)
            applySearch(event.currentTarget.value)
          }}
          w={240}
        />
        <Select
          label="Rôle"
          data={ROLE_OPTIONS}
          clearable
          value={params.filters.role ?? null}
          onChange={(role) => setParams({ filters: { role } })}
          w={220}
        />
        <Select
          label="Statut"
          data={STATUS_OPTIONS}
          clearable
          value={params.filters.status ?? null}
          onChange={(status) => setParams({ filters: { status } })}
          w={160}
        />
      </Group>
      <PaginatedTable
        label="Utilisateurs"
        columns={columns}
        data={users.data}
        rowKey={(user) => user.id ?? ''}
        params={params}
        setParams={setParams}
        loading={users.isPending}
        error={users.isError}
        emptyMessage="Aucun utilisateur ne correspond à ces critères."
      />

      <UserFormModal opened={creating} onClose={closeCreate} createUser={createUser} />
      {editing !== null && (
        <UserFormModal
          opened
          onClose={() => setEditing(null)}
          user={editing}
          isSelf={editing.id === current?.id}
          createUser={createUser}
        />
      )}
      {pending !== null && (
        <ConfirmDelete
          opened
          title={pending.kind === 'reset' ? 'Réinitialiser le mot de passe' : "Supprimer l'utilisateur"}
          loading={resetPassword.isPending || deleteUser.isPending}
          onConfirm={confirmPending}
          onClose={() => setPending(null)}
        >
          {pending.kind === 'reset'
            ? `Un mot de passe temporaire sera créé pour ${pending.user.fullName}, et ses sessions seront fermées.`
            : `Le compte de ${pending.user.fullName} sera désactivé et supprimé, et ses sessions fermées.`}
        </ConfirmDelete>
      )}
      {temporary !== null && (
        <TemporaryPasswordModal
          password={temporary.password ?? ''}
          expiresAt={temporary.expiresAt ?? ''}
          onClose={temporary.clear}
        />
      )}
    </Stack>
  )
}
