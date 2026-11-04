import { expect, test } from '@playwright/test'
import { ADMIN_EMAIL, ADMIN_NEW_PASSWORD, apiLogin, BASE_URL } from './helpers/api'

// Après les parcours : le dernier test sature la limitation de débit du login.

test('security headers on / and /api', async ({ request }) => {
  for (const path of ['/', '/servers/inconnu', '/api/v1/me', '/actuator/health']) {
    const response = await request.get(path)
    const headers = response.headers()
    expect(headers['content-security-policy'], path).toBe(
      "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; " +
        "connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'",
    )
    // Une seule valeur : celle de l'API est retirée au profit de celle du gateway.
    expect(headers['x-content-type-options'], path).toBe('nosniff')
    expect(headers['referrer-policy'], path).toBe('strict-origin-when-cross-origin')
    // HTTP en dev : pas de HSTS. Version de Nginx masquée.
    expect(headers['strict-transport-security'], path).toBeUndefined()
    expect(headers['server'], path).toBe('nginx')
  }
  expect((await request.get('/api/v1/me')).status()).toBe(401)
  // Le contrat OpenAPI n'est ni servi par l'API en prod, ni routé par le gateway : la route tombe sur le frontend.
  const apiDocs = await request.get('/v3/api-docs')
  expect(await apiDocs.text()).not.toContain('"openapi"')
})

test('spoofed X-Forwarded-For is not the audited ip', async ({ request }) => {
  const spoofedIp = '203.0.113.77'
  const failed = await request.post('/api/v1/auth/login', {
    headers: { Origin: BASE_URL, 'X-Forwarded-For': spoofedIp, 'X-Request-Id': 'choisi-par-le-client' },
    data: { email: `inconnu-${Date.now()}@exemple.fr`, password: 'mauvais-mot-de-passe' },
  })
  expect(failed.status()).toBe(401)

  const token = await apiLogin(request, ADMIN_EMAIL, ADMIN_NEW_PASSWORD)
  const response = await request.get('/api/v1/audit-logs?action=LOGIN_FAILED&sort=occurredAt,desc&size=1', {
    headers: { Authorization: `Bearer ${token}` },
  })
  const [latest] = ((await response.json()) as { items: { ip: string | null; correlationId: string | null }[] }).items
  expect(latest?.ip).toBeTruthy()
  expect(latest?.ip).not.toBe(spoofedIp)
  // L'identifiant de requête vient du gateway, jamais du client ; c'est celui renvoyé dans la réponse.
  expect(latest?.correlationId).not.toBe('choisi-par-le-client')
  expect(latest?.correlationId).toBe(failed.headers()['x-request-id'])
})

test('rate limit answers 429 problem json', async ({ request }) => {
  const responses = await Promise.all(
    Array.from({ length: 20 }, () =>
      request.post('/api/v1/auth/login', {
        headers: { Origin: BASE_URL },
        data: { email: 'rafale@exemple.fr', password: 'mauvais-mot-de-passe' },
      })),
  )

  const limited = []
  for (const response of responses.filter((candidate) => candidate.status() === 429)) {
    expect(response.headers()['content-type']).toContain('application/problem+json')
    const body = (await response.json()) as { type: string; status: number }
    if (body.type === 'urn:sentinel:problem:rate-limited') {
      limited.push(body)
    }
  }
  // Au moins une réponse vient du gateway (et non de la limitation de l'API, de type login-throttled).
  expect(limited.length).toBeGreaterThan(0)
  expect(limited[0]).toEqual({ type: 'urn:sentinel:problem:rate-limited', title: 'Trop de requêtes', status: 429 })
})
