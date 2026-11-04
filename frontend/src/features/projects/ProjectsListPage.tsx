import { Anchor, Button, Group, Stack, TextInput, Title } from '@mantine/core'
import { useDebouncedCallback, useDisclosure } from '@mantine/hooks'
import { notifications } from '@mantine/notifications'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { can, Permission } from '../../auth/permissions'
import { formatDateTime, orDash } from '../../components/format'
import { PaginatedTable, type Column } from '../../components/PaginatedTable'
import { useListParams } from '../../components/useListParams'
import { projectQuery, useProjects, type ProjectSummary } from './api'
import { ProjectModal } from './ProjectModal'

const COLUMNS: Column<ProjectSummary>[] = [
  {
    key: 'name',
    header: 'Nom',
    sortKey: 'name',
    render: (project) => (
      <Anchor component={Link} to={`/projects/${project.id}`}>
        {project.name}
      </Anchor>
    ),
  },
  { key: 'environmentCount', header: 'Environnements', render: (project) => project.environmentCount ?? 0 },
  { key: 'repositoryUrl', header: 'Dépôt', render: (project) => orDash(project.repositoryUrl) },
  { key: 'createdAt', header: 'Créé le', sortKey: 'createdAt', render: (project) => formatDateTime(project.createdAt) },
]

/** Liste des projets (spec §12.3) ; création dans une modale. */
export function ProjectsListPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { params, setParams } = useListParams({ sort: 'name,asc' })
  const projects = useProjects(projectQuery(params))
  const [creating, { open: create, close: closeCreate }] = useDisclosure(false)
  const [search, setSearch] = useState(params.filters.q ?? '')
  const applySearch = useDebouncedCallback((value: string) => setParams({ filters: { q: value.trim() } }), 300)

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Projets</Title>
        {can(user, Permission.PROJECT_WRITE) && <Button onClick={create}>Nouveau projet</Button>}
      </Group>
      <TextInput
        label="Rechercher"
        placeholder="Nom du projet"
        value={search}
        onChange={(event) => {
          setSearch(event.currentTarget.value)
          applySearch(event.currentTarget.value)
        }}
        w={280}
      />
      <PaginatedTable
        label="Projets"
        columns={COLUMNS}
        data={projects.data}
        rowKey={(project) => project.id ?? ''}
        params={params}
        setParams={setParams}
        loading={projects.isPending}
        error={projects.isError}
        emptyMessage="Aucun projet ne correspond à cette recherche."
      />
      <ProjectModal
        opened={creating}
        onClose={closeCreate}
        onSaved={(project) => {
          closeCreate()
          notifications.show({ color: 'green', message: `Projet « ${project.name} » créé.` })
          navigate(`/projects/${project.id}`)
        }}
      />
    </Stack>
  )
}
