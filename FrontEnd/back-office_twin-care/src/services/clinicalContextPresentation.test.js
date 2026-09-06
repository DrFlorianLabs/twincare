import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildClinicalExport, buildClinicalVignette, canReviewClinicalReason,
  clinicalExportIsCurrent, clinicalScopeKey, clinicalSnapshotIsConsistent
} from './clinicalContextPresentation.js'
import { CASE_ID, TIME, clinicalFixture } from './clinicalContextFixtures.js'

test('known clinical document enums match preparation MIME types, without conflating certainty and review', () => {
  const { context, preparation } = clinicalFixture()
  for (const [enumType, mime] of [['PDF', 'application/pdf'], ['JPEG', 'image/jpeg'], ['PNG', 'image/png']]) {
    context.documents[0].mediaType = enumType
    preparation.attachment.mediaType = mime
    assert.equal(clinicalSnapshotIsConsistent(context, preparation), true)
  }
  const text = buildClinicalVignette(context, preparation)
  assert.match(text, /Certitude non évaluée/)
  assert.match(text, /Non relu/)
  assert.match(text, /Tâche synthétique/)
  assert.match(text, /document-synthetique.pdf/)
  assert.match(text, /contenu non analysé/)
  context.documents[0].mediaType = 'image/png'
  assert.equal(clinicalSnapshotIsConsistent(context, preparation), false)
})

test('rejected current wording and replaced wording are excluded from the vignette and retained in JSON', () => {
  const { context, preparation } = clinicalFixture()
  context.facts[0].review = { status: 'REJECTED', reviewedAt: TIME }
  context.readiness = 'NEEDS_CORRECTION'
  context.preparationItems[0].status = 'NEEDS_CORRECTION'
  context.facts.push({
    ...structuredClone(context.facts[0]),
    id: '6f3de63b-692d-4c20-8a48-c7ec01b95b25',
    value: { text: 'Ancien motif synthétique remplacé' },
    lifecycle: { status: 'SUPERSEDED', supersededAt: TIME }
  })
  const text = buildClinicalVignette(context, preparation)
  assert.match(text, /à corriger/)
  assert.doesNotMatch(text, /Motif synthétique actuel/)
  assert.doesNotMatch(text, /Ancien motif synthétique remplacé/)
  const payload = buildClinicalExport(context, preparation, 'cabinet-synthetique')
  assert.equal(payload.clinicalContext.facts.length, 2)
  assert.equal(payload.clinicalContext.facts[0].review.status, 'REJECTED')
  assert.equal(payload.clinicalContext.facts[1].value.text, 'Ancien motif synthétique remplacé')
  assert.equal(payload.preparation.tasks[0].status, 'COMPLETED')
  assert.equal(payload.preparation.patientReferenceVerified, false)
})

test('mismatched case, revision, source content, lifecycle, task count or document metadata fails closed', () => {
  const changes = [
    ({ context }) => { context.contextId = '6f3de63b-692d-4c20-8a48-c7ec01b95b25' },
    ({ context }) => { delete context.preparationRevision },
    ({ context }) => { context.preparationRevision += 1 },
    ({ context }) => { context.schemaVersion = 'unknown' },
    ({ context }) => { context.facts[0].provenance.type = 'UNSPECIFIED' },
    ({ context }) => { context.facts[0].certainty = 'CERTAIN' },
    ({ context }) => { context.facts[0].review.status = 'VERIFIED_BY_AI' },
    ({ context }) => { context.facts[0].lifecycle.status = 'UNKNOWN' },
    ({ context }) => { context.facts[0].value.text = 'Divergent synthetic text' },
    ({ context }) => { context.facts.push(structuredClone(context.facts[0])) },
    ({ context }) => { context.documents[0].sizeBytes += 1 },
    ({ context }) => { context.documents[0].displayFilename = 'other.pdf' },
    ({ preparation }) => { preparation.openTaskCount = 1 },
    ({ context }) => { context.preparationItems = [] }
  ]
  for (const change of changes) {
    const snapshot = clinicalFixture()
    change(snapshot)
    assert.equal(clinicalSnapshotIsConsistent(snapshot.context, snapshot.preparation), false)
    assert.throws(() => buildClinicalVignette(snapshot.context, snapshot.preparation))
  }
})

test('review remains optional, needs the physician capability, and is unavailable once ready or terminal', () => {
  const { context, preparation } = clinicalFixture()
  const capabilities = ['VIEW_PREPARATION', 'CONFIRM_READY_FOR_CONSULTATION']
  assert.equal(canReviewClinicalReason(context, preparation, capabilities), true)
  assert.equal(canReviewClinicalReason(context, preparation, ['VIEW_PREPARATION']), false)
  context.facts[0].review = { status: 'CONFIRMED', reviewedAt: TIME }
  assert.equal(canReviewClinicalReason(context, preparation, capabilities), false)
  context.facts[0].review = { status: 'UNREVIEWED', reviewedAt: null }
  preparation.caseStatus = context.preparationStatus = 'READY_FOR_CONSULTATION'
  preparation.readiness.state = 'READY'
  preparation.readyConfirmation.confirmed = true
  assert.equal(clinicalSnapshotIsConsistent(context, preparation), true)
  assert.equal(canReviewClinicalReason(context, preparation, capabilities), false)
  for (const status of ['CLOSED', 'CANCELLED', 'EXPIRED']) {
    preparation.caseStatus = context.preparationStatus = status
    preparation.readiness.state = 'NOT_APPLICABLE'
    assert.equal(canReviewClinicalReason(context, preparation, capabilities), false)
  }
})

test('export requires the displayed case and revision with identical content across the two fresh projections', () => {
  const displayed = clinicalFixture()
  const fresh = structuredClone(displayed)
  fresh.context.generatedAt = '2099-01-01T10:01:00Z'
  assert.equal(clinicalExportIsCurrent(displayed, fresh, CASE_ID), true)
  fresh.preparation.revision += 1
  fresh.context.preparationRevision += 1
  assert.equal(clinicalExportIsCurrent(displayed, fresh, CASE_ID), false)
  const swapped = clinicalFixture()
  swapped.preparation.patientReference = 'another-synthetic-reference'
  assert.equal(clinicalExportIsCurrent(displayed, swapped, CASE_ID), false)
  const changedReview = clinicalFixture()
  changedReview.context.facts[0].review = { status: 'CONFIRMED', reviewedAt: TIME }
  assert.equal(clinicalExportIsCurrent(displayed, changedReview, CASE_ID), false)
})

test('case, actor, organization and capabilities are part of the sensitive operation scope', () => {
  const capabilities = ['VIEW_PREPARATION', 'CONFIRM_READY_FOR_CONSULTATION']
  const key = clinicalScopeKey('cabinet-a', CASE_ID, 42, capabilities)
  assert.ok(key)
  assert.equal(key, clinicalScopeKey('cabinet-a', CASE_ID, 42, [...capabilities].reverse()))
  assert.notEqual(key, clinicalScopeKey('cabinet-b', CASE_ID, 42, capabilities))
  assert.notEqual(key, clinicalScopeKey('cabinet-a', CASE_ID, 43, capabilities))
  assert.notEqual(key, clinicalScopeKey('cabinet-a', CASE_ID, 42, ['VIEW_PREPARATION']))
  assert.equal(clinicalScopeKey('cabinet-a', CASE_ID, 42, []), '')
})

test('an empty preparation can be exported without inventing a motif or required document', () => {
  const { context, preparation } = clinicalFixture()
  preparation.caseStatus = context.preparationStatus = 'CREATED'
  preparation.revision = context.preparationRevision = 0
  preparation.patientReason = { present: false }
  preparation.attachment = { present: false }
  preparation.tasks = []
  preparation.readiness.state = 'INCOMPLETE'
  preparation.readiness.blockers = ['PATIENT_REASON_MISSING']
  context.readiness = 'INCOMPLETE'
  context.facts = []
  context.documents = []
  context.preparationItems[0].status = 'MISSING'
  context.preparationItems[1].status = 'NOT_PROVIDED'
  assert.equal(clinicalSnapshotIsConsistent(context, preparation), true)
  assert.match(buildClinicalVignette(context, preparation), /non encore renseigné/)
  assert.match(buildClinicalVignette(context, preparation), /Aucun document transmis/)
})
