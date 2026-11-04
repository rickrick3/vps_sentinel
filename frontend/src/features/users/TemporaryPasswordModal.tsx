import { Button, Code, CopyButton, Group, Modal, Stack, Text } from '@mantine/core'
import { formatDateTime } from '../../components/format'

type Props = {
  password: string
  expiresAt: string
  /** Doit appeler `mutation.reset()` : le mot de passe quitte alors l'écran et le cache (spec §12.4). */
  onClose: () => void
}

/** Mot de passe temporaire, affiché une seule fois (spec §12.4). */
export function TemporaryPasswordModal({ password, expiresAt, onClose }: Props) {
  return (
    <Modal opened onClose={onClose} title="Mot de passe temporaire" closeOnClickOutside={false} centered>
      <Stack>
        <Text size="sm">
          Communiquez ce mot de passe à l'utilisateur par un canal sûr. Il ne sera plus affiché, et devra être changé à
          la première connexion.
        </Text>
        <Group gap="xs" wrap="nowrap">
          <Code fz="md" p="xs" style={{ flex: 1 }}>
            {password}
          </Code>
          <CopyButton value={password}>
            {({ copied, copy }) => (
              <Button variant={copied ? 'filled' : 'light'} color={copied ? 'green' : undefined} onClick={copy}>
                {copied ? 'Copié' : 'Copier'}
              </Button>
            )}
          </CopyButton>
        </Group>
        <Text size="sm" c="dimmed">
          Valable jusqu'au {formatDateTime(expiresAt)}
        </Text>
        <Group justify="flex-end">
          <Button onClick={onClose}>J'ai noté le mot de passe</Button>
        </Group>
      </Stack>
    </Modal>
  )
}
