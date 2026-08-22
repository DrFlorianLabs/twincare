import { professionalErrorKind } from './preparationPresentation.js'

export const PREPARATION_CLOSURE_OUTCOME = Object.freeze({
  CLOSED: 'CLOSED',
  CONFLICT: 'CONFLICT',
  STALE: 'STALE',
  ALREADY_RUNNING: 'ALREADY_RUNNING'
})

export function createPreparationClosureExecutor(closeCase) {
  if (typeof closeCase !== 'function') {
    throw new Error('Case closure function is required')
  }

  const running = new Set()

  return async function executePreparationClosure({
    scope,
    organizationId,
    caseId,
    expectedRevision,
    stillApplies,
    refresh
  }) {
    if (!scope || typeof stillApplies !== 'function' || typeof refresh !== 'function') {
      throw new Error('Case closure execution context is invalid')
    }

    const executionKey = JSON.stringify([scope, expectedRevision])
    if (running.has(executionKey)) {
      return PREPARATION_CLOSURE_OUTCOME.ALREADY_RUNNING
    }

    running.add(executionKey)
    try {
      try {
        await closeCase(organizationId, caseId, expectedRevision)
      } catch (error) {
        if (!stillApplies()) return PREPARATION_CLOSURE_OUTCOME.STALE
        if (professionalErrorKind(error) !== 'CONFLICT') throw error
        await refresh()
        return stillApplies()
          ? PREPARATION_CLOSURE_OUTCOME.CONFLICT
          : PREPARATION_CLOSURE_OUTCOME.STALE
      }

      if (!stillApplies()) return PREPARATION_CLOSURE_OUTCOME.STALE
      await refresh()
      return stillApplies()
        ? PREPARATION_CLOSURE_OUTCOME.CLOSED
        : PREPARATION_CLOSURE_OUTCOME.STALE
    } finally {
      running.delete(executionKey)
    }
  }
}
