import { afterEach, expect, test, vi } from 'vitest'
import type { components } from '../api/schema'
import { authResponse } from '../test/fixtures'
import { refreshOnce } from './refreshCoordinator'

type AuthResponse = components['schemas']['AuthResponse']
type LockCallback = () => Promise<unknown>

afterEach(() => {
  vi.useRealTimers()
})

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

/** Gestionnaire de verrous qui accorde le verrou aussitôt, mais respecte le signal à tout moment (plus strict que le vrai). */
function strictLocks() {
  return vi.fn((_name: string, options: LockOptions, callback: LockCallback) =>
    new Promise((resolve, reject) => {
      options.signal?.addEventListener('abort', () => reject(new DOMException('Abandon', 'AbortError')))
      callback().then(resolve, reject)
    }))
}

test('concurrent callers share one refresh', async () => {
  const pending = deferred<AuthResponse | null>()
  const doRefresh = vi.fn(() => pending.promise)

  const calls = [refreshOnce(doRefresh), refreshOnce(doRefresh), refreshOnce(doRefresh)]
  pending.resolve(authResponse('t1'))
  const results = await Promise.all(calls)

  expect(doRefresh).toHaveBeenCalledTimes(1)
  expect(results.map((result) => result?.accessToken)).toEqual(['t1', 't1', 't1'])
})

test('a new refresh starts once the previous one is over', async () => {
  const doRefresh = vi.fn(async () => authResponse('t'))

  await refreshOnce(doRefresh)
  await refreshOnce(doRefresh)

  expect(doRefresh).toHaveBeenCalledTimes(2)
})

test('a failed refresh is shared, then forgotten', async () => {
  const doRefresh = vi.fn<() => Promise<AuthResponse | null>>()
    .mockRejectedValueOnce(new Error('réseau'))
    .mockResolvedValueOnce(authResponse('t'))

  await expect(Promise.all([refreshOnce(doRefresh), refreshOnce(doRefresh)])).rejects.toThrow('réseau')
  expect(doRefresh).toHaveBeenCalledTimes(1)
  await expect(refreshOnce(doRefresh)).resolves.toMatchObject({ accessToken: 't' })
})

test('uses navigator.locks when available', async () => {
  const request = strictLocks()
  vi.stubGlobal('navigator', { locks: { request } })
  const doRefresh = vi.fn(async () => authResponse('t'))

  await refreshOnce(doRefresh)

  expect(request).toHaveBeenCalledWith('sentinel-refresh', { signal: expect.any(AbortSignal) }, expect.any(Function))
  expect(doRefresh).toHaveBeenCalledOnce()
})

test('falls back when navigator.locks is undefined', async () => {
  vi.stubGlobal('navigator', {})
  const doRefresh = vi.fn(async () => authResponse('t'))

  await expect(refreshOnce(doRefresh)).resolves.toMatchObject({ accessToken: 't' })
  expect(doRefresh).toHaveBeenCalledOnce()
})

test('gives up after 10 s waiting for the lock', async () => {
  vi.useFakeTimers()
  // Verrou tenu par un autre onglet : la demande n'aboutit qu'à l'abandon.
  const request = vi.fn((_name: string, options: LockOptions) =>
    new Promise((_resolve, reject) => {
      options.signal?.addEventListener('abort', () => reject(new DOMException('Abandon', 'AbortError')))
    }))
  vi.stubGlobal('navigator', { locks: { request } })
  const doRefresh = vi.fn(async () => authResponse('t'))
  let settled = false

  const result = refreshOnce(doRefresh)
  void result.then(() => {
    settled = true
  })
  await vi.advanceTimersByTimeAsync(9_999)
  expect(settled).toBe(false)
  await vi.advanceTimersByTimeAsync(1)

  await expect(result).resolves.toBeNull()
  expect(doRefresh).not.toHaveBeenCalled()
})

test('the 10 s limit only applies while waiting for the lock', async () => {
  vi.useFakeTimers()
  vi.stubGlobal('navigator', { locks: { request: strictLocks() } })
  const doRefresh = vi.fn(() => new Promise<AuthResponse>((resolve) => setTimeout(() => resolve(authResponse('t')), 15_000)))

  const result = refreshOnce(doRefresh)
  await vi.advanceTimersByTimeAsync(15_000)

  await expect(result).resolves.toMatchObject({ accessToken: 't' })
})
