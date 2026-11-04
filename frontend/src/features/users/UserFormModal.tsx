import { Button, Group, Modal, Select, Stack, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { notifications } from '@mantine/notifications'
import { applyError } from '../../api/applyProblem'
import { usePatchUser, type useCreateUser, type User, type UserChanges } from './api'
import { ROLE_OPTIONS, STATUS_OPTIONS } from './labels'

type CreateProps = { onClose: () => void; createUser: ReturnType<typeof useCreateUser> }

function CreateUserForm({ onClose, createUser }: CreateProps) {
  const form = useForm<{ email: string; fullName: string; role: string | null }>({
    mode: 'uncontrolled',
    initialValues: { email: '', fullName: '', role: null },
    validate: {
      email: (value) => (value.trim() ? null : 'Saisissez un email.'),
      fullName: (value) => (value.trim() ? null : 'Saisissez un nom.'),
      role: (value) => (value ? null : 'Choisissez un rôle.'),
    },
  })

  // Le mot de passe temporaire reste porté par la mutation de la page, qui l'affiche après la fermeture du formulaire.
  const submit = form.onSubmit((values) =>
    createUser.mutate(
      { email: values.email.trim(), fullName: values.fullName.trim(), role: values.role ?? '' },
      { onSuccess: onClose, onError: (error) => applyError(error, form) },
    ))

  return (
    <form onSubmit={submit} noValidate>
      <Stack>
        <TextInput label="Email" type="email" withAsterisk data-autofocus key={form.key('email')} {...form.getInputProps('email')} />
        <TextInput label="Nom complet" withAsterisk key={form.key('fullName')} {...form.getInputProps('fullName')} />
        <Select label="Rôle" withAsterisk data={ROLE_OPTIONS} allowDeselect={false} key={form.key('role')} {...form.getInputProps('role')} />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Annuler
          </Button>
          <Button type="submit" loading={createUser.isPending}>
            Créer
          </Button>
        </Group>
      </Stack>
    </form>
  )
}

function EditUserForm({ user, isSelf, onClose }: { user: User; isSelf: boolean; onClose: () => void }) {
  const patch = usePatchUser(user.id ?? '')
  const initial = { fullName: user.fullName ?? '', role: user.role ?? null, status: user.status ?? null }
  const form = useForm<{ fullName: string; role: string | null; status: string | null }>({
    mode: 'uncontrolled',
    initialValues: initial,
    validate: { fullName: (value) => (value.trim() ? null : 'Saisissez un nom.') },
  })

  // Seuls les champs modifiés partent, avec la version lue : un champ absent reste inchangé (spec §10.2).
  const submit = form.onSubmit((values) => {
    const changes: UserChanges = { version: user.version ?? 0 }
    if (values.fullName.trim() !== initial.fullName) changes.fullName = values.fullName.trim()
    if (values.role !== initial.role && values.role !== null) changes.role = values.role as UserChanges['role']
    if (values.status !== initial.status && values.status !== null) changes.status = values.status as UserChanges['status']
    if (Object.keys(changes).length === 1) {
      onClose()
      return
    }
    patch.mutate(changes, {
      onSuccess: () => {
        notifications.show({ color: 'green', message: `Utilisateur « ${values.fullName.trim()} » modifié.` })
        onClose()
      },
      onError: (error) => applyError(error, form),
    })
  })

  const selfNotice = isSelf ? 'Vous ne pouvez pas modifier votre propre rôle ni votre statut.' : undefined
  return (
    <form onSubmit={submit} noValidate>
      <Stack>
        <TextInput label="Email" value={user.email ?? ''} disabled readOnly />
        <TextInput label="Nom complet" withAsterisk data-autofocus key={form.key('fullName')} {...form.getInputProps('fullName')} />
        <Select
          label="Rôle"
          data={ROLE_OPTIONS}
          allowDeselect={false}
          disabled={isSelf}
          description={selfNotice}
          key={form.key('role')}
          {...form.getInputProps('role')}
        />
        <Select
          label="Statut"
          data={STATUS_OPTIONS}
          allowDeselect={false}
          disabled={isSelf}
          key={form.key('status')}
          {...form.getInputProps('status')}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Annuler
          </Button>
          <Button type="submit" loading={patch.isPending}>
            Enregistrer
          </Button>
        </Group>
      </Stack>
    </form>
  )
}

type Props = {
  opened: boolean
  onClose: () => void
  /** Utilisateur à modifier ; absent : création. */
  user?: User
  isSelf?: boolean
  createUser: ReturnType<typeof useCreateUser>
}

/** Création (email, nom, rôle) ou modification partielle d'un utilisateur. */
export function UserFormModal({ opened, onClose, user, isSelf = false, createUser }: Props) {
  return (
    <Modal opened={opened} onClose={onClose} title={user === undefined ? 'Nouvel utilisateur' : `Modifier ${user.fullName}`}>
      {user === undefined ? (
        <CreateUserForm onClose={onClose} createUser={createUser} />
      ) : (
        <EditUserForm user={user} isSelf={isSelf} onClose={onClose} />
      )}
    </Modal>
  )
}
