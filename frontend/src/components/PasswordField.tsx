import { PasswordInput, type PasswordInputProps } from '@mantine/core'

/**
 * `PasswordInput` marqué `aria-invalid` en cas d'erreur. Mantine relie bien l'erreur au champ (`aria-describedby`),
 * mais ne signale pas l'état invalide sur ce composant, contrairement aux autres champs.
 */
export function PasswordField(props: PasswordInputProps) {
  return <PasswordInput {...props} aria-invalid={props.error ? true : undefined} />
}
