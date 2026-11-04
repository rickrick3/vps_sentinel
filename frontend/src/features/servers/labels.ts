/** Statuts administratifs (spec §9.3), dans l'ordre d'affichage. */
export const ADMIN_STATUSES = [
  { value: 'ACTIVE', label: 'Actif', color: 'green' },
  { value: 'MAINTENANCE', label: 'En maintenance', color: 'yellow' },
  { value: 'DECOMMISSIONED', label: 'Décommissionné', color: 'gray' },
] as const

export const OS_FAMILIES = [
  { value: 'UBUNTU', label: 'Ubuntu' },
  { value: 'DEBIAN', label: 'Debian' },
  { value: 'RHEL', label: 'Red Hat Enterprise Linux' },
  { value: 'ROCKY', label: 'Rocky Linux' },
  { value: 'ALMALINUX', label: 'AlmaLinux' },
  { value: 'CENTOS', label: 'CentOS' },
  { value: 'OTHER', label: 'Autre' },
] as const

export function adminStatus(value: string | null | undefined) {
  return ADMIN_STATUSES.find((status) => status.value === value) ?? { value, label: value ?? '—', color: 'gray' }
}

export function osFamilyLabel(value: string | null | undefined): string | null {
  return OS_FAMILIES.find((family) => family.value === value)?.label ?? value ?? null
}
