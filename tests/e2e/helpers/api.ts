import { expect, type APIRequestContext, type Page } from '@playwright/test'

/** Gateway de la pile dev ; aussi l'origine autorisée envoyée par les appels directs à l'API. */
export const BASE_URL = 'http://localhost:8088'

// Administrateur initial : variables de bootstrap de .env (sourcé par scripts/e2e.sh), défauts de .env.example.
export const ADMIN_EMAIL = process.env.SENTINEL_BOOTSTRAP_ADMIN_EMAIL ?? 'admin@sentinel.local'
export const ADMIN_BOOTSTRAP_PASSWORD = process.env.SENTINEL_BOOTSTRAP_ADMIN_PASSWORD ?? 'Bootstrap-Dev-2026!'
export const ADMIN_NEW_PASSWORD = 'Admin-E2E-Password-1!'

/** Connexion par l'API, comme le frontend : l'en-tête Origin est exigé (spec §8.9). Renvoie l'access token. */
export async function apiLogin(request: APIRequestContext, email: string, password: string): Promise<string> {
  const response = await request.post('/api/v1/auth/login', { headers: { Origin: BASE_URL }, data: { email, password } })
  expect(response.status(), await response.text()).toBe(200)
  const body = (await response.json()) as { accessToken: string }
  return body.accessToken
}

/** Connexion par l'interface. */
export async function uiLogin(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Mot de passe').fill(password)
  await page.getByRole('button', { name: 'Se connecter' }).click()
}

/** Changement de mot de passe, imposé ou volontaire, depuis la page /change-password. */
export async function uiChangePassword(page: Page, current: string, next: string): Promise<void> {
  await expect(page.getByRole('heading', { name: 'Changer le mot de passe' })).toBeVisible()
  await page.getByLabel('Mot de passe actuel').fill(current)
  await page.getByLabel('Nouveau mot de passe', { exact: true }).fill(next)
  await page.getByLabel('Confirmation du nouveau mot de passe').fill(next)
  await page.getByRole('button', { name: 'Changer le mot de passe' }).click()
}

/** Lien de la navigation principale. */
export function navLink(page: Page, name: string) {
  return page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name, exact: true })
}
