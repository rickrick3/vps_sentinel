import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { components } from '../../api/schema'
import { unwrap } from '../../api/unwrap'
import { useApi } from '../../auth/AuthProvider'
import type { ListParams } from '../../components/useListParams'

type Schemas = components['schemas']
export type ProjectSummary = Schemas['ProjectResponse']
export type Project = Schemas['ProjectDetailResponse']
export type Environment = Schemas['EnvironmentDetail']
export type Domain = Schemas['DomainResponse']
export type Deployment = Schemas['DeploymentResponse']
type ProjectRequest = Schemas['ProjectRequest']

/** Corps complet d'un projet ; `null` vide le champ en `PUT` (spec §10.2), ce que le contrat généré ne déclare pas. */
export type ProjectPayload = { name: string; description: string | null; repositoryUrl: string | null }
export type EnvironmentPayload = { name: string; kind: string }

export type ProjectQuery = { page: number; size: number; sort: string; q?: string }

export const projectKeys = {
  all: ['projects'] as const,
  list: (query: ProjectQuery) => ['projects', 'list', query] as const,
  detail: (id: string) => ['projects', 'detail', id] as const,
}

export function projectQuery(params: ListParams): ProjectQuery {
  return { page: params.page, size: params.size, sort: params.sort, q: params.filters.q }
}

export function useProjects(query: ProjectQuery, enabled = true) {
  const api = useApi()
  return useQuery({
    queryKey: projectKeys.list(query),
    queryFn: () => unwrap(api.GET('/api/v1/projects', { params: { query } })),
    placeholderData: keepPreviousData,
    enabled,
  })
}

export function useProject(id: string) {
  const api = useApi()
  return useQuery({
    queryKey: projectKeys.detail(id),
    queryFn: () => unwrap(api.GET('/api/v1/projects/{id}', { params: { path: { id } } })),
  })
}

/** Mutation qui modifie un projet ou ses enfants : toutes les données des projets sont rechargées (nombres d'environnements compris). */
function useProjectMutation<Variables, Result>(mutationFn: (variables: Variables) => Promise<Result>, { touchesServers = false } = {}) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: projectKeys.all })
      if (touchesServers) {
        // Environnements hébergés de la fiche VPS.
        await queryClient.invalidateQueries({ queryKey: ['servers', 'detail'] })
      }
    },
  })
}

export function useCreateProject() {
  const api = useApi()
  return useProjectMutation((payload: ProjectPayload) =>
    unwrap(api.POST('/api/v1/projects', { body: payload as ProjectRequest })))
}

export function useUpdateProject(id: string) {
  const api = useApi()
  return useProjectMutation(({ payload, version }: { payload: ProjectPayload; version: number }) =>
    unwrap(api.PUT('/api/v1/projects/{id}', { params: { path: { id } }, body: { ...payload, version } as ProjectRequest })))
}

export function useDeleteProject() {
  const api = useApi()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => unwrap(api.DELETE('/api/v1/projects/{id}', { params: { path: { id } } })),
    onSuccess: (_result, id) => {
      queryClient.removeQueries({ queryKey: projectKeys.detail(id) })
      return queryClient.invalidateQueries({ queryKey: projectKeys.all })
    },
  })
}

export function useCreateEnvironment(projectId: string) {
  const api = useApi()
  return useProjectMutation((body: EnvironmentPayload) =>
    unwrap(api.POST('/api/v1/projects/{projectId}/environments', { params: { path: { projectId } }, body })))
}

export function useUpdateEnvironment(id: string) {
  const api = useApi()
  return useProjectMutation(({ payload, version }: { payload: EnvironmentPayload; version: number }) =>
    unwrap(api.PUT('/api/v1/environments/{id}', { params: { path: { id } }, body: { ...payload, version } })))
}

export function useDeleteEnvironment() {
  const api = useApi()
  return useProjectMutation(
    (id: string) => unwrap(api.DELETE('/api/v1/environments/{id}', { params: { path: { id } } })),
    { touchesServers: true },
  )
}

export function useAddDomain(environmentId: string) {
  const api = useApi()
  return useProjectMutation((body: { hostname: string; isPrimary: boolean }) =>
    unwrap(api.POST('/api/v1/environments/{environmentId}/domains', { params: { path: { environmentId } }, body })))
}

export function useRemoveDomain() {
  const api = useApi()
  return useProjectMutation((id: string) => unwrap(api.DELETE('/api/v1/domains/{id}', { params: { path: { id } } })))
}

export function useAttachServer(environmentId: string) {
  const api = useApi()
  return useProjectMutation(
    (body: { serverId: string; role?: string }) =>
      unwrap(api.POST('/api/v1/environments/{environmentId}/deployments', { params: { path: { environmentId } }, body })),
    { touchesServers: true },
  )
}

export function useDetachDeployment() {
  const api = useApi()
  return useProjectMutation(
    (id: string) => unwrap(api.DELETE('/api/v1/deployments/{id}', { params: { path: { id } } })),
    { touchesServers: true },
  )
}
