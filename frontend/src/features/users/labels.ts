import { roleLabel } from '../../auth/permissions'

export const ROLE_OPTIONS = ['SUPER_ADMIN', 'INFRA_ADMIN', 'DEVOPS', 'DEVELOPER', 'VIEWER'].map((role) => ({
  value: role,
  label: roleLabel(role),
}))

export const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Actif' },
  { value: 'DISABLED', label: 'Désactivé' },
]

export function statusLabel(value: string | null | undefined): string {
  return STATUS_OPTIONS.find((status) => status.value === value)?.label ?? value ?? '—'
}
