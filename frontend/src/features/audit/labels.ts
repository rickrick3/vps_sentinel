import type { AuditEntry } from './api'

/** Actions auditées (spec §9.5). */
export const AUDIT_ACTIONS: Record<string, string> = {
  LOGIN_SUCCEEDED: 'Connexion réussie',
  LOGIN_FAILED: 'Échec de connexion',
  LOGIN_THROTTLED: 'Connexion bloquée',
  LOGOUT: 'Déconnexion',
  TOKEN_REUSE_DETECTED: 'Réutilisation de jeton détectée',
  PASSWORD_CHANGED: 'Mot de passe changé',
  PASSWORD_CHANGE_FAILED: 'Échec du changement de mot de passe',
  USER_CREATED: 'Utilisateur créé',
  USER_UPDATED: 'Utilisateur modifié',
  USER_ROLE_CHANGED: 'Rôle modifié',
  USER_DISABLED: 'Utilisateur désactivé',
  USER_ENABLED: 'Utilisateur réactivé',
  USER_DELETED: 'Utilisateur supprimé',
  USER_PASSWORD_RESET: 'Mot de passe réinitialisé',
  SERVER_CREATED: 'VPS créé',
  SERVER_UPDATED: 'VPS modifié',
  SERVER_DELETED: 'VPS supprimé',
  PROJECT_CREATED: 'Projet créé',
  PROJECT_UPDATED: 'Projet modifié',
  PROJECT_DELETED: 'Projet supprimé',
  ENVIRONMENT_CREATED: 'Environnement créé',
  ENVIRONMENT_UPDATED: 'Environnement modifié',
  ENVIRONMENT_DELETED: 'Environnement supprimé',
  DOMAIN_ADDED: 'Domaine ajouté',
  DOMAIN_REMOVED: 'Domaine retiré',
  DEPLOYMENT_ADDED: 'VPS rattaché',
  DEPLOYMENT_REMOVED: 'VPS détaché',
}

export const TARGET_TYPES: Record<string, string> = {
  USER: 'Utilisateur',
  SERVER: 'VPS',
  PROJECT: 'Projet',
  ENVIRONMENT: 'Environnement',
  DOMAIN: 'Domaine',
  DEPLOYMENT: 'Déploiement',
}

export const ACTION_OPTIONS = Object.entries(AUDIT_ACTIONS).map(([value, label]) => ({ value, label }))
export const TARGET_OPTIONS = Object.entries(TARGET_TYPES).map(([value, label]) => ({ value, label }))

export function actionLabel(action: string | null | undefined): string {
  return (action && AUDIT_ACTIONS[action]) ?? action ?? '—'
}

export function targetLabel(targetType: string | null | undefined): string {
  return (targetType && TARGET_TYPES[targetType]) ?? targetType ?? '—'
}

/** Email de l'utilisateur, ou nature de l'acteur quand il n'y en a pas. */
export function actorLabel(entry: AuditEntry): string {
  if (entry.actorEmail) {
    return entry.actorEmail
  }
  return entry.actorType === 'SYSTEM' ? 'Système' : entry.actorType === 'ANONYMOUS' ? 'Anonyme' : '—'
}
