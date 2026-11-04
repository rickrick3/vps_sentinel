import { Anchor, Badge, Button, Center, Group, Loader, Paper, Stack, Table, Tabs, Text, Title } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { applyError } from '../../api/applyProblem'
import { useAuth } from '../../auth/AuthProvider'
import { can, Permission } from '../../auth/permissions'
import { ConfirmDelete } from '../../components/ConfirmDelete'
import { orDash } from '../../components/format'
import { QueryError } from '../../components/QueryError'
import { adminStatus } from '../servers/labels'
import {
  useDeleteEnvironment,
  useDeleteProject,
  useDetachDeployment,
  useProject,
  useRemoveDomain,
  type Deployment,
  type Domain,
  type Environment,
} from './api'
import { DeploymentModal } from './DeploymentModal'
import { DomainModal } from './DomainModal'
import { EnvironmentModal } from './EnvironmentModal'
import { environmentKind, isHttpUrl } from './labels'
import { ProjectModal } from './ProjectModal'

type OpenModal =
  | { kind: 'project' }
  | { kind: 'environment'; environment?: Environment }
  | { kind: 'domain'; environment: Environment }
  | { kind: 'deployment'; environment: Environment }

type Removal =
  | { kind: 'project' }
  | { kind: 'environment'; environment: Environment }
  | { kind: 'domain'; domain: Domain }
  | { kind: 'deployment'; deployment: Deployment }

type PanelActions = {
  canWrite: boolean
  canDelete: boolean
  open: (modal: OpenModal) => void
  remove: (removal: Removal) => void
}

function EnvironmentPanel({ environment, actions }: { environment: Environment; actions: PanelActions }) {
  const kind = environmentKind(environment.kind)
  const domains = environment.domains ?? []
  const deployments = environment.deployments ?? []
  return (
    <Stack gap="lg" pt="md">
      <Group justify="space-between">
        <Badge color={kind.color} variant="light">
          {kind.label}
        </Badge>
        <Group gap="xs">
          {actions.canWrite && (
            <Button variant="default" size="xs" onClick={() => actions.open({ kind: 'environment', environment })}>
              Modifier l'environnement
            </Button>
          )}
          {actions.canDelete && (
            <Button color="red" variant="light" size="xs" onClick={() => actions.remove({ kind: 'environment', environment })}>
              Supprimer l'environnement
            </Button>
          )}
        </Group>
      </Group>

      <Stack gap="xs">
        <Group justify="space-between">
          <Title order={4}>Domaines</Title>
          {actions.canWrite && (
            <Button size="xs" variant="light" onClick={() => actions.open({ kind: 'domain', environment })}>
              Ajouter un domaine
            </Button>
          )}
        </Group>
        {domains.length === 0 ? (
          <Text c="dimmed" size="sm">Aucun domaine.</Text>
        ) : (
          <Table aria-label={`Domaines de ${environment.name}`} striped>
            <Table.Tbody>
              {domains.map((domain) => (
                <Table.Tr key={domain.id}>
                  <Table.Td>
                    <Group gap="xs">
                      {domain.hostname}
                      {domain.isPrimary && <Badge size="sm">Principal</Badge>}
                    </Group>
                  </Table.Td>
                  <Table.Td ta="right">
                    {actions.canWrite && (
                      <Button
                        size="xs"
                        variant="subtle"
                        color="red"
                        aria-label={`Retirer ${domain.hostname}`}
                        onClick={() => actions.remove({ kind: 'domain', domain })}
                      >
                        Retirer
                      </Button>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Stack>

      <Stack gap="xs">
        <Group justify="space-between">
          <Title order={4}>Déploiements</Title>
          {actions.canWrite && (
            <Button size="xs" variant="light" onClick={() => actions.open({ kind: 'deployment', environment })}>
              Rattacher un VPS
            </Button>
          )}
        </Group>
        {deployments.length === 0 ? (
          <Text c="dimmed" size="sm">Aucun VPS rattaché.</Text>
        ) : (
          <Table aria-label={`Déploiements de ${environment.name}`} striped>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>VPS</Table.Th>
                <Table.Th>Statut</Table.Th>
                <Table.Th>Rôle</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {deployments.map((deployment) => {
                const status = adminStatus(deployment.serverAdminStatus)
                return (
                  <Table.Tr key={deployment.id}>
                    <Table.Td>
                      <Anchor component={Link} to={`/servers/${deployment.serverId}`}>
                        {deployment.serverName}
                      </Anchor>
                    </Table.Td>
                    <Table.Td>
                      <Badge color={status.color} variant="light" size="sm">
                        {status.label}
                      </Badge>
                    </Table.Td>
                    <Table.Td>{orDash(deployment.role)}</Table.Td>
                    <Table.Td ta="right">
                      {actions.canWrite && (
                        <Button
                          size="xs"
                          variant="subtle"
                          color="red"
                          aria-label={`Détacher ${deployment.serverName}`}
                          onClick={() => actions.remove({ kind: 'deployment', deployment })}
                        >
                          Détacher
                        </Button>
                      )}
                    </Table.Td>
                  </Table.Tr>
                )
              })}
            </Table.Tbody>
          </Table>
        )}
      </Stack>
    </Stack>
  )
}

function removalText(removal: Removal, projectName: string): { title: string; message: string } {
  switch (removal.kind) {
    case 'project':
      return {
        title: 'Supprimer le projet',
        message: `Le projet « ${projectName} », ses environnements, leurs domaines et leurs déploiements seront supprimés.`,
      }
    case 'environment':
      return {
        title: "Supprimer l'environnement",
        message: `L'environnement « ${removal.environment.name} », ses domaines et ses déploiements seront supprimés.`,
      }
    case 'domain':
      return { title: 'Retirer le domaine', message: `Le domaine « ${removal.domain.hostname} » sera retiré.` }
    case 'deployment':
      return {
        title: 'Détacher le VPS',
        message: `Le VPS « ${removal.deployment.serverName} » ne sera plus rattaché à cet environnement.`,
      }
  }
}

/** Fiche projet (spec §12.3) : un onglet par environnement, avec ses domaines et ses déploiements. */
export function ProjectDetailPage() {
  const { id = '' } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const project = useProject(id)
  const deleteProject = useDeleteProject()
  const deleteEnvironment = useDeleteEnvironment()
  const removeDomain = useRemoveDomain()
  const detachDeployment = useDetachDeployment()
  const [modal, setModal] = useState<OpenModal | null>(null)
  const [removal, setRemoval] = useState<Removal | null>(null)
  const [activeTab, setActiveTab] = useState<string | null>(null)

  if (project.isPending) {
    return (
      <Center py="xl">
        <Loader aria-label="Chargement" />
      </Center>
    )
  }
  if (project.isError) {
    return <QueryError error={project.error} />
  }

  const detail = project.data
  const environments = detail.environments ?? []
  const currentTab = environments.some((environment) => environment.id === activeTab) ? activeTab : (environments[0]?.id ?? null)
  const actions: PanelActions = {
    canWrite: can(user, Permission.PROJECT_WRITE),
    canDelete: can(user, Permission.PROJECT_DELETE),
    open: setModal,
    remove: setRemoval,
  }
  const closeModal = () => setModal(null)
  const removing = deleteProject.isPending || deleteEnvironment.isPending || removeDomain.isPending || detachDeployment.isPending

  const confirmRemoval = () => {
    if (removal === null) {
      return
    }
    const onError = (error: unknown) => {
      setRemoval(null)
      applyError(error)
    }
    switch (removal.kind) {
      case 'project':
        deleteProject.mutate(id, {
          onSuccess: () => {
            notifications.show({ color: 'green', message: `Projet « ${detail.name} » supprimé.` })
            navigate('/projects', { replace: true })
          },
          onError,
        })
        break
      case 'environment':
        deleteEnvironment.mutate(removal.environment.id ?? '', { onSuccess: () => setRemoval(null), onError })
        break
      case 'domain':
        removeDomain.mutate(removal.domain.id ?? '', { onSuccess: () => setRemoval(null), onError })
        break
      case 'deployment':
        detachDeployment.mutate(removal.deployment.id ?? '', { onSuccess: () => setRemoval(null), onError })
        break
    }
  }

  return (
    <Stack gap="lg">
      <Anchor component={Link} to="/projects" size="sm">
        <span aria-hidden="true">← </span>Tous les projets
      </Anchor>
      <Group justify="space-between" align="flex-start">
        <Stack gap={4}>
          <Title order={2}>{detail.name}</Title>
          {detail.repositoryUrl &&
            (isHttpUrl(detail.repositoryUrl) ? (
              <Anchor href={detail.repositoryUrl} target="_blank" rel="noopener noreferrer" size="sm">
                {detail.repositoryUrl}
              </Anchor>
            ) : (
              <Text size="sm" c="dimmed">
                {detail.repositoryUrl}
              </Text>
            ))}
        </Stack>
        <Group gap="xs">
          {actions.canWrite && (
            <Button variant="default" onClick={() => setModal({ kind: 'project' })}>
              Modifier le projet
            </Button>
          )}
          {actions.canDelete && (
            <Button color="red" variant="light" onClick={() => setRemoval({ kind: 'project' })}>
              Supprimer le projet
            </Button>
          )}
        </Group>
      </Group>
      {detail.description && (
        <Paper withBorder p="md">
          {/* Texte brut (spec §12.4) : retours à la ligne conservés, aucun HTML interprété. */}
          <Text style={{ whiteSpace: 'pre-wrap' }}>{detail.description}</Text>
        </Paper>
      )}

      <Stack gap="xs">
        <Group justify="space-between">
          <Title order={3}>Environnements</Title>
          {actions.canWrite && (
            <Button variant="light" onClick={() => setModal({ kind: 'environment' })}>
              Ajouter un environnement
            </Button>
          )}
        </Group>
        {environments.length === 0 ? (
          <Text c="dimmed">Aucun environnement.</Text>
        ) : (
          <Tabs value={currentTab} onChange={setActiveTab} keepMounted={false}>
            <Tabs.List>
              {environments.map((environment) => (
                <Tabs.Tab key={environment.id} value={environment.id ?? ''}>
                  {environment.name}
                </Tabs.Tab>
              ))}
            </Tabs.List>
            {environments.map((environment) => (
              <Tabs.Panel key={environment.id} value={environment.id ?? ''}>
                <EnvironmentPanel environment={environment} actions={actions} />
              </Tabs.Panel>
            ))}
          </Tabs>
        )}
      </Stack>

      <ProjectModal
        opened={modal?.kind === 'project'}
        onClose={closeModal}
        project={detail}
        onSaved={() => {
          closeModal()
          notifications.show({ color: 'green', message: 'Projet enregistré.' })
        }}
      />
      <EnvironmentModal
        opened={modal?.kind === 'environment'}
        onClose={closeModal}
        projectId={id}
        environment={modal?.kind === 'environment' ? modal.environment : undefined}
      />
      {modal?.kind === 'domain' && (
        <DomainModal
          opened
          onClose={closeModal}
          environmentId={modal.environment.id ?? ''}
          environmentName={modal.environment.name ?? ''}
        />
      )}
      {modal?.kind === 'deployment' && (
        <DeploymentModal
          opened
          onClose={closeModal}
          environmentId={modal.environment.id ?? ''}
          environmentName={modal.environment.name ?? ''}
        />
      )}
      {removal !== null && (
        <ConfirmDelete
          opened
          title={removalText(removal, detail.name ?? '').title}
          loading={removing}
          onConfirm={confirmRemoval}
          onClose={() => setRemoval(null)}
        >
          {removalText(removal, detail.name ?? '').message}
        </ConfirmDelete>
      )}
    </Stack>
  )
}
