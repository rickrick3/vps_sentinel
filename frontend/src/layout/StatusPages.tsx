import { Anchor, Button, Center, Loader, Stack, Text, Title } from '@mantine/core'
import { Link } from 'react-router'

export function LoadingPage() {
  return (
    <Center h="100vh">
      <Loader aria-label="Chargement" />
    </Center>
  )
}

export function NotFoundPage() {
  return (
    <Stack gap="xs">
      <Title order={2}>Page introuvable</Title>
      <Text c="dimmed">Cette adresse ne correspond à aucune page.</Text>
      <Anchor component={Link} to="/">
        Retour au tableau de bord
      </Anchor>
    </Stack>
  )
}

/** Route masquée par l'interface mais ouverte par son adresse : l'API refuserait de toute façon ses données. */
export function ForbiddenPage() {
  return (
    <Stack gap="xs">
      <Title order={2}>Accès refusé</Title>
      <Text c="dimmed">Votre rôle ne permet pas d'afficher cette page.</Text>
      <Anchor component={Link} to="/">
        Retour au tableau de bord
      </Anchor>
    </Stack>
  )
}

/** Erreur imprévue pendant l'affichage d'une route. */
export function RouteErrorPage() {
  return (
    <Center h="100vh">
      <Stack gap="xs" align="center">
        <Title order={2}>Une erreur inattendue est survenue</Title>
        <Text c="dimmed">Rechargez la page. Si le problème persiste, contactez un administrateur.</Text>
        <Button onClick={() => window.location.reload()}>Recharger</Button>
      </Stack>
    </Center>
  )
}
