import {
  Autocomplete,
  Button,
  Center,
  Group,
  Loader,
  NumberInput,
  Paper,
  Select,
  SimpleGrid,
  Stack,
  TagsInput,
  Textarea,
  TextInput,
  Title,
} from '@mantine/core'
import { useForm } from '@mantine/form'
import { notifications } from '@mantine/notifications'
import { Link, useNavigate, useParams } from 'react-router'
import { applyError } from '../../api/applyProblem'
import { QueryError } from '../../components/QueryError'
import {
  useCreateServer,
  useServer,
  useServerFacets,
  useUpdateServer,
  type Server,
  type ServerPayload,
} from './api'
import { ADMIN_STATUSES, OS_FAMILIES } from './labels'

type ServerFormValues = {
  name: string
  hostname: string
  publicIp: string
  privateIp: string
  provider: string
  region: string
  osFamily: string | null
  osVersion: string
  cpuCores: number | string
  ramMb: number | string
  diskGb: number | string
  adminStatus: string
  description: string
  tags: string[]
}

function toFormValues(server?: Server): ServerFormValues {
  return {
    name: server?.name ?? '',
    hostname: server?.hostname ?? '',
    publicIp: server?.publicIp ?? '',
    privateIp: server?.privateIp ?? '',
    provider: server?.provider ?? '',
    region: server?.region ?? '',
    osFamily: server?.osFamily ?? null,
    osVersion: server?.osVersion ?? '',
    cpuCores: server?.cpuCores ?? '',
    ramMb: server?.ramMb ?? '',
    diskGb: server?.diskGb ?? '',
    adminStatus: server?.adminStatus ?? 'ACTIVE',
    description: server?.description ?? '',
    tags: server?.tags ?? [],
  }
}

const text = (value: string) => value.trim() || null
const count = (value: number | string) => (typeof value === 'number' ? value : null)

/** Corps complet : un champ vide devient `null`, ce qui le vide en `PUT` (spec §10.2). */
function toPayload(values: ServerFormValues): ServerPayload {
  return {
    name: values.name.trim(),
    hostname: text(values.hostname),
    publicIp: text(values.publicIp),
    privateIp: text(values.privateIp),
    provider: text(values.provider),
    region: text(values.region),
    osFamily: values.osFamily || null,
    osVersion: text(values.osVersion),
    cpuCores: count(values.cpuCores),
    ramMb: count(values.ramMb),
    diskGb: count(values.diskGb),
    adminStatus: values.adminStatus,
    description: text(values.description),
    tags: values.tags,
  }
}

function ServerEditor({ server }: { server?: Server }) {
  const navigate = useNavigate()
  const facets = useServerFacets()
  const create = useCreateServer()
  const update = useUpdateServer(server?.id ?? '')
  const saving = create.isPending || update.isPending
  const form = useForm<ServerFormValues>({
    mode: 'uncontrolled',
    initialValues: toFormValues(server),
    validate: { name: (value) => (value.trim() ? null : 'Saisissez un nom.') },
  })

  const submit = form.onSubmit((values) => {
    const callbacks = {
      onSuccess: (saved: Server) => {
        notifications.show({ color: 'green', message: `VPS « ${saved.name} » enregistré.` })
        navigate(`/servers/${saved.id}`, { replace: true })
      },
      onError: (error: unknown) => applyError(error, form),
    }
    const payload = toPayload(values)
    if (server === undefined) {
      create.mutate(payload, callbacks)
    } else {
      update.mutate({ payload, version: server.version ?? 0 }, callbacks)
    }
  })

  return (
    <Stack>
      <Title order={2}>{server === undefined ? 'Nouveau VPS' : `Modifier « ${server.name} »`}</Title>
      <Paper withBorder p="md">
        <form onSubmit={submit} noValidate>
          <Stack>
            <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md">
              <TextInput label="Nom" withAsterisk key={form.key('name')} {...form.getInputProps('name')} />
              <Select
                label="Statut administratif"
                data={ADMIN_STATUSES.map(({ value, label }) => ({ value, label }))}
                allowDeselect={false}
                key={form.key('adminStatus')}
                {...form.getInputProps('adminStatus')}
              />
              <TextInput label="Hostname" placeholder="vps-01.exemple.fr" key={form.key('hostname')} {...form.getInputProps('hostname')} />
              <TextInput label="IP publique" key={form.key('publicIp')} {...form.getInputProps('publicIp')} />
              <TextInput label="IP privée" key={form.key('privateIp')} {...form.getInputProps('privateIp')} />
              <Autocomplete label="Fournisseur" data={facets.data?.providers ?? []} key={form.key('provider')} {...form.getInputProps('provider')} />
              <Autocomplete label="Région" data={facets.data?.regions ?? []} key={form.key('region')} {...form.getInputProps('region')} />
              <Select
                label="Système"
                data={OS_FAMILIES.map(({ value, label }) => ({ value, label }))}
                clearable
                key={form.key('osFamily')}
                {...form.getInputProps('osFamily')}
              />
              <TextInput label="Version du système" placeholder="24.04" key={form.key('osVersion')} {...form.getInputProps('osVersion')} />
              <NumberInput label="Cœurs CPU" min={1} max={1024} allowDecimal={false} key={form.key('cpuCores')} {...form.getInputProps('cpuCores')} />
              <NumberInput label="Mémoire (Mo)" min={1} max={16_777_216} allowDecimal={false} key={form.key('ramMb')} {...form.getInputProps('ramMb')} />
              <NumberInput label="Disque (Go)" min={1} max={1_048_576} allowDecimal={false} key={form.key('diskGb')} {...form.getInputProps('diskGb')} />
            </SimpleGrid>
            <TagsInput
              label="Tags"
              description="Mis en minuscules ; 20 au maximum."
              data={facets.data?.tags ?? []}
              key={form.key('tags')}
              {...form.getInputProps('tags')}
            />
            <Textarea label="Description" autosize minRows={3} key={form.key('description')} {...form.getInputProps('description')} />
            <Group justify="flex-end">
              <Button component={Link} to={server === undefined ? '/servers' : `/servers/${server.id}`} variant="default">
                Annuler
              </Button>
              <Button type="submit" loading={saving}>
                Enregistrer
              </Button>
            </Group>
          </Stack>
        </form>
      </Paper>
    </Stack>
  )
}

function EditServer({ id }: { id: string }) {
  const server = useServer(id)
  if (server.isPending) {
    return (
      <Center py="xl">
        <Loader aria-label="Chargement" />
      </Center>
    )
  }
  if (server.isError) {
    return <QueryError error={server.error} />
  }
  return <ServerEditor server={server.data} />
}

/** Création ou modification d'un VPS (spec §12.3) ; la modification envoie l'objet complet en `PUT`. */
export function ServerFormPage() {
  const { id } = useParams()
  return id === undefined ? <ServerEditor /> : <EditServer id={id} />
}
