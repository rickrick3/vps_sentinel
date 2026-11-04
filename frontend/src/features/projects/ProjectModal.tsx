import { Button, Group, Modal, Stack, Textarea, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { applyError } from '../../api/applyProblem'
import { useCreateProject, useUpdateProject, type Project, type ProjectPayload } from './api'

type Props = {
  opened: boolean
  onClose: () => void
  /** Projet à modifier ; absent : création. */
  project?: Project
  onSaved: (project: Project) => void
}

function ProjectForm({ project, onClose, onSaved }: Omit<Props, 'opened'>) {
  const create = useCreateProject()
  const update = useUpdateProject(project?.id ?? '')
  const saving = create.isPending || update.isPending
  const form = useForm({
    mode: 'uncontrolled',
    initialValues: {
      name: project?.name ?? '',
      description: project?.description ?? '',
      repositoryUrl: project?.repositoryUrl ?? '',
    },
    validate: { name: (value) => (value.trim() ? null : 'Saisissez un nom.') },
  })

  const submit = form.onSubmit((values) => {
    const payload: ProjectPayload = {
      name: values.name.trim(),
      description: values.description.trim() || null,
      repositoryUrl: values.repositoryUrl.trim() || null,
    }
    const callbacks = { onSuccess: onSaved, onError: (error: unknown) => applyError(error, form) }
    if (project === undefined) {
      create.mutate(payload, callbacks)
    } else {
      update.mutate({ payload, version: project.version ?? 0 }, callbacks)
    }
  })

  return (
    <form onSubmit={submit} noValidate>
      <Stack>
        <TextInput label="Nom" withAsterisk data-autofocus key={form.key('name')} {...form.getInputProps('name')} />
        <TextInput
          label="Dépôt"
          placeholder="https://github.com/organisation/projet"
          description="URL http ou https, sans identifiants."
          key={form.key('repositoryUrl')}
          {...form.getInputProps('repositoryUrl')}
        />
        <Textarea label="Description" autosize minRows={3} key={form.key('description')} {...form.getInputProps('description')} />
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

/** Création ou modification d'un projet ; la modification envoie l'objet complet en `PUT`. */
export function ProjectModal({ opened, onClose, project, onSaved }: Props) {
  return (
    <Modal opened={opened} onClose={onClose} title={project === undefined ? 'Nouveau projet' : 'Modifier le projet'}>
      <ProjectForm project={project} onClose={onClose} onSaved={onSaved} />
    </Modal>
  )
}
