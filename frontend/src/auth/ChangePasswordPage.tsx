import { Alert, Anchor, Button, Center, Group, Paper, Stack, Text, Title } from '@mantine/core'
import { useForm } from '@mantine/form'
import { notifications } from '@mantine/notifications'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError, fieldErrors } from '../api/problem'
import { retryAfterSeconds, waitMessage } from '../api/retryAfter'
import { PasswordField } from '../components/PasswordField'
import { useAuth } from './AuthProvider'

// Seuil de la politique des mots de passe (spec §8.6), en points de code ; l'API vérifie le reste.
const MIN_LENGTH = 12

/**
 * Changement de mot de passe (spec §12.3) : imposé à la première connexion et après une réinitialisation, ou volontaire
 * depuis le profil. En mode imposé, seule la déconnexion permet d'y échapper.
 */
export function ChangePasswordPage() {
  const { user, changePassword, logout } = useAuth()
  const navigate = useNavigate()
  const forced = user?.mustChangePassword ?? false
  const [error, setError] = useState<string | null>(null)
  const form = useForm({
    mode: 'uncontrolled',
    initialValues: { currentPassword: '', newPassword: '', confirmation: '' },
    validate: {
      currentPassword: (value) => (value ? null : 'Saisissez votre mot de passe actuel.'),
      newPassword: (value) => ([...value].length >= MIN_LENGTH ? null : `${MIN_LENGTH} caractères minimum.`),
      confirmation: (value, values) => (value === values.newPassword ? null : 'Les deux saisies ne correspondent pas.'),
    },
  })

  const submit = form.onSubmit(async ({ currentPassword, newPassword }) => {
    setError(null)
    try {
      await changePassword(currentPassword, newPassword)
      notifications.show({ color: 'green', message: 'Mot de passe modifié.' })
      navigate(forced ? '/' : '/profile', { replace: true })
    } catch (failure) {
      if (!(failure instanceof ApiError)) {
        setError('Modification impossible. Vérifiez votre connexion réseau.')
        return
      }
      const { problem, response } = failure
      if (problem.status === 400 && problem.errors?.length) {
        form.setErrors(fieldErrors(problem))
      } else if (problem.status === 429) {
        setError(waitMessage(retryAfterSeconds(response)))
      } else {
        setError(problem.detail ?? 'Modification impossible.')
      }
    }
  })

  return (
    <Center mih="100vh" p="md">
      <Paper withBorder shadow="sm" p="xl" w="100%" maw={440}>
        <form onSubmit={submit} noValidate>
          <Stack>
            <div>
              <Title order={2}>Changer le mot de passe</Title>
              <Text c="dimmed" size="sm">
                {user?.email}
              </Text>
            </div>
            {forced && <Alert color="blue">Votre mot de passe doit être changé avant de continuer.</Alert>}
            {error !== null && <Alert color="red">{error}</Alert>}
            <PasswordField
              label="Mot de passe actuel"
              description={forced ? 'Le mot de passe temporaire reçu de votre administrateur.' : undefined}
              autoComplete="current-password"
              key={form.key('currentPassword')}
              {...form.getInputProps('currentPassword')}
            />
            <PasswordField
              label="Nouveau mot de passe"
              description={`${MIN_LENGTH} caractères minimum, différent de votre email.`}
              autoComplete="new-password"
              key={form.key('newPassword')}
              {...form.getInputProps('newPassword')}
            />
            <PasswordField
              label="Confirmation du nouveau mot de passe"
              autoComplete="new-password"
              key={form.key('confirmation')}
              {...form.getInputProps('confirmation')}
            />
            <Button type="submit" loading={form.submitting} fullWidth>
              Changer le mot de passe
            </Button>
            <Group justify="center">
              {forced ? (
                <Button variant="subtle" color="gray" onClick={() => void logout()}>
                  Se déconnecter
                </Button>
              ) : (
                <Anchor component={Link} to="/profile" size="sm">
                  Annuler
                </Anchor>
              )}
            </Group>
          </Stack>
        </form>
      </Paper>
    </Center>
  )
}
