import { Button, Group, Modal, Stack, Text } from '@mantine/core'
import type { ReactNode } from 'react'

type Props = {
  opened: boolean
  title: string
  children: ReactNode
  /** Suppression en cours : bouton de confirmation en attente, fermeture bloquée. */
  loading: boolean
  onConfirm: () => void
  onClose: () => void
}

/** Confirmation exigée avant toute suppression (spec §12.4) : rien n'est envoyé à l'API avant « Confirmer ». */
export function ConfirmDelete({ opened, title, children, loading, onConfirm, onClose }: Props) {
  return (
    <Modal opened={opened} onClose={loading ? () => {} : onClose} title={title} centered>
      <Stack>
        <Text>{children}</Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose} disabled={loading}>
            Annuler
          </Button>
          <Button color="red" onClick={onConfirm} loading={loading}>
            Confirmer
          </Button>
        </Group>
      </Stack>
    </Modal>
  )
}
