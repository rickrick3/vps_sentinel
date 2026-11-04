import { Button, Code, Group, Modal, Select, SimpleGrid, Stack, Text, TextInput, Title } from '@mantine/core'
import { useDebouncedCallback } from '@mantine/hooks'
import { useState } from 'react'
import { formatDateTime, orDash } from '../../components/format'
import { PaginatedTable, type Column } from '../../components/PaginatedTable'
import { useListParams } from '../../components/useListParams'
import { auditQuery, useAuditLogs, type AuditEntry } from './api'
import { ACTION_OPTIONS, actionLabel, actorLabel, TARGET_OPTIONS, targetLabel } from './labels'

/** Valeur d'un champ datetime-local (heure locale) pour un instant ISO-8601, et inversement. */
function toLocalInput(iso: string | undefined): string {
  if (!iso) {
    return ''
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function toInstant(local: string): string | null {
  const date = new Date(local)
  return local === '' || Number.isNaN(date.getTime()) ? null : date.toISOString()
}

function DetailsModal({ entry, onClose }: { entry: AuditEntry; onClose: () => void }) {
  return (
    <Modal opened onClose={onClose} title={actionLabel(entry.action)} size="lg">
      <Stack gap="sm">
        <SimpleGrid cols={2} spacing="xs">
          <Text size="sm" c="dimmed">Date</Text>
          <Text size="sm">{formatDateTime(entry.occurredAt, { seconds: true })}</Text>
          <Text size="sm" c="dimmed">Acteur</Text>
          <Text size="sm">{actorLabel(entry)}</Text>
          <Text size="sm" c="dimmed">Cible</Text>
          <Text size="sm">{targetLabel(entry.targetType)} {orDash(entry.targetId)}</Text>
          <Text size="sm" c="dimmed">IP</Text>
          <Text size="sm">{orDash(entry.ip)}</Text>
          <Text size="sm" c="dimmed">Identifiant de requête</Text>
          <Text size="sm">{orDash(entry.correlationId)}</Text>
        </SimpleGrid>
        {/* Détails affichés comme du texte JSON : aucune valeur n'est interprétée comme du HTML (spec §12.4). */}
        <Code block>{JSON.stringify(entry.details ?? {}, null, 2)}</Code>
      </Stack>
    </Modal>
  )
}

/** Journal d'audit filtrable (spec §12.3), du plus récent au plus ancien par défaut. */
export function AuditPage() {
  const { params, setParams } = useListParams({ sort: 'occurredAt,desc' })
  const logs = useAuditLogs(auditQuery(params))
  const [selected, setSelected] = useState<AuditEntry | null>(null)
  const [targetId, setTargetId] = useState(params.filters.targetId ?? '')
  const [actorUserId, setActorUserId] = useState(params.filters.actorUserId ?? '')
  const applyText = useDebouncedCallback((name: string, value: string) => setParams({ filters: { [name]: value.trim() } }), 300)

  const columns: Column<AuditEntry>[] = [
    { key: 'occurredAt', header: 'Date', sortKey: 'occurredAt', render: (entry) => formatDateTime(entry.occurredAt, { seconds: true }) },
    { key: 'actor', header: 'Acteur', render: actorLabel },
    { key: 'action', header: 'Action', render: (entry) => actionLabel(entry.action) },
    {
      key: 'target',
      header: 'Cible',
      render: (entry) => (entry.targetType ? `${targetLabel(entry.targetType)} ${entry.targetId ?? ''}` : '—'),
    },
    { key: 'ip', header: 'IP', render: (entry) => orDash(entry.ip) },
    {
      key: 'details',
      header: '',
      render: (entry) => (
        <Button size="xs" variant="subtle" onClick={() => setSelected(entry)}>
          Détails
        </Button>
      ),
    },
  ]

  return (
    <Stack>
      <Title order={2}>Journal d'audit</Title>
      <Group align="flex-end" gap="sm">
        <Select
          label="Action"
          data={ACTION_OPTIONS}
          searchable
          clearable
          value={params.filters.action ?? null}
          onChange={(action) => setParams({ filters: { action } })}
          w={240}
        />
        <Select
          label="Cible"
          data={TARGET_OPTIONS}
          clearable
          value={params.filters.targetType ?? null}
          onChange={(targetType) => setParams({ filters: { targetType } })}
          w={170}
        />
        <TextInput
          label="Identifiant de la cible"
          value={targetId}
          onChange={(event) => {
            setTargetId(event.currentTarget.value)
            applyText('targetId', event.currentTarget.value)
          }}
          w={280}
        />
        <TextInput
          label="Identifiant de l'acteur"
          value={actorUserId}
          onChange={(event) => {
            setActorUserId(event.currentTarget.value)
            applyText('actorUserId', event.currentTarget.value)
          }}
          w={280}
        />
        <TextInput
          label="Du"
          type="datetime-local"
          value={toLocalInput(params.filters.from)}
          onChange={(event) => setParams({ filters: { from: toInstant(event.currentTarget.value) } })}
        />
        <TextInput
          label="Au"
          description="Exclu"
          type="datetime-local"
          value={toLocalInput(params.filters.to)}
          onChange={(event) => setParams({ filters: { to: toInstant(event.currentTarget.value) } })}
        />
      </Group>
      <PaginatedTable
        label="Journal d'audit"
        columns={columns}
        data={logs.data}
        rowKey={(entry) => entry.id ?? ''}
        params={params}
        setParams={setParams}
        loading={logs.isPending}
        error={logs.isError}
        emptyMessage="Aucune action ne correspond à ces critères."
      />
      {selected !== null && <DetailsModal entry={selected} onClose={() => setSelected(null)} />}
    </Stack>
  )
}
