import { Anchor, Group, Loader, Paper, SimpleGrid, Stack, Table, Text, Title } from '@mantine/core'
import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthProvider'
import { can, Permission } from '../../auth/permissions'
import { formatDateTime } from '../../components/format'
import { useAuditLogs } from '../audit/api'
import { actionLabel, actorLabel, targetLabel } from '../audit/labels'
import { useProjects } from '../projects/api'
import { useServers } from '../servers/api'

type Counter = { data?: { totalItems?: number }; isPending: boolean; isError: boolean }

function Stat({ label, counter, to }: { label: string; counter: Counter; to: string }) {
  return (
    <Paper component="section" aria-label={label} withBorder p="md">
      <Text size="sm" c="dimmed">
        {label}
      </Text>
      <Group justify="space-between" align="flex-end">
        <Title order={3} component="p">
          {counter.isError ? '—' : counter.isPending ? <Loader size="xs" aria-label="Chargement" /> : (counter.data?.totalItems ?? 0)}
        </Title>
        <Anchor component={Link} to={to} size="sm">
          Voir
        </Anchor>
      </Group>
    </Paper>
  )
}

// Une ligne suffit : seul `totalItems` est lu (spec §12.3).
const COUNT = { page: 0, size: 1, sort: 'name,asc' }

/** Tableau de bord (spec §12.3) : inventaire, et dernières actions pour les rôles qui ont `audit:read`. */
export function DashboardPage() {
  const { user } = useAuth()
  const canReadServers = can(user, Permission.SERVER_READ)
  const canReadAudit = can(user, Permission.AUDIT_READ)
  const active = useServers({ ...COUNT, adminStatus: 'ACTIVE' }, canReadServers)
  const maintenance = useServers({ ...COUNT, adminStatus: 'MAINTENANCE' }, canReadServers)
  const decommissioned = useServers({ ...COUNT, adminStatus: 'DECOMMISSIONED' }, canReadServers)
  const projects = useProjects(COUNT, can(user, Permission.PROJECT_READ))
  const recent = useAuditLogs({ page: 0, size: 10, sort: 'occurredAt,desc' }, canReadAudit)

  return (
    <Stack gap="lg">
      <Title order={2}>Tableau de bord</Title>
      <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }}>
        <Stat label="VPS actifs" counter={active} to="/servers?adminStatus=ACTIVE" />
        <Stat label="VPS en maintenance" counter={maintenance} to="/servers?adminStatus=MAINTENANCE" />
        <Stat label="VPS décommissionnés" counter={decommissioned} to="/servers?adminStatus=DECOMMISSIONED" />
        <Stat label="Projets" counter={projects} to="/projects" />
      </SimpleGrid>
      {canReadAudit && (
        <Stack gap="xs">
          <Group justify="space-between">
            <Title order={3}>Dernières actions</Title>
            <Anchor component={Link} to="/audit" size="sm">
              Voir le journal
            </Anchor>
          </Group>
          {recent.isPending && <Loader size="sm" aria-label="Chargement" />}
          {recent.isError && <Text c="dimmed">Impossible de charger les dernières actions.</Text>}
          {recent.data?.items?.length === 0 && <Text c="dimmed">Aucune action enregistrée.</Text>}
          {recent.data?.items !== undefined && recent.data.items.length > 0 && (
            <Table aria-label="Dernières actions" striped>
              <Table.Tbody>
                {recent.data.items.map((entry) => (
                  <Table.Tr key={entry.id}>
                    <Table.Td>{formatDateTime(entry.occurredAt)}</Table.Td>
                    <Table.Td>{actorLabel(entry)}</Table.Td>
                    <Table.Td>{actionLabel(entry.action)}</Table.Td>
                    <Table.Td>{entry.targetType ? targetLabel(entry.targetType) : '—'}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          )}
        </Stack>
      )}
    </Stack>
  )
}
