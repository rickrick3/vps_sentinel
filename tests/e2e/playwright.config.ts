import { defineConfig, devices } from '@playwright/test'
import { BASE_URL } from './helpers/api'

/**
 * Tests E2E (spec §13.8) sur la pile dev complète, derrière le gateway. Lancés par scripts/e2e.sh, qui démarre une
 * pile vierge. Les parcours partagent l'état de la base : un seul worker, les contrôles du gateway en dernier (ils
 * saturent la limitation de débit du login).
 */
export default defineConfig({
  testDir: '.',
  timeout: 90_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: Boolean(process.env.CI),
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    ...devices['Desktop Chrome'],
    baseURL: BASE_URL,
    locale: 'fr-FR',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'journeys', testMatch: /journeys\.spec\.ts/ },
    { name: 'gateway', testMatch: /gateway\.spec\.ts/, dependencies: ['journeys'] },
  ],
})
