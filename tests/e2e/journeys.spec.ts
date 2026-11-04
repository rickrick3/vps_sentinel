import { expect, test, type Locator, type Page } from '@playwright/test'
import {
  ADMIN_BOOTSTRAP_PASSWORD,
  ADMIN_EMAIL,
  ADMIN_NEW_PASSWORD,
  apiLogin,
  BASE_URL,
  navLink,
  uiChangePassword,
  uiLogin,
} from './helpers/api'

// Les deux parcours partagent la base : E2E-2 s'appuie sur le VPS, le projet et le mot de passe admin d'E2E-1.
test.describe.configure({ mode: 'serial' })

const IN_USE = 'VPS utilisé par des déploiements'

/**
 * Choix d'une option dans un Select de Mantine. Le champ est cherché dans `scope` (une modale, par exemple, quand la page
 * a un filtre du même nom) ; la liste des options est rendue hors de la modale, d'où la recherche dans la page.
 */
async function choose(page: Page, label: string, option: string, scope: Page | Locator = page) {
  await scope.getByRole('combobox', { name: label }).click()
  await page.getByRole('option', { name: option, exact: true }).click()
}

test('E2E-1 admin journey', async ({ page, request }) => {
  // 1-2. Administrateur initial, changement de mot de passe imposé.
  await uiLogin(page, ADMIN_EMAIL, ADMIN_BOOTSTRAP_PASSWORD)
  await uiChangePassword(page, ADMIN_BOOTSTRAP_PASSWORD, ADMIN_NEW_PASSWORD)
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible()

  // 3. VPS-01 : fournisseur, région, OS, tags.
  await navLink(page, 'VPS').click()
  await page.getByRole('link', { name: 'Nouveau VPS' }).click()
  await page.getByLabel('Nom').fill('VPS-01')
  // Champs auto-complétés : la liste de suggestions porte le même libellé que le champ, d'où le rôle.
  await page.getByRole('combobox', { name: 'Fournisseur' }).fill('OVH')
  await page.getByRole('combobox', { name: 'Région' }).fill('GRA')
  await choose(page, 'Système', 'Ubuntu')
  await page.getByLabel('Version du système').fill('24.04')
  await page.getByRole('combobox', { name: 'Tags' }).fill('web')
  await page.getByRole('combobox', { name: 'Tags' }).press('Enter')
  await page.getByRole('button', { name: 'Enregistrer' }).click()
  await expect(page.getByRole('heading', { name: 'VPS-01' })).toBeVisible()

  // 4. Projet Demo, environnement prod (PRODUCTION), domaine demo.example.com.
  await navLink(page, 'Projets').click()
  await page.getByRole('button', { name: 'Nouveau projet' }).click()
  await page.getByRole('dialog').getByLabel('Nom').fill('Demo')
  await page.getByRole('dialog').getByRole('button', { name: 'Enregistrer' }).click()
  await expect(page.getByRole('heading', { name: 'Demo', exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Ajouter un environnement' }).click()
  await page.getByRole('dialog').getByLabel('Nom').fill('prod')
  await choose(page, 'Nature', 'Production', page.getByRole('dialog'))
  await page.getByRole('dialog').getByRole('button', { name: 'Enregistrer' }).click()
  await expect(page.getByRole('tab', { name: 'prod' })).toBeVisible()

  await page.getByRole('button', { name: 'Ajouter un domaine' }).click()
  await page.getByRole('dialog').getByLabel('Nom de domaine').fill('demo.example.com')
  await page.getByRole('dialog').getByLabel('Domaine principal').check()
  await page.getByRole('dialog').getByRole('button', { name: 'Ajouter' }).click()
  const domains = page.getByRole('table', { name: 'Domaines de prod' })
  await expect(domains.getByText('demo.example.com')).toBeVisible()
  await expect(domains.getByText('Principal')).toBeVisible()

  // 5. Rattachement de prod à VPS-01, rôle « web ».
  await page.getByRole('button', { name: 'Rattacher un VPS' }).click()
  await choose(page, 'VPS', 'VPS-01', page.getByRole('dialog'))
  await page.getByRole('dialog').getByLabel('Rôle').fill('web')
  await page.getByRole('dialog').getByRole('button', { name: 'Rattacher' }).click()
  const deployments = page.getByRole('table', { name: 'Déploiements de prod' })
  await expect(deployments.getByRole('link', { name: 'VPS-01' })).toBeVisible()

  // 6. La fiche VPS-01 liste Demo / prod.
  await deployments.getByRole('link', { name: 'VPS-01' }).click()
  const hosted = page.getByRole('table', { name: 'Environnements hébergés' })
  await expect(hosted.getByRole('link', { name: 'Demo' })).toBeVisible()
  await expect(hosted.getByText('prod')).toBeVisible()

  // Journal d'audit : les actions apparaissent, avec l'IP réelle et l'identifiant de requête du gateway.
  await navLink(page, 'Audit').click()
  const journal = page.getByRole('table', { name: "Journal d'audit" })
  for (const label of ['VPS créé', 'Projet créé', 'VPS rattaché']) {
    await expect(journal.getByText(label, { exact: true }).first()).toBeVisible()
  }
  const token = await apiLogin(request, ADMIN_EMAIL, ADMIN_NEW_PASSWORD)
  for (const action of ['SERVER_CREATED', 'PROJECT_CREATED', 'DEPLOYMENT_ADDED']) {
    const response = await request.get(`/api/v1/audit-logs?action=${action}&sort=occurredAt,desc&size=1`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    const [entry] = ((await response.json()) as { items: { ip: string | null; correlationId: string | null; actorEmail: string }[] }).items
    expect(entry, action).toBeDefined()
    expect(entry?.ip, action).toBeTruthy()
    expect(entry?.correlationId, action).toBeTruthy()
    expect(entry?.actorEmail, action).toBe(ADMIN_EMAIL)
  }

  // 7. Ni suppression, ni passage en DECOMMISSIONED : le VPS héberge prod.
  await navLink(page, 'VPS').click()
  await page.getByRole('link', { name: 'VPS-01' }).click()
  await page.getByRole('button', { name: 'Supprimer' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Confirmer' }).click()
  await expect(page.getByText(IN_USE)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'VPS-01' })).toBeVisible()
  // La notification se ferme d'elle-même : la suivante est donc bien nouvelle.
  await expect(page.getByText(IN_USE)).toHaveCount(0)

  await page.getByRole('link', { name: 'Modifier' }).click()
  await choose(page, 'Statut administratif', 'Décommissionné')
  await page.getByRole('button', { name: 'Enregistrer' }).click()
  await expect(page.getByText(IN_USE)).toBeVisible()
  await expect(page).toHaveURL(/\/servers\/[^/]+\/edit$/)
})

test('E2E-2 viewer is read-only', async ({ page, browser, request }) => {
  const viewerEmail = `lecteur-${Date.now()}@exemple.fr`
  const viewerPassword = 'Viewer-E2E-Password-1!'

  // 1. L'admin crée un VIEWER et lit le mot de passe temporaire, affiché une seule fois.
  await uiLogin(page, ADMIN_EMAIL, ADMIN_NEW_PASSWORD)
  await navLink(page, 'Utilisateurs').click()
  await page.getByRole('button', { name: 'Nouvel utilisateur' }).click()
  const form = page.getByRole('dialog')
  await form.getByLabel('Email').fill(viewerEmail)
  await form.getByLabel('Nom complet').fill('Lecteur E2E')
  await choose(page, 'Rôle', 'Lecteur', form)
  await form.getByRole('button', { name: 'Créer' }).click()
  const shown = page.getByRole('dialog', { name: 'Mot de passe temporaire' })
  const temporaryPassword = (await shown.locator('code').textContent())?.trim() ?? ''
  expect(temporaryPassword).toHaveLength(16)
  await shown.getByRole('button', { name: "J'ai noté le mot de passe" }).click()
  await expect(page.getByText(temporaryPassword)).toHaveCount(0)

  // 2. Le VIEWER se connecte dans sa propre session et doit changer son mot de passe.
  const viewerContext = await browser.newContext({ baseURL: BASE_URL, locale: 'fr-FR' })
  const viewer = await viewerContext.newPage()
  await uiLogin(viewer, viewerEmail, temporaryPassword)
  await uiChangePassword(viewer, temporaryPassword, viewerPassword)
  await expect(viewer.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible()

  // 3. Lecture seule : ni Utilisateurs ni Audit, aucune action de création, de modification ou de suppression.
  await expect(navLink(viewer, 'VPS')).toBeVisible()
  await expect(navLink(viewer, 'Projets')).toBeVisible()
  await expect(navLink(viewer, 'Utilisateurs')).toHaveCount(0)
  await expect(navLink(viewer, 'Audit')).toHaveCount(0)
  await expect(viewer.getByRole('heading', { name: 'Dernières actions' })).toHaveCount(0)

  await navLink(viewer, 'VPS').click()
  await expect(viewer.getByRole('link', { name: 'VPS-01' })).toBeVisible()
  await expect(viewer.getByRole('link', { name: 'Nouveau VPS' })).toHaveCount(0)
  await viewer.getByRole('link', { name: 'VPS-01' }).click()
  await expect(viewer.getByRole('heading', { name: 'VPS-01' })).toBeVisible()
  await expect(viewer.getByRole('link', { name: 'Modifier' })).toHaveCount(0)
  await expect(viewer.getByRole('button', { name: 'Supprimer' })).toHaveCount(0)

  await navLink(viewer, 'Projets').click()
  await expect(viewer.getByRole('button', { name: 'Nouveau projet' })).toHaveCount(0)
  await viewer.getByRole('link', { name: 'Demo' }).click()
  await expect(viewer.getByRole('tab', { name: 'prod' })).toBeVisible()
  for (const action of ['Modifier le projet', 'Supprimer le projet', 'Ajouter un environnement', 'Ajouter un domaine', 'Rattacher un VPS']) {
    await expect(viewer.getByRole('button', { name: action })).toHaveCount(0)
  }
  await viewerContext.close()

  // 4. L'API refuse aussi : le masquage n'est qu'un confort d'interface.
  const token = await apiLogin(request, viewerEmail, viewerPassword)
  const created = await request.post('/api/v1/servers', {
    headers: { Authorization: `Bearer ${token}` },
    data: { name: 'VPS-INTERDIT' },
  })
  expect(created.status()).toBe(403)
})
