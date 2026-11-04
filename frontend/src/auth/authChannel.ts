const CHANNEL = 'sentinel-auth'

// BroadcastChannel remet un message à tous les autres canaux du même nom, y compris ceux du même onglet : l'AuthProvider
// recevrait sa propre déconnexion. Chaque message porte donc l'identifiant de l'onglet émetteur. getRandomValues reste
// disponible hors contexte sécurisé, contrairement à randomUUID.
const TAB_ID = Array.from(crypto.getRandomValues(new Uint32Array(4)), (part) => part.toString(36)).join('')

type AuthMessage = { type: 'logout'; sender: string }

/** Annonce la déconnexion aux autres onglets de la même origine (spec §12.2). */
export function broadcastLogout(): void {
  if (typeof BroadcastChannel === 'undefined') {
    return
  }
  const channel = new BroadcastChannel(CHANNEL)
  channel.postMessage({ type: 'logout', sender: TAB_ID } satisfies AuthMessage)
  channel.close()
}

/** Écoute les déconnexions des autres onglets, pas celle de cet onglet ; renvoie la fonction qui arrête l'écoute. */
export function onLogout(callback: () => void): () => void {
  if (typeof BroadcastChannel === 'undefined') {
    return () => {}
  }
  const channel = new BroadcastChannel(CHANNEL)
  channel.onmessage = (event: MessageEvent<Partial<AuthMessage> | null>) => {
    if (event.data?.type === 'logout' && event.data.sender !== TAB_ID) {
      callback()
    }
  }
  return () => channel.close()
}
