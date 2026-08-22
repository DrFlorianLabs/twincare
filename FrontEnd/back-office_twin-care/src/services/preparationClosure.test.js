import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createPreparationClosureExecutor,
  PREPARATION_CLOSURE_OUTCOME
} from './preparationClosure.js'

const CASE_ID = '2f3de63b-692d-4c20-8a48-c7ec01b95b21'
const SCOPE = `cabinet-a\u0000${CASE_ID}`

function execution(overrides = {}) {
  return {
    scope: SCOPE,
    organizationId: 'cabinet-a',
    caseId: CASE_ID,
    expectedRevision: 5,
    stillApplies: () => true,
    refresh: async () => {},
    ...overrides
  }
}

test('successful closure refreshes once and reports the terminal outcome', async () => {
  let closeCount = 0
  let refreshCount = 0
  const execute = createPreparationClosureExecutor(async () => {
    closeCount += 1
  })

  const outcome = await execute(execution({
    refresh: async () => { refreshCount += 1 }
  }))

  assert.equal(outcome, PREPARATION_CLOSURE_OUTCOME.CLOSED)
  assert.equal(closeCount, 1)
  assert.equal(refreshCount, 1)
})

test('conflict refreshes once without retrying the closure command', async () => {
  let closeCount = 0
  let refreshCount = 0
  const execute = createPreparationClosureExecutor(async () => {
    closeCount += 1
    const error = new Error('Conflict')
    error.response = { status: 409 }
    throw error
  })

  const outcome = await execute(execution({
    refresh: async () => { refreshCount += 1 }
  }))

  assert.equal(outcome, PREPARATION_CLOSURE_OUTCOME.CONFLICT)
  assert.equal(closeCount, 1)
  assert.equal(refreshCount, 1)
})

test('a duplicate confirmation for the same scope and revision is ignored', async () => {
  let resolveClosure
  let closeCount = 0
  const closePending = new Promise(resolve => { resolveClosure = resolve })
  const execute = createPreparationClosureExecutor(async () => {
    closeCount += 1
    await closePending
  })

  const first = execute(execution())
  const duplicate = await execute(execution())
  assert.equal(duplicate, PREPARATION_CLOSURE_OUTCOME.ALREADY_RUNNING)
  assert.equal(closeCount, 1)

  resolveClosure()
  assert.equal(await first, PREPARATION_CLOSURE_OUTCOME.CLOSED)
})

test('a response for a stale case scope never refreshes the current view', async () => {
  let refreshCount = 0
  const execute = createPreparationClosureExecutor(async () => {})

  const outcome = await execute(execution({
    stillApplies: () => false,
    refresh: async () => { refreshCount += 1 }
  }))

  assert.equal(outcome, PREPARATION_CLOSURE_OUTCOME.STALE)
  assert.equal(refreshCount, 0)
})
