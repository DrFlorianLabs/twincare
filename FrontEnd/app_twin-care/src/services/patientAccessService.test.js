import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createPatientAccessClient,
  PatientAccessError,
  PatientAccessErrorKind,
} from './patientAccessService.js'

function jsonResponse(body, { status = 200, headers = {} } = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

test('exchanges an invitation using a cookie-capable, no-store request', async () => {
  const calls = []
  const client = createPatientAccessClient({
    apiBase: '',
    fetchImplementation: async (...arguments_) => {
      calls.push(arguments_)
      return new Response(null, { status: 200 })
    },
  })

  await client.exchangeInvitation('A'.repeat(43))

  assert.equal(calls.length, 1)
  assert.equal(calls[0][0], '/api/v1/patient-access/exchange')
  assert.equal(calls[0][1].credentials, 'include')
  assert.equal(calls[0][1].cache, 'no-store')
  assert.deepEqual(JSON.parse(calls[0][1].body), { token: 'A'.repeat(43) })
})

test('keeps CSRF in memory and sends it only on the explicit mutation', async () => {
  const calls = []
  const responses = [
    jsonResponse(
      { status: 'ACTIVE', expiresAt: '2026-08-23T10:00:00Z' },
      { headers: { 'X-TC-CSRF': 'synthetic-csrf' } },
    ),
    new Response(null, { status: 204 }),
  ]
  const client = createPatientAccessClient({
    fetchImplementation: async (...arguments_) => {
      calls.push(arguments_)
      return responses.shift()
    },
  })

  await client.restoreSession()
  await client.saveReason('Motif entièrement synthétique')

  assert.equal(calls[1][0], '/api/v1/patient-access/reason')
  assert.equal(calls[1][1].method, 'PUT')
  assert.equal(calls[1][1].headers['X-TC-CSRF'], 'synthetic-csrf')
  assert.deepEqual(JSON.parse(calls[1][1].body), {
    reason: 'Motif entièrement synthétique',
  })
})

test('reads both absent and present reasons without technical identifiers', async () => {
  const responses = [
    jsonResponse({ present: false }),
    jsonResponse({
      present: true,
      reason: 'Motif synthétique',
      recordedAt: '2026-08-22T10:00:00Z',
    }),
  ]
  const client = createPatientAccessClient({
    fetchImplementation: async () => responses.shift(),
  })

  assert.deepEqual(await client.readReason(), { present: false })
  assert.deepEqual(await client.readReason(), {
    present: true,
    reason: 'Motif synthétique',
    recordedAt: '2026-08-22T10:00:00Z',
  })
})

test('maps an unavailable session to one controlled error', async () => {
  const client = createPatientAccessClient({
    fetchImplementation: async () => new Response(null, { status: 401 }),
  })

  await assert.rejects(
    client.restoreSession(),
    (error) =>
      error instanceof PatientAccessError &&
      error.kind === PatientAccessErrorKind.UNAUTHORIZED &&
      error.message === 'Patient access request failed',
  )
})

test('does not retry a failed reason write', async () => {
  let calls = 0
  const responses = [
    jsonResponse(
      { status: 'ACTIVE', expiresAt: '2026-08-23T10:00:00Z' },
      { headers: { 'X-TC-CSRF': 'synthetic-csrf' } },
    ),
    new Response(null, { status: 503 }),
  ]
  const client = createPatientAccessClient({
    fetchImplementation: async () => {
      calls += 1
      return responses.shift()
    },
  })
  await client.restoreSession()

  await assert.rejects(
    client.saveReason('Motif synthétique'),
    (error) => error.kind === PatientAccessErrorKind.NETWORK,
  )
  assert.equal(calls, 2)
})

test('bounds an unresponsive request and reports one controlled network error', async () => {
  const client = createPatientAccessClient({
    requestTimeoutMs: 5,
    fetchImplementation: async (_url, options) =>
      new Promise((_resolve, reject) => {
        options.signal.addEventListener('abort', () => reject(new Error('synthetic abort')))
      }),
  })

  await assert.rejects(
    client.restoreSession(),
    (error) => error.kind === PatientAccessErrorKind.NETWORK,
  )
})
