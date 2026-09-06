import test from 'node:test'
import assert from 'node:assert/strict'
import api from './api.js'
import {
  createClinicalScopeTracker, getClinicalSnapshot, prepareClinicalExport, reviewClinicalFact
} from './clinicalContextService.js'
import { clinicalScopeKey } from './clinicalContextPresentation.js'
import { CASE_ID, FACT_ID, TIME, clinicalFixture } from './clinicalContextFixtures.js'

test('clinical snapshot reads both projections through the configured authenticated client', async () => {
  const original = api.defaults.adapter
  const snapshot = clinicalFixture()
  const requests = []
  api.defaults.adapter = async config => {
    requests.push(config)
    return { data: config.url.endsWith('/clinical-context') ? snapshot.context : snapshot.preparation,
      status: 200, statusText: 'OK', headers: {}, config }
  }
  try {
    const result = await getClinicalSnapshot('cabinet-synthetique', CASE_ID)
    assert.equal(requests.length, 2)
    assert.ok(requests.every(request => request.method === 'get'
      && request.url.startsWith('/api/v1/organizations/cabinet-synthetique/preparation-cases/' + CASE_ID)))
    assert.deepEqual(result, snapshot)
  } finally {
    api.defaults.adapter = original
  }
})

test('clinical review sends the fact and decision once, without adding client-owned authorization claims', async () => {
  const original = api.defaults.adapter
  const requests = []
  api.defaults.adapter = async config => {
    requests.push(config)
    return { data: { factId: FACT_ID, status: 'CONFIRMED', reviewedAt: TIME },
      status: 200, statusText: 'OK', headers: {}, config }
  }
  try {
    await reviewClinicalFact('cabinet-synthetique', CASE_ID, FACT_ID, 'CONFIRM')
    assert.equal(requests.length, 1)
    assert.equal(requests[0].method, 'put')
    assert.equal(requests[0].url,
      '/api/v1/organizations/cabinet-synthetique/preparation-cases/' + CASE_ID
        + '/clinical-context/facts/' + FACT_ID + '/review')
    assert.deepEqual(JSON.parse(requests[0].data), { decision: 'CONFIRM' })
  } finally {
    api.defaults.adapter = original
  }
})

test('a review conflict is propagated exactly once and never retried automatically', async () => {
  const original = api.defaults.adapter
  let count = 0
  api.defaults.adapter = async () => {
    count += 1
    const exception = new Error('Conflict')
    exception.response = { status: 409 }
    throw exception
  }
  try {
    await assert.rejects(reviewClinicalFact('cabinet-a', CASE_ID, FACT_ID, 'REJECT'),
      exception => exception.response.status === 409)
    assert.equal(count, 1)
    await assert.rejects(reviewClinicalFact('cabinet-a', CASE_ID, FACT_ID, 'UNSUPPORTED'))
    assert.equal(count, 1)
  } finally {
    api.defaults.adapter = original
  }
})

test('review response identity and decision must match before reporting success', async () => {
  const original = api.defaults.adapter
  api.defaults.adapter = async config => ({
    data: { factId: CASE_ID, status: 'CONFIRMED', reviewedAt: TIME },
    status: 200, statusText: 'OK', headers: {}, config
  })
  try {
    await assert.rejects(reviewClinicalFact('cabinet-a', CASE_ID, FACT_ID, 'CONFIRM'))
  } finally {
    api.defaults.adapter = original
  }
})

test('export rejects changed content without another read or automatic retry', async () => {
  const displayed = clinicalFixture()
  const fresh = structuredClone(displayed)
  fresh.context.preparationRevision += 1
  fresh.preparation.revision += 1
  let reads = 0
  await assert.rejects(prepareClinicalExport({
    organizationId: 'cabinet-a', caseId: CASE_ID, displayed, stillApplies: () => true,
    readSnapshot: async () => { reads += 1; return fresh }
  }), exception => exception.code === 'CLINICAL_SNAPSHOT_CHANGED')
  assert.equal(reads, 1)
})

test('late exports cannot publish after case, organization, actor or rights change, including A to B to A', async () => {
  const snapshot = clinicalFixture()
  const capabilities = ['VIEW_PREPARATION', 'CONFIRM_READY_FOR_CONSULTATION']
  const originalScope = clinicalScopeKey('cabinet-a', CASE_ID, 42, capabilities)
  const replacements = [
    clinicalScopeKey('cabinet-b', CASE_ID, 42, capabilities),
    clinicalScopeKey('cabinet-a', FACT_ID, 42, capabilities),
    clinicalScopeKey('cabinet-a', CASE_ID, 43, capabilities),
    clinicalScopeKey('cabinet-a', CASE_ID, 42, ['VIEW_PREPARATION']),
    ''
  ]
  for (const replacement of replacements) {
    const tracker = createClinicalScopeTracker()
    tracker.reset(originalScope)
    const ticket = tracker.capture()
    let resolveRead
    const pending = prepareClinicalExport({
      organizationId: 'cabinet-a', caseId: CASE_ID, displayed: snapshot,
      stillApplies: () => tracker.applies(ticket),
      readSnapshot: () => new Promise(resolve => { resolveRead = resolve })
    })
    tracker.reset(replacement)
    tracker.reset(originalScope)
    resolveRead(snapshot)
    assert.equal(await pending, null)
    assert.equal(tracker.applies(ticket), false)
  }
})

test('an invalidated scope does not start an export request; a current scope receives a verified snapshot', async () => {
  const displayed = clinicalFixture()
  let reads = 0
  const options = {
    organizationId: 'cabinet-a', caseId: CASE_ID, displayed,
    readSnapshot: async () => { reads += 1; return structuredClone(displayed) }
  }
  assert.equal(await prepareClinicalExport({ ...options, stillApplies: () => false }), null)
  assert.equal(reads, 0)
  assert.deepEqual(await prepareClinicalExport({ ...options, stillApplies: () => true }), displayed)
  assert.equal(reads, 1)
})
