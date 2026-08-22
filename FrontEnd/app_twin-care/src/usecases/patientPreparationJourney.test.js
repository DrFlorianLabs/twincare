import assert from 'node:assert/strict'
import test from 'node:test'

import { InvitationCaptureKind } from '../security/invitationSecret.js'
import { PatientAccessError, PatientAccessErrorKind } from '../services/patientAccessService.js'
import {
  createPatientPreparationJourney,
  PatientJourneyPhase,
} from './patientPreparationJourney.js'

function client(overrides = {}) {
  return {
    exchangeInvitation: async () => {},
    restoreSession: async () => ({ expiresAt: '2026-08-23T10:00:00Z' }),
    readReason: async () => ({ present: false }),
    saveReason: async () => {},
    clearSecrets: () => {},
    ...overrides,
  }
}

test('opens a new invitation and shows the minimal reason form', async () => {
  const calls = []
  const capture = { kind: InvitationCaptureKind.TOKEN, token: 'A'.repeat(43) }
  const journey = createPatientPreparationJourney(
    client({ exchangeInvitation: async (token) => calls.push(token) }),
  )

  await journey.start(capture)

  assert.deepEqual(calls, ['A'.repeat(43)])
  assert.equal(capture.token, null)
  assert.equal(journey.state.phase, PatientJourneyPhase.FORM)
})

test('resumes an existing reason from the server-owned session', async () => {
  const journey = createPatientPreparationJourney(
    client({
      readReason: async () => ({
        present: true,
        reason: 'Motif synthétique déjà enregistré',
        recordedAt: '2026-08-22T10:00:00Z',
      }),
    }),
  )

  await journey.start({ kind: InvitationCaptureKind.NONE })

  assert.equal(journey.state.phase, PatientJourneyPhase.SAVED)
  assert.equal(journey.state.draft, 'Motif synthétique déjà enregistré')
})

test('never calls the backend for an invalid invitation location', async () => {
  let calls = 0
  const journey = createPatientPreparationJourney(
    client({ restoreSession: async () => (calls += 1) }),
  )

  await journey.start({ kind: InvitationCaptureKind.INVALID })

  assert.equal(calls, 0)
  assert.equal(journey.state.phase, PatientJourneyPhase.LINK_ERROR)
})

test('offers an explicit retry when invitation exchange is temporarily unavailable', async () => {
  const journey = createPatientPreparationJourney(
    client({
      exchangeInvitation: async () => {
        throw new PatientAccessError(PatientAccessErrorKind.NETWORK)
      },
    }),
  )

  await journey.start({ kind: InvitationCaptureKind.TOKEN, token: 'A'.repeat(43) })

  assert.equal(journey.state.phase, PatientJourneyPhase.NETWORK_ERROR)
})

test('resolves an ambiguous exchange by trying the server session before replaying the token', async () => {
  let exchanges = 0
  let restores = 0
  const journey = createPatientPreparationJourney(
    client({
      exchangeInvitation: async () => {
        exchanges += 1
        throw new PatientAccessError(PatientAccessErrorKind.NETWORK)
      },
      restoreSession: async () => {
        restores += 1
        return { expiresAt: '2026-08-23T10:00:00Z' }
      },
    }),
  )
  await journey.start({ kind: InvitationCaptureKind.TOKEN, token: 'A'.repeat(43) })

  await journey.retryConnection()

  assert.equal(exchanges, 1)
  assert.equal(restores, 1)
  assert.equal(journey.state.phase, PatientJourneyPhase.FORM)
})

test('prevents duplicate saves and performs one explicit write', async () => {
  let release
  let writes = 0
  const pendingWrite = new Promise((resolve) => {
    release = resolve
  })
  const journey = createPatientPreparationJourney(
    client({
      saveReason: async () => {
        writes += 1
        await pendingWrite
      },
    }),
  )
  await journey.start({ kind: InvitationCaptureKind.NONE })
  journey.state.draft = '  Motif synthétique  '

  const first = journey.saveReason()
  const second = journey.saveReason()
  release()
  await Promise.all([first, second])

  assert.equal(writes, 1)
  assert.equal(journey.state.savedReason, 'Motif synthétique')
  assert.equal(journey.state.phase, PatientJourneyPhase.SAVED)
})

test('keeps the draft in memory after a failed write and does not retry', async () => {
  let writes = 0
  const journey = createPatientPreparationJourney(
    client({
      saveReason: async () => {
        writes += 1
        throw new PatientAccessError(PatientAccessErrorKind.NETWORK)
      },
    }),
  )
  await journey.start({ kind: InvitationCaptureKind.NONE })
  journey.state.draft = 'Motif synthétique conservé'

  await journey.saveReason()

  assert.equal(writes, 1)
  assert.equal(journey.state.phase, PatientJourneyPhase.FORM)
  assert.equal(journey.state.draft, 'Motif synthétique conservé')
  assert.match(journey.state.formError, /réessayer/u)
})

test('fails closed when the session expires during the write', async () => {
  const journey = createPatientPreparationJourney(
    client({
      saveReason: async () => {
        throw new PatientAccessError(PatientAccessErrorKind.UNAUTHORIZED)
      },
    }),
  )
  await journey.start({ kind: InvitationCaptureKind.NONE })
  journey.state.draft = 'Motif synthétique'

  await journey.saveReason()

  assert.equal(journey.state.phase, PatientJourneyPhase.SESSION_EXPIRED)
})
