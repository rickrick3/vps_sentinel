import { Button, Group, Modal, Select, Stack, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { applyError } from '../../api/applyProblem'
import { ApiError, problemCode } from '../../api/problem'
import { useServers } from '../servers/api'
import { useAttachServer } from './api'

type Props = {
  opened: boolean
  onClose: () => void
  environmentId: string
  environmentName: string
}

// Une page de 100 VPS, le maximum de l'API (spec §10.2), triée par nom.
const SERVER_CHOICES = { page: 0, size: 100, sort: 'name,asc' }

function DeploymentForm({ environmentId, onClose }: { environmentId: string; onClose: () => void }) {
  const attach = useAttachServer(environmentId)
  const servers = useServers(SERVER_CHOICES)
  const form = useForm<{ serverId: string | null; role: string }>({
    mode: 'uncontrolled',
    initialValues: { serverId: null, role: '' },
    validate: { serverId: (value) => (value ? null : 'Choisissez un VPS.') },
  })

  // Un VPS décommissionné ne peut pas recevoir de déploiement (spec §9.4) : il n'est pas proposé.
  const choices = (servers.data?.items ?? [])
    .filter((server) => server.adminStatus !== 'DECOMMISSIONED')
    .map((server) => ({
      value: server.id ?? '',
      label: server.adminStatus === 'MAINTENANCE' ? `${server.name} (en maintenance)` : (server.name ?? ''),
    }))

  const submit = form.onSubmit((values) =>
    attach.mutate(
      { serverId: values.serverId ?? '', role: values.role.trim() || undefined },
      {
        onSuccess: onClose,
        onError: (error) => {
          const code = error instanceof ApiError ? problemCode(error.problem) : null
          if (code === 'deployment-exists') {
            form.setFieldError('serverId', 'Ce VPS est déjà rattaché à cet environnement.')
          } else if (code === 'server-not-deployable' && error instanceof ApiError) {
            form.setFieldError('serverId', error.problem.detail ?? 'Ce VPS ne peut pas recevoir de déploiement.')
          } else {
            applyError(error, form)
          }
        },
      },
    ))

  return (
    <form onSubmit={submit} noValidate>
      <Stack>
        <Select
          label="VPS"
          withAsterisk
          searchable
          data={choices}
          nothingFoundMessage="Aucun VPS"
          disabled={servers.isPending}
          data-autofocus
          key={form.key('serverId')}
          {...form.getInputProps('serverId')}
        />
        <TextInput label="Rôle" placeholder="web, db, worker…" key={form.key('role')} {...form.getInputProps('role')} />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Annuler
          </Button>
          <Button type="submit" loading={attach.isPending}>
            Rattacher
          </Button>
        </Group>
      </Stack>
    </form>
  )
}

export function DeploymentModal({ opened, onClose, environmentId, environmentName }: Props) {
  return (
    <Modal opened={opened} onClose={onClose} title={`Rattacher un VPS à ${environmentName}`}>
      <DeploymentForm environmentId={environmentId} onClose={onClose} />
    </Modal>
  )
}
