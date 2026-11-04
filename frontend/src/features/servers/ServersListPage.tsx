import { Anchor, Badge, Button, Group, Select, Stack, TextInput, Title } from '@mantine/core'
import { useDebouncedCallback } from '@mantine/hooks'
import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { can, Permission } from '../../auth/permissions'
import { formatDateTime, orDash } from '../../components/format'
import { PaginatedTable, type Column } from '../../components/PaginatedTable'
import { useListParams } from '../../components/useListParams'
import { serverQuery, useServerFacets, useServers, type Server } from './api'
import { ADMIN_STATUSES, adminStatus } from './labels'

const COLUMNS: Column<Server>[] = [
  {
    key: 'name',
    header: 'Nom',
    sortKey: 'name',
    render: (server) => (
      <Anchor component={Link} to={`/servers/${server.id}`}>
        {server.name}
      </Anchor>
    ),
  },
  {
    key: 'adminStatus',
    header: 'Statut',
    sortKey: 'adminStatus',
    render: (server) => {
      const status = adminStatus(server.adminStatus)
      return <Badge color={status.color} variant="light">{status.label}</Badge>
    },
  },
  { key: 'hostname', header: 'Hostname', render: (server) => orDash(server.hostname) },
  { key: 'publicIp', header: 'IP publique', render: (server) => orDash(server.publicIp) },
  { key: 'provider', header: 'Fournisseur', sortKey: 'provider', render: (server) => orDash(server.provider) },
  { key: 'region', header: 'Région', sortKey: 'region', render: (server) => orDash(server.region) },
  {
    key: 'tags',
    header: 'Tags',
    render: (server) => (
      <Group gap={4}>
        {(server.tags ?? []).map((tag) => (
          <Badge key={tag} variant="outline" size="sm">
            {tag}
          </Badge>
        ))}
      </Group>
    ),
  },
  { key: 'createdAt', header: 'Créé le', sortKey: 'createdAt', render: (server) => formatDateTime(server.createdAt) },
]

/** Liste des VPS (spec §12.3) : filtres, tri et page dans l'URL. */
export function ServersListPage() {
  const { user } = useAuth()
  const { params, setParams } = useListParams({ sort: 'name,asc' })
  const servers = useServers(serverQuery(params))
  const facets = useServerFacets()
  const [search, setSearch] = useState(params.filters.q ?? '')
  const applySearch = useDebouncedCallback((value: string) => setParams({ filters: { q: value.trim() } }), 300)

  const filter = (name: string) => ({
    value: params.filters[name] ?? null,
    onChange: (value: string | null) => setParams({ filters: { [name]: value } }),
    clearable: true,
    searchable: true,
  })

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>VPS</Title>
        {can(user, Permission.SERVER_WRITE) && (
          <Button component={Link} to="/servers/new">
            Nouveau VPS
          </Button>
        )}
      </Group>
      <Group align="flex-end" gap="sm">
        <TextInput
          label="Rechercher"
          placeholder="Nom, hostname ou IP"
          value={search}
          onChange={(event) => {
            setSearch(event.currentTarget.value)
            applySearch(event.currentTarget.value)
          }}
          w={240}
        />
        <Select label="Statut" data={ADMIN_STATUSES.map(({ value, label }) => ({ value, label }))} w={180} {...filter('adminStatus')} />
        <Select label="Fournisseur" data={facets.data?.providers ?? []} w={180} {...filter('provider')} />
        <Select label="Région" data={facets.data?.regions ?? []} w={160} {...filter('region')} />
        <Select label="Tag" data={facets.data?.tags ?? []} w={160} {...filter('tag')} />
      </Group>
      <PaginatedTable
        label="VPS"
        columns={COLUMNS}
        data={servers.data}
        rowKey={(server) => server.id ?? ''}
        params={params}
        setParams={setParams}
        loading={servers.isPending}
        error={servers.isError}
        emptyMessage="Aucun VPS ne correspond à ces critères."
      />
    </Stack>
  )
}
