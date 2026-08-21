import test from 'node:test'
import assert from 'node:assert/strict'

import {
  blockerLabel,
  filenameFromContentDisposition,
  formatBytes,
  isKnownReadiness,
  isPreparationCaseId,
  isPreparationTaskLabel,
  preparationCommandPolicy,
  preparationScopeKey,
  professionalErrorKind,
  scopedResultApplies,
  normalizeOrganizationId,
  readinessPresentation,
  safeDownloadFilename
} from './preparationPresentation.js'

test('unknown readiness fails closed in the presentation', () => {
  assert.equal(readinessPresentation('FUTURE_RULE').label, 'État indisponible')
  assert.equal(readinessPresentation('FUTURE_RULE').color, 'error')
  assert.equal(isKnownReadiness('FUTURE_RULE'), false)
  assert.equal(isKnownReadiness('READY'), true)
  assert.match(blockerLabel('FUTURE_BLOCKER'), /Vérification/)
})

test('attachment sizes are presented without guessing invalid values', () => {
  assert.equal(formatBytes(2_500), '2.5 Ko')
  assert.equal(formatBytes(-1), 'Taille indisponible')
})

test('case identifiers and task labels are validated without UTF-16 truncation', () => {
  assert.equal(isPreparationCaseId('2f3de63b-692d-4c20-8a48-c7ec01b95b21'), true)
  assert.equal(isPreparationCaseId('2f3de63b-692d-4c20----------c7ec01b95b21'), false)
  assert.equal(isPreparationTaskLabel('🩺'.repeat(240)), true)
  assert.equal(isPreparationTaskLabel('🩺'.repeat(241)), false)
})

function preparation(overrides = {}) {
  return {
    caseId: '2f3de63b-692d-4c20-8a48-c7ec01b95b21',
    caseStatus: 'COLLECTING',
    openTaskCount: 0,
    patientReason: { present: true },
    readyConfirmation: {
      confirmed: false,
      confirmedByUserId: null,
      confirmedAt: null,
      ruleSetId: null,
      ruleSetVersion: null,
      reviewedCaseRevision: null
    },
    readiness: {
      ruleSetId: 'CORE_PREPARATION',
      ruleSetVersion: 1,
      state: 'REVIEWABLE',
      blockers: [],
      provenance: 'DERIVED_DETERMINISTIC'
    },
    ...overrides
  }
}

test('command policy mirrors the supported deterministic workflow', () => {
  assert.deepEqual(preparationCommandPolicy(preparation()), {
    trusted: true,
    createTask: true,
    changeTaskStatus: false,
    confirmReady: true
  })
  assert.deepEqual(preparationCommandPolicy(preparation({
    caseStatus: 'NEEDS_ACTION',
    openTaskCount: 2,
    readiness: {
      ruleSetId: 'CORE_PREPARATION',
      ruleSetVersion: 1,
      state: 'ACTION_REQUIRED',
      blockers: ['OPEN_PREPARATION_TASKS'],
      provenance: 'DERIVED_DETERMINISTIC'
    }
  })), {
    trusted: true,
    createTask: true,
    changeTaskStatus: true,
    confirmReady: false
  })

  const confirmed = preparation({
    caseStatus: 'READY_FOR_CONSULTATION',
    readyConfirmation: {
      confirmed: true,
      confirmedByUserId: 42,
      confirmedAt: '2026-08-21T13:00:00Z',
      ruleSetId: 'CORE_PREPARATION',
      ruleSetVersion: 1,
      reviewedCaseRevision: 4
    },
    readiness: {
      ruleSetId: 'CORE_PREPARATION',
      ruleSetVersion: 1,
      state: 'READY',
      blockers: [],
      provenance: 'DERIVED_DETERMINISTIC'
    }
  })
  assert.deepEqual(preparationCommandPolicy(confirmed), {
    trusted: true,
    createTask: false,
    changeTaskStatus: false,
    confirmReady: false
  })
})

test('unknown or inconsistent workflow responses disable every mutation', () => {
  const future = preparation()
  future.readiness = { ...future.readiness, ruleSetVersion: 2 }
  assert.deepEqual(preparationCommandPolicy(future), {
    trusted: false,
    createTask: false,
    changeTaskStatus: false,
    confirmReady: false
  })

  const inconsistent = preparation({ openTaskCount: 1 })
  assert.equal(preparationCommandPolicy(inconsistent).trusted, false)

  const unknownBlocker = preparation()
  unknownBlocker.readiness = { ...unknownBlocker.readiness, blockers: ['FUTURE_BLOCKER'] }
  assert.equal(preparationCommandPolicy(unknownBlocker).trusted, false)
})

test('professional HTTP errors and preparation scopes have stable fail-closed categories', () => {
  assert.equal(professionalErrorKind({ response: { status: 401 } }), 'UNAUTHENTICATED')
  assert.equal(professionalErrorKind({ response: { status: 403 } }), 'FORBIDDEN')
  assert.equal(professionalErrorKind({ response: { status: 404 } }), 'NOT_FOUND')
  assert.equal(professionalErrorKind({ response: { status: 409 } }), 'CONFLICT')
  assert.equal(professionalErrorKind({ response: { status: 503 } }), 'UNAVAILABLE')
  assert.equal(professionalErrorKind(new Error('network')), 'UNKNOWN')
  assert.notEqual(
    preparationScopeKey('org-a', '2f3de63b-692d-4c20-8a48-c7ec01b95b21'),
    preparationScopeKey('org-b', '2f3de63b-692d-4c20-8a48-c7ec01b95b21')
  )
  const scope = preparationScopeKey('org-a', '2f3de63b-692d-4c20-8a48-c7ec01b95b21')
  assert.equal(scopedResultApplies(scope, scope, 3, 3), true)
  assert.equal(scopedResultApplies(scope, scope, 3, 4), false)
  assert.equal(scopedResultApplies(scope, preparationScopeKey(
    'org-a', '33d14480-a6ea-43a7-8933-f0c8c7449a2c'
  ), 3, 3), false)
})

test('organization configuration is trimmed and rejects control characters', () => {
  assert.equal(normalizeOrganizationId(' cabinet-a '), 'cabinet-a')
  assert.equal(normalizeOrganizationId('cabinet\nother'), '')
  assert.equal(normalizeOrganizationId(''), '')
})

test('download filenames cannot inject paths or control characters', () => {
  assert.equal(safeDownloadFilename('../../résultat\n.pdf'), '.._.._résultat_.pdf')
  assert.equal(
    filenameFromContentDisposition("attachment; filename*=UTF-8''r%C3%A9sultat.pdf", 'fallback.pdf'),
    'résultat.pdf'
  )
  assert.equal(filenameFromContentDisposition('attachment', '../fallback.pdf'), '.._fallback.pdf')
})
