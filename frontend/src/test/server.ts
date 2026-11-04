import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'

const emptyPage = () => HttpResponse.json({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 1 })

/**
 * Inventaire vide par défaut : le tableau de bord, page d'accueil de l'application, interroge ces listes dès qu'un
 * utilisateur est connecté. Chaque test remplace ces réponses par les siennes avec server.use(...).
 */
const defaultHandlers = [
  http.get('/api/v1/servers', emptyPage),
  http.get('/api/v1/projects', emptyPage),
  http.get('/api/v1/audit-logs', emptyPage),
]

/** API simulée : toute autre requête doit être déclarée par le test (onUnhandledFrame: 'error'). */
export const server = setupServer(...defaultHandlers)
