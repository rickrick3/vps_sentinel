import { notifications } from '@mantine/notifications'
import type { ReactNode } from 'react'
import { ApiError, fieldErrors, problemCode, type Problem } from './problem'

export const VERSION_CONFLICT_MESSAGE = "Cet élément a été modifié par quelqu'un d'autre. Rechargez la page."
export const SERVER_IN_USE_MESSAGE = 'VPS utilisé par des déploiements'

type FormWithErrors = { setErrors: (errors: Record<string, ReactNode>) => void }

function notifyError(message: string) {
  notifications.show({ color: 'red', title: 'Action impossible', message })
}

/**
 * Affichage d'une erreur de l'API (spec §12.4) : les erreurs de champ vont sur le formulaire ; un conflit de version et
 * un VPS utilisé ont leur message ; toute autre erreur est notifiée avec son `detail`.
 */
export function applyProblem(problem: Problem, form?: FormWithErrors): void {
  const code = problemCode(problem)
  if (code === 'version-conflict') {
    notifyError(VERSION_CONFLICT_MESSAGE)
    return
  }
  if (code === 'server-in-use') {
    notifyError(SERVER_IN_USE_MESSAGE)
    return
  }
  const errors = fieldErrors(problem)
  if (form !== undefined && Object.keys(errors).length > 0) {
    form.setErrors(errors)
    return
  }
  notifyError(problem.detail ?? 'Une erreur est survenue.')
}

/** Comme {@link applyProblem}, pour l'erreur d'une requête ou d'une mutation : réseau compris. */
export function applyError(error: unknown, form?: FormWithErrors): void {
  if (error instanceof ApiError) {
    applyProblem(error.problem, form)
  } else {
    notifyError('Le serveur est injoignable. Vérifiez votre connexion.')
  }
}
