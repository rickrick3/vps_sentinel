import { Stack, Text, Title } from '@mantine/core'

/** Page provisoire, remplacée par l'écran de la fonctionnalité au jalon suivant. */
export function PlaceholderPage({ title }: { title: string }) {
  return (
    <Stack gap="xs">
      <Title order={2}>{title}</Title>
      <Text c="dimmed">Cette page sera disponible prochainement.</Text>
    </Stack>
  )
}
