import { Alert } from '@mantine/core'
import { ApiError } from '../api/problem'

/** Échec d'un chargement : le `detail` de l'API (« VPS introuvable. »…), ou un message générique. */
export function QueryError({ error }: { error: unknown }) {
  const message = error instanceof ApiError
    ? (error.problem.detail ?? 'Chargement impossible.')
    : 'Le serveur est injoignable. Vérifiez votre connexion.'
  return <Alert color="red">{message}</Alert>
}
