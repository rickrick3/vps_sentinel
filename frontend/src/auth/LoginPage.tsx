import { Alert, Button, Center, Paper, Stack, Text, TextInput, Title } from '@mantine/core'
import { useForm } from '@mantine/form'
import { useEffect, useState } from 'react'
import { Navigate, useLocation, type Location } from 'react-router'
import { ApiError, fieldErrors } from '../api/problem'
import { retryAfterSeconds, waitMessage } from '../api/retryAfter'
import { PasswordField } from '../components/PasswordField'
import { LoadingPage } from '../layout/StatusPages'
import { useAuth } from './AuthProvider'

// Message unique pour toute erreur d'identifiants : il ne dit pas si le compte existe (spec §8.4).
const INVALID_CREDENTIALS = 'Identifiants invalides.'

/** Connexion (spec §12.3). Une fois connecté, l'utilisateur rejoint la page demandée, ou le changement de mot de passe. */
export function LoginPage() {
  const { status, user, login } = useAuth()
  const location = useLocation()
  const [error, setError] = useState<string | null>(null)
  // Secondes d'attente restantes après un 429, décomptées chaque seconde.
  const [remaining, setRemaining] = useState(0)
  const blocked = remaining > 0
  const form = useForm({
    mode: 'uncontrolled',
    initialValues: { email: '', password: '' },
    validate: {
      email: (value) => (value.trim() ? null : 'Saisissez votre email.'),
      password: (value) => (value ? null : 'Saisissez votre mot de passe.'),
    },
  })

  useEffect(() => {
    if (!blocked) {
      return undefined
    }
    const timer = setInterval(() => setRemaining((seconds) => Math.max(0, seconds - 1)), 1000)
    return () => clearInterval(timer)
  }, [blocked])

  if (status === 'loading') {
    return <LoadingPage />
  }
  if (user !== null) {
    const from = (location.state as { from?: Location } | null)?.from
    return <Navigate to={user.mustChangePassword ? '/change-password' : (from ?? '/')} replace />
  }

  const submit = form.onSubmit(async ({ email, password }) => {
    setError(null)
    try {
      await login(email.trim(), password)
    } catch (failure) {
      if (!(failure instanceof ApiError)) {
        setError('Connexion impossible. Vérifiez votre connexion réseau.')
        return
      }
      const { problem, response } = failure
      if (problem.status === 401) {
        setError(INVALID_CREDENTIALS)
      } else if (problem.status === 429) {
        setRemaining(retryAfterSeconds(response))
      } else if (problem.status === 400 && problem.errors?.length) {
        form.setErrors(fieldErrors(problem))
      } else {
        setError(problem.detail ?? 'Connexion impossible.')
      }
    }
  })

  return (
    <Center mih="100vh" p="md">
      <Paper withBorder shadow="sm" p="xl" w="100%" maw={400}>
        <form onSubmit={submit} noValidate>
          <Stack>
            <div>
              <Title order={2}>Connexion</Title>
              <Text c="dimmed" size="sm">
                VPS Sentinel
              </Text>
            </div>
            {error !== null && <Alert color="red">{error}</Alert>}
            {blocked && <Alert color="orange">{waitMessage(remaining)}</Alert>}
            <TextInput
              label="Email"
              type="email"
              autoComplete="username"
              key={form.key('email')}
              {...form.getInputProps('email')}
            />
            <PasswordField
              label="Mot de passe"
              autoComplete="current-password"
              key={form.key('password')}
              {...form.getInputProps('password')}
            />
            <Button type="submit" loading={form.submitting} disabled={blocked} fullWidth>
              Se connecter
            </Button>
          </Stack>
        </form>
      </Paper>
    </Center>
  )
}
