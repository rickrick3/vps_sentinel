import { http, HttpResponse } from 'msw'
import type { Problem } from '../api/problem'
import type { components } from '../api/schema'
import type { Me } from '../auth/session'
import { server } from './server'

type Schemas = components['schemas']
type AuthResponse = Schemas['AuthResponse']
export type Role = 'SUPER_ADMIN' | 'INFRA_ADMIN' | 'DEVOPS' | 'DEVELOPER' | 'VIEWER'

/** Réponse telle que l'API l'envoie : un champ vide vaut `null` (le contrat généré ne le dit pas). */
type Wire<T> = { [K in keyof T]-?: T[K] | null }

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

/** Session restaurée au chargement pour un utilisateur de ce rôle. */
export function signInAs(role: Role, overrides: Partial<Me> = {}) {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse('t', withRole(role, overrides)))))
}

/** Réponse Problem Details telle que l'API la produit (spec §11). */
export function problem(status: number, code: string, extra: Partial<Problem> = {}, headers: Record<string, string> = {}) {
  return HttpResponse.json(
    { type: `urn:sentinel:problem:${code}`, title: 'Erreur', status, detail: 'Erreur.', ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json', ...headers } },
  )
}

/** Page de résultats (spec §10.2). */
export function page<T>(items: T[], { page = 0, size = 20, totalItems = items.length } = {}) {
  return { items, page, size, totalItems, totalPages: Math.max(1, Math.ceil(totalItems / size)) }
}

export function serverResponse(overrides: Partial<Wire<Schemas['ServerResponse']>> = {}): Wire<Schemas['ServerResponse']> {
  return {
    id: 'srv-1',
    name: 'VPS-01',
    hostname: 'vps-01.exemple.fr',
    publicIp: '203.0.113.10',
    privateIp: null,
    provider: 'OVH',
    region: 'GRA',
    osFamily: 'UBUNTU',
    osVersion: '24.04',
    cpuCores: 4,
    ramMb: 8192,
    diskGb: 160,
    adminStatus: 'ACTIVE',
    description: null,
    tags: ['web'],
    createdAt: '2026-11-02T10:30:00Z',
    updatedAt: '2026-11-03T08:00:00Z',
    version: 3,
    ...overrides,
  }
}

export function environmentDetail(
  overrides: Partial<Wire<Schemas['EnvironmentDetail']>> = {},
): Wire<Schemas['EnvironmentDetail']> {
  return {
    id: 'env-prod',
    name: 'prod',
    kind: 'PRODUCTION',
    domains: [],
    deployments: [],
    createdAt: '2026-11-02T10:30:00Z',
    updatedAt: '2026-11-02T10:30:00Z',
    version: 0,
    ...overrides,
  }
}

type ProjectDetailWire = Omit<Wire<Schemas['ProjectDetailResponse']>, 'environments'> & {
  environments: Wire<Schemas['EnvironmentDetail']>[]
}

export function projectDetail(overrides: Partial<ProjectDetailWire> = {}): ProjectDetailWire {
  return {
    id: 'prj-1',
    name: 'Demo',
    description: null,
    repositoryUrl: 'https://github.com/exemple/demo',
    environments: [environmentDetail()],
    createdAt: '2026-11-02T10:30:00Z',
    updatedAt: '2026-11-02T10:30:00Z',
    version: 2,
    ...overrides,
  }
}

export function userResponse(overrides: Partial<Wire<Schemas['UserResponse']>> = {}): Wire<Schemas['UserResponse']> {
  return {
    id: 'usr-bob',
    email: 'bob@exemple.fr',
    fullName: 'Bob Durand',
    role: 'DEVELOPER',
    status: 'ACTIVE',
    mustChangePassword: false,
    passwordExpiresAt: null,
    lastLoginAt: '2026-11-03T08:00:00Z',
    createdAt: '2026-11-02T10:30:00Z',
    updatedAt: '2026-11-02T10:30:00Z',
    version: 3,
    ...overrides,
  }
}

export function auditLog(overrides: Partial<Wire<Schemas['AuditLogResponse']>> = {}): Wire<Schemas['AuditLogResponse']> {
  return {
    id: 'log-1',
    occurredAt: '2026-11-03T08:00:00Z',
    actorType: 'USER',
    actorUserId: '0190a6e8-cb7b-75e8-8497-96e25b32ca4c',
    actorEmail: 'alice@exemple.fr',
    action: 'SERVER_CREATED',
    targetType: 'SERVER',
    targetId: 'srv-1',
    details: { name: 'VPS-01' },
    ip: '203.0.113.7',
    correlationId: 'req-1',
    ...overrides,
  }
}
