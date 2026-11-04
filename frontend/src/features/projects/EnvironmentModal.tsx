import { Button, Group, Modal, Select, Stack, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { applyError } from '../../api/applyProblem'
import { useCreateEnvironment, useUpdateEnvironment, type Environment } from './api'
import { ENVIRONMENT_KINDS } from './labels'

type Props = {
  opened: boolean
  onClose: () => void
  projectId: string
  /** Environnement à modifier ; absent : création. */
  environment?: Environment
}

function EnvironmentForm({ projectId, environment, onClose }: Omit<Props, 'opened'>) {
  const create = useCreateEnvironment(projectId)
  const update = useUpdateEnvironment(environment?.id ?? '')
  const saving = create.isPending || update.isPending
  const form = useForm<{ name: string; kind: string | null }>({
    mode: 'uncontrolled',
    initialValues: { name: environment?.name ?? '', kind: environment?.kind ?? null },
    validate: {
      name: (value) => (value.trim() ? null : 'Saisissez un nom.'),
      kind: (value) => (value ? null : 'Choisissez une nature.'),
    },
  })

  const submit = form.onSubmit((values) => {
    const payload = { name: values.name.trim(), kind: values.kind ?? '' }
    const callbacks = { onSuccess: onClose, onError: (error: unknown) => applyError(error, form) }
    if (environment === undefined) {
      create.mutate(payload, callbacks)
    } else {
      update.mutate({ payload, version: environment.version ?? 0 }, callbacks)
    }
  })

  return (
    <form onSubmit={submit} noValidate>
      <Stack>
        <TextInput
          label="Nom"
          description="Lettres, chiffres, « . », « _ » ou « - »."
          withAsterisk
          data-autofocus
          key={form.key('name')}
          {...form.getInputProps('name')}
        />
        <Select
          label="Nature"
          withAsterisk
          data={ENVIRONMENT_KINDS.map(({ value, label }) => ({ value, label }))}
          allowDeselect={false}
          key={form.key('kind')}
          {...form.getInputProps('kind')}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Annuler
          </Button>
          <Button type="submit" loading={saving}>
            Enregistrer
          </Button>
        </Group>
      </Stack>
    </form>
  )
}

export function EnvironmentModal({ opened, onClose, projectId, environment }: Props) {
  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={environment === undefined ? 'Nouvel environnement' : "Modifier l'environnement"}
    >
      <EnvironmentForm projectId={projectId} environment={environment} onClose={onClose} />
    </Modal>
  )
}
