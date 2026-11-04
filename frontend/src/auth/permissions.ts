import type { Me } from './session'

/** Permissions de la matrice fixe (spec §8.12), telles que le claim `perms` et `GET /me` les portent. */
export const Permission = {
  SERVER_READ: 'server:read',
  SERVER_WRITE: 'server:write',
  SERVER_DELETE: 'server:delete',
  PROJECT_READ: 'project:read',
  PROJECT_WRITE: 'project:write',
  PROJECT_DELETE: 'project:delete',
  USER_READ: 'user:read',
  USER_MANAGE: 'user:manage',
  AUDIT_READ: 'audit:read',
} as const

export type Permission = (typeof Permission)[keyof typeof Permission]

/** Confort d'interface seulement (spec §12.4) : masquer ce que l'API refuserait. L'API reste l'autorité. */
export function can(user: Me | null, permission: Permission): boolean {
  return user?.permissions.includes(permission) ?? false
}

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: 'Super administrateur',
  INFRA_ADMIN: 'Administrateur infrastructure',
  DEVOPS: 'DevOps',
  DEVELOPER: 'Développeur',
  VIEWER: 'Lecteur',
}

export function roleLabel(role: string): string {
  return ROLE_LABELS[role] ?? role
}
