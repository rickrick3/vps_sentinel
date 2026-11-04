import { keepPreviousData, useQuery } from '@tanstack/react-query'
import type { components } from '../../api/schema'
import { unwrap } from '../../api/unwrap'
import { useApi } from '../../auth/AuthProvider'
import type { ListParams } from '../../components/useListParams'

export type AuditEntry = components['schemas']['AuditLogResponse']

export type AuditQuery = {
  page: number
  size: number
  sort: string
  actorUserId?: string
  action?: string
  targetType?: string
  targetId?: string
  from?: string
  to?: string
}

export function auditQuery(params: ListParams): AuditQuery {
  const { actorUserId, action, targetType, targetId, from, to } = params.filters
  return { page: params.page, size: params.size, sort: params.sort, actorUserId, action, targetType, targetId, from, to }
}

/** Journal d'audit ; `enabled` à faux sans `audit:read` : aucune requête n'est émise (spec §12.3). */
export function useAuditLogs(query: AuditQuery, enabled = true) {
  const api = useApi()
  return useQuery({
    queryKey: ['audit-logs', query],
    queryFn: () => unwrap(api.GET('/api/v1/audit-logs', { params: { query } })),
    placeholderData: keepPreviousData,
    enabled,
  })
}
