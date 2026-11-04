import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { components } from '../../api/schema'
import { unwrap } from '../../api/unwrap'
import { useApi } from '../../auth/AuthProvider'
import type { ListParams } from '../../components/useListParams'

type Schemas = components['schemas']
export type Server = Schemas['ServerResponse']
export type HostedEnvironment = Schemas['HostedEnvironmentResponse']
type ServerRequest = Schemas['ServerRequest']

/**
 * Corps complet d'un VPS : chaque champ figure, `null` s'il est vide. En `PUT`, un champ absent ou `null` est vidé
 * (spec §10.2) ; le contrat généré ne déclare pas `null`, d'où la conversion à l'envoi.
 */
export type ServerPayload = { [K in Exclude<keyof ServerRequest, 'version'>]-?: ServerRequest[K] | null }

export type ServerQuery = {
  page: number
  size: number
  sort: string
  q?: string
  tag?: string
  provider?: string
  region?: string
  adminStatus?: string
}

export const serverKeys = {
  all: ['servers'] as const,
  lists: ['servers', 'list'] as const,
  list: (query: ServerQuery) => ['servers', 'list', query] as const,
  detail: (id: string) => ['servers', 'detail', id] as const,
  deployments: (id: string) => ['servers', 'detail', id, 'deployments'] as const,
  facets: ['servers', 'facets'] as const,
}

export function serverQuery(params: ListParams): ServerQuery {
  const { q, tag, provider, region, adminStatus } = params.filters
  return { page: params.page, size: params.size, sort: params.sort, q, tag, provider, region, adminStatus }
}

export function useServers(query: ServerQuery, enabled = true) {
  const api = useApi()
  return useQuery({
    queryKey: serverKeys.list(query),
    queryFn: () => unwrap(api.GET('/api/v1/servers', { params: { query } })),
    placeholderData: keepPreviousData,
    enabled,
  })
}

export function useServer(id: string) {
  const api = useApi()
  return useQuery({
    queryKey: serverKeys.detail(id),
    queryFn: () => unwrap(api.GET('/api/v1/servers/{id}', { params: { path: { id } } })),
  })
}

/** Environnements hébergés par le VPS ; demande `project:read`. */
export function useServerDeployments(id: string, enabled: boolean) {
  const api = useApi()
  return useQuery({
    queryKey: serverKeys.deployments(id),
    queryFn: () => unwrap(api.GET('/api/v1/servers/{serverId}/deployments', { params: { path: { serverId: id } } })),
    enabled,
  })
}

/** Fournisseurs, régions et tags existants, pour l'auto-complétion et les filtres. */
export function useServerFacets() {
  const api = useApi()
  return useQuery({
    queryKey: serverKeys.facets,
    queryFn: () => unwrap(api.GET('/api/v1/servers/facets')),
    staleTime: 60_000,
  })
}

export function useCreateServer() {
  const api = useApi()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: ServerPayload) =>
      unwrap(api.POST('/api/v1/servers', { body: payload as ServerRequest })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: serverKeys.all }),
  })
}

/** Remplacement complet (spec §10.2) : tous les champs, `null` pour un champ vidé, et la version lue. */
export function useUpdateServer(id: string) {
  const api = useApi()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ payload, version }: { payload: ServerPayload; version: number }) =>
      unwrap(api.PUT('/api/v1/servers/{id}', { params: { path: { id } }, body: { ...payload, version } as ServerRequest })),
    onSuccess: (server) => {
      queryClient.setQueryData(serverKeys.detail(id), server)
      return queryClient.invalidateQueries({ queryKey: serverKeys.lists })
    },
  })
}

export function useDeleteServer() {
  const api = useApi()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => unwrap(api.DELETE('/api/v1/servers/{id}', { params: { path: { id } } })),
    onSuccess: (_result, id) => {
      queryClient.removeQueries({ queryKey: serverKeys.detail(id) })
      return queryClient.invalidateQueries({ queryKey: serverKeys.lists })
    },
  })
}
