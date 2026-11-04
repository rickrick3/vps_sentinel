import { expect, test } from 'vitest'
import { asProblem, fieldErrors, isProblem, problemCode } from './problem'

test('fieldErrors maps errors[]', () => {
  expect(fieldErrors({ type: 'urn:sentinel:problem:validation-error', status: 400, errors: [{ field: 'name', message: 'requis' }] }))
    .toEqual({ name: 'requis' })
})

test('fieldErrors joins several messages for the same field', () => {
  const problem = {
    type: 'urn:sentinel:problem:validation-error',
    status: 400,
    errors: [
      { field: 'name', message: 'requis' },
      { field: 'version', message: 'ne doit pas être nul' },
      { field: 'name', message: '100 caractères maximum' },
    ],
  }

  expect(fieldErrors(problem)).toEqual({ name: 'requis ; 100 caractères maximum', version: 'ne doit pas être nul' })
})

test('fieldErrors is empty without errors[]', () => {
  expect(fieldErrors({ type: 'urn:sentinel:problem:not-found', status: 404 })).toEqual({})
})

test('isProblem rejects plain JSON', () => expect(isProblem({ foo: 1 })).toBe(false))

test('isProblem rejects non-objects and foreign problem types', () => {
  expect(isProblem(null)).toBe(false)
  expect(isProblem('<html>Bad Gateway</html>')).toBe(false)
  expect(isProblem({ type: 'about:blank', status: 500 })).toBe(false)
  expect(isProblem({ type: 'urn:sentinel:problem:forbidden' })).toBe(false)
})

test('isProblem accepts a Sentinel problem', () => {
  expect(isProblem({ type: 'urn:sentinel:problem:forbidden', title: 'Accès refusé', status: 403 })).toBe(true)
})

test('problemCode returns the URN suffix', () => {
  expect(problemCode({ type: 'urn:sentinel:problem:version-conflict', status: 409 })).toBe('version-conflict')
})

test('asProblem keeps a problem and wraps anything else', () => {
  const forbidden = { type: 'urn:sentinel:problem:forbidden', status: 403 }

  expect(asProblem(forbidden, 403)).toBe(forbidden)
  expect(asProblem('<html>Bad Gateway</html>', 502)).toEqual({
    type: 'urn:sentinel:problem:unexpected-response',
    status: 502,
    detail: 'Le serveur a renvoyé une réponse inattendue (502).',
  })
})
