import { Button, Checkbox, Group, Modal, Stack, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { applyError } from '../../api/applyProblem'
import { useAddDomain } from './api'

type Props = {
  opened: boolean
  onClose: () => void
  environmentId: string
  environmentName: string
}

function DomainForm({ environmentId, onClose }: { environmentId: string; onClose: () => void }) {
  const add = useAddDomain(environmentId)
  const form = useForm({
    mode: 'uncontrolled',
    initialValues: { hostname: '', isPrimary: false },
    validate: { hostname: (value) => (value.trim() ? null : 'Saisissez un nom de domaine.') },
  })

  const submit = form.onSubmit((values) =>
    add.mutate(
      { hostname: values.hostname.trim(), isPrimary: values.isPrimary },
      { onSuccess: onClose, onError: (error) => applyError(error, form) },
    ))

  return (
    <form onSubmit={submit} noValidate>
      <Stack>
        <TextInput
          label="Nom de domaine"
          placeholder="app.exemple.fr"
          description="Au moins un point ; noms internationalisés en punycode (xn--)."
          withAsterisk
          data-autofocus
          key={form.key('hostname')}
          {...form.getInputProps('hostname')}
        />
        <Checkbox
          label="Domaine principal"
          description="Remplace le domaine principal actuel de l'environnement."
          key={form.key('isPrimary')}
          {...form.getInputProps('isPrimary', { type: 'checkbox' })}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Annuler
          </Button>
          <Button type="submit" loading={add.isPending}>
            Ajouter
          </Button>
        </Group>
      </Stack>
    </form>
  )
}

export function DomainModal({ opened, onClose, environmentId, environmentName }: Props) {
  return (
    <Modal opened={opened} onClose={onClose} title={`Nouveau domaine pour ${environmentName}`}>
      <DomainForm environmentId={environmentId} onClose={onClose} />
    </Modal>
  )
}
