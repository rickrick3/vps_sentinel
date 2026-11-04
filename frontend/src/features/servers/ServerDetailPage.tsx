import { Anchor, Badge, Button, Center, Group, Loader, Paper, SimpleGrid, Stack, Table, Text, Title } from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { notifications } from '@mantine/notifications'
import type { ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { applyError } from '../../api/applyProblem'
import { useAuth } from '../../auth/AuthProvider'
import { can, Permission } from '../../auth/permissions'
import { ConfirmDelete } from '../../components/ConfirmDelete'
import { formatDateTime, orDash } from '../../components/format'
import { QueryError } from '../../components/QueryError'
import { useDeleteServer, useServer, useServerDeployments } from './api'
import { adminStatus, osFamilyLabel } from './labels'

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
        {label}
      </Text>
      <Text component="div">{children}</Text>
    </div>
  )
}

function HostedEnvironments({ id }: { id: string }) {
  const { user } = useAuth()
  const allowed = can(user, Permission.PROJECT_READ)
  const hosted = useServerDeployments(id, allowed)
  if (!allowed) {
    return null
  }
  return (
    <Stack gap="xs">
      <Title order={3}>Environnements hébergés</Title>
      {hosted.isPending && <Loader size="sm" aria-label="Chargement" />}
      {hosted.isError && <QueryError error={hosted.error} />}
      {hosted.data?.length === 0 && <Text c="dimmed">Aucun environnement n'est déployé sur ce VPS.</Text>}
      {hosted.data !== undefined && hosted.data.length > 0 && (
        <Table aria-label="Environnements hébergés" striped>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Projet</Table.Th>
              <Table.Th>Environnement</Table.Th>
              <Table.Th>Rôle</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {hosted.data.map((environment) => (
              <Table.Tr key={environment.deploymentId}>
                <Table.Td>
                  <Anchor component={Link} to={`/projects/${environment.projectId}`}>
                    {environment.projectName}
                  </Anchor>
                </Table.Td>
                <Table.Td>{environment.environmentName}</Table.Td>
                <Table.Td>{orDash(environment.role)}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </Stack>
  )
}

/** Fiche d'un VPS (spec §12.3) et environnements qu'il héberge. */
export function ServerDetailPage() {
  const { id = '' } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const server = useServer(id)
  const remove = useDeleteServer()
  const [confirming, { open: confirm, close: cancel }] = useDisclosure(false)

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

  const vps = server.data
  const status = adminStatus(vps.adminStatus)
  const deleteServer = () =>
    remove.mutate(id, {
      onSuccess: () => {
        notifications.show({ color: 'green', message: `VPS « ${vps.name} » supprimé.` })
        navigate('/servers', { replace: true })
      },
      onError: (error) => {
        cancel()
        applyError(error)
      },
    })

  return (
    <Stack gap="lg">
      <Anchor component={Link} to="/servers" size="sm">
        <span aria-hidden="true">← </span>Tous les VPS
      </Anchor>
      <Group justify="space-between">
        <Group gap="sm">
          <Title order={2}>{vps.name}</Title>
          <Badge color={status.color} variant="light">
            {status.label}
          </Badge>
        </Group>
        <Group gap="xs">
          {can(user, Permission.SERVER_WRITE) && (
            <Button component={Link} to={`/servers/${id}/edit`} variant="default">
              Modifier
            </Button>
          )}
          {can(user, Permission.SERVER_DELETE) && (
            <Button color="red" variant="light" onClick={confirm}>
              Supprimer
            </Button>
          )}
        </Group>
      </Group>
      <Paper withBorder p="md">
        <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }} spacing="md">
          <Field label="Hostname">{orDash(vps.hostname)}</Field>
          <Field label="IP publique">{orDash(vps.publicIp)}</Field>
          <Field label="IP privée">{orDash(vps.privateIp)}</Field>
          <Field label="Fournisseur">{orDash(vps.provider)}</Field>
          <Field label="Région">{orDash(vps.region)}</Field>
          <Field label="Système">{orDash([osFamilyLabel(vps.osFamily), vps.osVersion].filter(Boolean).join(' '))}</Field>
          <Field label="Cœurs CPU">{orDash(vps.cpuCores)}</Field>
          <Field label="Mémoire">{vps.ramMb ? `${vps.ramMb} Mo` : '—'}</Field>
          <Field label="Disque">{vps.diskGb ? `${vps.diskGb} Go` : '—'}</Field>
          <Field label="Créé le">{formatDateTime(vps.createdAt)}</Field>
          <Field label="Modifié le">{formatDateTime(vps.updatedAt)}</Field>
          <Field label="Tags">
            {vps.tags?.length ? (
              <Group gap={4}>
                {vps.tags.map((tag) => (
                  <Badge key={tag} variant="outline" size="sm">
                    {tag}
                  </Badge>
                ))}
              </Group>
            ) : (
              '—'
            )}
          </Field>
        </SimpleGrid>
        {vps.description && (
          <Stack gap={2} mt="md">
            <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
              Description
            </Text>
            {/* Texte brut (spec §12.4) : retours à la ligne conservés, aucun HTML interprété. */}
            <Text style={{ whiteSpace: 'pre-wrap' }}>{vps.description}</Text>
          </Stack>
        )}
      </Paper>
      <HostedEnvironments id={id} />
      <ConfirmDelete
        opened={confirming}
        title="Supprimer le VPS"
        loading={remove.isPending}
        onConfirm={deleteServer}
        onClose={cancel}
      >
        Le VPS « {vps.name} » sera supprimé. Un VPS qui héberge encore des environnements ne peut pas l'être.
      </ConfirmDelete>
    </Stack>
  )
}
