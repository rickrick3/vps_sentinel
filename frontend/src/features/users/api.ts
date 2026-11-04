import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { components } from '../../api/schema'
import { unwrap } from '../../api/unwrap'
import { useApi } from '../../auth/AuthProvider'
import type { ListParams } from '../../components/useListParams'

type Schemas = components['schemas']
export type User = Schemas['UserResponse']
export type CreateUserPayload = Schemas['CreateUserRequest']
export type UserChanges = Schemas['PatchUserRequest']

export type UserQuery = { page: number; size: number; sort: string; q?: string; role?: string; status?: string }

export const userKeys = {
  all: ['users'] as const,
  list: (query: UserQuery) => ['users', 'list', query] as const,
}

export function userQuery(params: ListParams): UserQuery {
  const { q, role, status } = params.filters
  return { page: params.page, size: params.size, sort: params.sort, q, role, status }
}

export function useUsers(query: UserQuery) {
  const api = useApi()
  return useQuery({
    queryKey: userKeys.list(query),
    queryFn: () => unwrap(api.GET('/api/v1/users', { params: { query } })),
    placeholderData: keepPreviousData,
  })
}

// Les mutations qui renvoient un mot de passe temporaire sont retirées du cache dès que plus personne ne les observe
// (`gcTime: 0`) : après `reset()`, le mot de passe ne subsiste nulle part (spec §12.4).
const FORGET_RESULT = { gcTime: 0 }

export function useCreateUser() {
  const api = useApi()
  const queryClient = useQueryClient()
  return useMutation({
    ...FORGET_RESULT,
    mutationFn: (body: CreateUserPayload) => unwrap(api.POST('/api/v1/users', { body })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: userKeys.all }),
  })
}

/** Modification partielle : seuls les champs modifiés, plus la version lue (spec §10.2). */
export function usePatchUser(id: string) {
  const api = useApi()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (changes: UserChanges) => unwrap(api.PATCH('/api/v1/users/{id}', { params: { path: { id } }, body: changes })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: userKeys.all }),
  })
}

export function useResetPassword() {
  const api = useApi()
  const queryClient = useQueryClient()
  return useMutation({
    ...FORGET_RESULT,
    mutationFn: (id: string) => unwrap(api.POST('/api/v1/users/{id}/reset-password', { params: { path: { id } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: userKeys.all }),
  })
}

export function useDeleteUser() {
  const api = useApi()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => unwrap(api.DELETE('/api/v1/users/{id}', { params: { path: { id } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: userKeys.all }),
  })
}
