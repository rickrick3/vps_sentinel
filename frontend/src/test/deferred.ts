/** Promesse résolue à la main : une réponse simulée reste en attente tant que le test ne l'a pas libérée. */
export function deferred() {
  let release!: () => void
  const promise = new Promise<void>((resolve) => {
    release = resolve
  })
  return { promise, release }
}
