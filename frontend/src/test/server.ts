import { setupServer } from 'msw/node'

/** API simulée : chaque test déclare ses réponses avec server.use(...). */
export const server = setupServer()
