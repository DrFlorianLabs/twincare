import test from 'node:test'
import assert from 'node:assert/strict'

import api from './api.js'
import { closePreparationCase } from './preparationService.js'

const CASE_ID = '2f3de63b-692d-4c20-8a48-c7ec01b95b21'

test('case closure sends one versioned request to the organization-scoped endpoint', async () => {
  const originalAdapter = api.defaults.adapter
  const requests = []
  api.defaults.adapter = async config => {
    requests.push(config)
    return {
      data: {
        caseStatus: 'CLOSED',
        revision: 6,
        closedByUserId: 42,
        closedAt: '2026-08-21T14:00:00Z',
        readyCaseRevision: 5
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config
    }
  }

  try {
    const result = await closePreparationCase('cabinet-a', CASE_ID, 5)
    assert.equal(requests.length, 1)
    assert.equal(requests[0].method, 'put')
    assert.equal(
      requests[0].url,
      `/api/v1/organizations/cabinet-a/preparation-cases/${CASE_ID}/closure`
    )
    assert.deepEqual(JSON.parse(requests[0].data), { expectedRevision: 5 })
    assert.equal(result.caseStatus, 'CLOSED')
    assert.equal(result.revision, 6)
  } finally {
    api.defaults.adapter = originalAdapter
  }
})

test('case closure never retries a conflict automatically', async () => {
  const originalAdapter = api.defaults.adapter
  let requestCount = 0
  api.defaults.adapter = async config => {
    requestCount += 1
    const error = new Error('Conflict')
    error.response = { status: 409, config }
    throw error
  }

  try {
    await assert.rejects(closePreparationCase('cabinet-a', CASE_ID, 5))
    assert.equal(requestCount, 1)
  } finally {
    api.defaults.adapter = originalAdapter
  }
})

test('case closure rejects every invalid revision before any HTTP request', async () => {
  const originalAdapter = api.defaults.adapter
  let requestCount = 0
  api.defaults.adapter = async config => {
    requestCount += 1
    return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
  }

  try {
    for (const revision of [0, -1, 1.5, Number.NaN, null, undefined]) {
      await assert.rejects(closePreparationCase('cabinet-a', CASE_ID, revision))
    }
    assert.equal(requestCount, 0)
  } finally {
    api.defaults.adapter = originalAdapter
  }
})
