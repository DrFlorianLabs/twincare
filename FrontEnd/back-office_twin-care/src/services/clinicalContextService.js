import api from './api.js'
import { getPreparation } from './preparationService.js'
import { isPreparationCaseId, normalizeOrganizationId } from './preparationPresentation.js'
import { clinicalExportIsCurrent } from './clinicalContextPresentation.js'

function clinicalPath(organizationId, caseId) {
  const organization = normalizeOrganizationId(organizationId)
  if (!organization || !isPreparationCaseId(caseId)) {
    throw new Error('Clinical context scope is invalid')
  }
  return '/api/v1/organizations/' + encodeURIComponent(organization)
    + '/preparation-cases/' + encodeURIComponent(caseId) + '/clinical-context'
}

export async function getClinicalContext(organizationId, caseId, options = {}) {
  const response = await api.get(clinicalPath(organizationId, caseId), options)
  return response.data
}

export async function reviewClinicalFact(organizationId, caseId, factId, decision, options = {}) {
  if (!isPreparationCaseId(factId) || !['CONFIRM', 'REJECT'].includes(decision)) {
    throw new Error('Clinical review input is invalid')
  }
  const response = await api.put(
    clinicalPath(organizationId, caseId) + '/facts/' + encodeURIComponent(factId) + '/review',
    { decision },
    options
  )
  if (response.data?.factId !== factId
      || response.data?.status !== (decision === 'CONFIRM' ? 'CONFIRMED' : 'REJECTED')
      || typeof response.data?.reviewedAt !== 'string'
      || !Number.isFinite(Date.parse(response.data.reviewedAt))) {
    throw new Error('Clinical review response is invalid')
  }
  return response.data
}

export async function getClinicalSnapshot(organizationId, caseId, options = {}) {
  clinicalPath(organizationId, caseId)
  const [preparation, context] = await Promise.all([
    getPreparation(organizationId, caseId, options),
    getClinicalContext(organizationId, caseId, options)
  ])
  return { preparation, context }
}

/** Each export is a new authorized read; changed content requires a new explicit user action. */
export async function prepareClinicalExport({
  organizationId, caseId, displayed, stillApplies, signal,
  readSnapshot = getClinicalSnapshot
}) {
  if (!stillApplies()) return null
  const fresh = await readSnapshot(organizationId, caseId, { signal })
  if (!stillApplies()) return null
  if (!clinicalExportIsCurrent(displayed, fresh, caseId)) {
    const error = new Error('Clinical snapshot changed')
    error.code = 'CLINICAL_SNAPSHOT_CHANGED'
    throw error
  }
  return fresh
}

/** An epoch also invalidates A→B→A navigation and refreshed rights for the same case. */
export function createClinicalScopeTracker() {
  let scope = ''
  let epoch = 0
  return Object.freeze({
    reset(nextScope = '') {
      scope = nextScope
      epoch += 1
    },
    capture() {
      return { scope, epoch }
    },
    applies(ticket) {
      return Boolean(scope) && ticket?.scope === scope && ticket?.epoch === epoch
    }
  })
}
