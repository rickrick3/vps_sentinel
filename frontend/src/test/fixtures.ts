import { HttpResponse } from 'msw'
import type { components } from '../api/schema'
import type { Problem } from '../api/problem'
import type { Me } from '../auth/session'

type AuthResponse = components['schemas']['AuthResponse']
type Role = 'SUPER_ADMIN' | 'INFRA_ADMIN' | 'DEVOPS' | 'DEVELOPER' | 'VIEWER'

/** Matrice fixe des permissions (spec §8.12). */
export const ROLE_PERMISSIONS: Record<Role, string[]> = {
  SUPER_ADMIN: [
    'server:read', 'server:write', 'server:delete', 'project:read', 'project:write', 'project:delete',
    'user:read', 'user:manage', 'audit:read',
  ],
  INFRA_ADMIN: [
    'server:read', 'server:write', 'server:delete', 'project:read', 'project:write', 'project:delete',
    'user:read', 'audit:read',
  ],
  DEVOPS: ['server:read', 'server:write', 'project:read', 'project:write', 'project:delete'],
  DEVELOPER: ['server:read', 'project:read', 'project:write'],
  VIEWER: ['server:read', 'project:read'],
}

export function me(overrides: Partial<Me> = {}): Me {
  return {
    id: '0190a6e8-cb7b-75e8-8497-96e25b32ca4c',
    email: 'alice@exemple.fr',
    fullName: 'Alice Martin',
    role: 'VIEWER',
    permissions: ROLE_PERMISSIONS.VIEWER,
    mustChangePassword: false,
    ...overrides,
  }
}

export function withRole(role: Role, overrides: Partial<Me> = {}): Me {
  return me({ role, permissions: ROLE_PERMISSIONS[role], ...overrides })
}

export function authResponse(accessToken: string, user: Partial<Me> = {}): AuthResponse {
  return { accessToken, tokenType: 'Bearer', expiresIn: 900, user: me(user) }
}

/** Réponse Problem Details telle que l'API la produit (spec §11). */
export function problem(status: number, code: string, extra: Partial<Problem> = {}, headers: Record<string, string> = {}) {
  return HttpResponse.json(
    { type: `urn:sentinel:problem:${code}`, title: 'Erreur', status, detail: 'Erreur.', ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json', ...headers } },
  )
}
