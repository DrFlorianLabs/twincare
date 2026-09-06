import {
  formatDateTime,
  isKnownPreparationCaseStatus,
  isPreparationCaseId,
  normalizeOrganizationId,
  preparationCommandPolicy,
  readinessPresentation,
  taskStatusLabel
} from './preparationPresentation.js'

const PROVENANCE = Object.freeze({
  PATIENT_DECLARED: 'Déclaré par le patient',
  DOCUMENT_EXTRACTED: 'Extrait d’un document',
  DOCUMENT_VERIFIED: 'Vérifié dans un document',
  HUMAN_ENTERED: 'Saisi par un professionnel',
  IMPORTED_CONTEXT: 'Contexte importé',
  DERIVED_DETERMINISTIC: 'Calcul déterministe',
  AI_PROPOSED: 'Proposé par une IA'
})
const CERTAINTY = Object.freeze({
  NOT_ASSESSED: 'Certitude non évaluée',
  ASSERTED: 'Affirmé par la source',
  EXPRESSED_AS_UNCERTAIN: 'Incertitude exprimée par la source'
})
const REVIEW = Object.freeze({
  UNREVIEWED: 'Non relu',
  CONFIRMED: 'Confirmé après relecture',
  REJECTED: 'Rejeté après relecture'
})
const MIME_TYPES = Object.freeze({
  PDF: 'application/pdf',
  JPEG: 'image/jpeg',
  PNG: 'image/png'
})
const REVIEWABLE_STATUSES = new Set(['COLLECTING', 'READY_FOR_REVIEW', 'NEEDS_ACTION'])

export const CLINICAL_CONTEXT_SCHEMA = 'twincare.clinical-context.v1'
export const PREPARATION_EXPORT_SCHEMA = 'twincare.preparation-export.v1'

export function provenanceLabel(value) {
  return PROVENANCE[value] || 'Source inconnue'
}
export function certaintyLabel(value) {
  return CERTAINTY[value] || 'Certitude inconnue'
}
export function reviewLabel(value) {
  return REVIEW[value] || 'Relecture inconnue'
}
export function clinicalDate(value) {
  return value ? formatDateTime(value) : 'Non renseignée'
}

function timestamp(value) {
  return typeof value === 'string' && Number.isFinite(Date.parse(value))
}
function sameTimestamp(left, right) {
  return left === right || (timestamp(left) && timestamp(right) && Date.parse(left) === Date.parse(right))
}
function validRevision(value) {
  return Number.isSafeInteger(value) && value >= 0
}
function nonemptyText(value) {
  return typeof value === 'string' && Boolean(value.trim()) && !value.includes('\0')
}

export function clinicalScopeKey(organizationId, caseId, actorUserId, capabilities) {
  const organization = normalizeOrganizationId(organizationId)
  if (!organization || !isPreparationCaseId(caseId)
      || !Number.isSafeInteger(actorUserId) || actorUserId <= 0
      || !Array.isArray(capabilities) || !capabilities.includes('VIEW_PREPARATION')
      || capabilities.some(value => typeof value !== 'string')) return ''
  return JSON.stringify([organization, caseId, actorUserId, [...new Set(capabilities)].sort()])
}

export function currentClinicalReason(context) {
  return context?.facts?.find(fact =>
    fact?.kind === 'CONSULTATION_REASON' && fact.lifecycle?.status === 'CURRENT') || null
}

export function historicalClinicalReasons(context) {
  return (context?.facts || []).filter(fact => fact.lifecycle?.status === 'SUPERSEDED')
    .slice().sort((a, b) => Date.parse(b.temporal.recordedAt) - Date.parse(a.temporal.recordedAt))
}

function validFact(fact) {
  if (!isPreparationCaseId(fact?.id) || fact.kind !== 'CONSULTATION_REASON'
      || !nonemptyText(fact.value?.text) || fact.value.text.length > 4000
      || !Object.hasOwn(PROVENANCE, fact.provenance?.type)
      || !Object.hasOwn(CERTAINTY, fact.certainty)
      || !timestamp(fact.temporal?.recordedAt)
      || !Object.hasOwn(REVIEW, fact.review?.status)) return false
  const { recordedAt, effectiveFrom, effectiveTo } = fact.temporal
  if ((effectiveFrom != null && !timestamp(effectiveFrom))
      || (effectiveTo != null && !timestamp(effectiveTo))
      || (effectiveFrom && effectiveTo && Date.parse(effectiveFrom) > Date.parse(effectiveTo))) return false
  const { status, supersededAt } = fact.lifecycle || {}
  if (status === 'CURRENT' ? supersededAt != null
    : status !== 'SUPERSEDED' || !timestamp(supersededAt)
      || Date.parse(supersededAt) < Date.parse(recordedAt)) return false
  return fact.review.status === 'UNREVIEWED'
    ? fact.review.reviewedAt == null
    : timestamp(fact.review.reviewedAt)
      && Date.parse(fact.review.reviewedAt) >= Date.parse(recordedAt)
}

function validDocuments(documents, attachment) {
  if (!Array.isArray(documents) || typeof attachment?.present !== 'boolean') return false
  if (!attachment.present) return documents.length === 0
  if (documents.length !== 1) return false
  const document = documents[0]
  return isPreparationCaseId(document?.id)
    && nonemptyText(document.displayFilename)
    && document.displayFilename === attachment.displayFilename
    && Object.hasOwn(MIME_TYPES, document.mediaType)
    && MIME_TYPES[document.mediaType] === attachment.mediaType
    && Number.isSafeInteger(document.sizeBytes) && document.sizeBytes >= 0
    && document.sizeBytes === attachment.sizeBytes
    && document.provenance === 'PATIENT_UPLOADED'
    && timestamp(document.uploadedAt) && timestamp(document.updatedAt)
    && sameTimestamp(document.uploadedAt, attachment.uploadedAt)
    && sameTimestamp(document.updatedAt, attachment.updatedAt)
}

function validTasks(preparation) {
  const tasks = preparation?.tasks
  return Array.isArray(tasks)
    && tasks.every(task => isPreparationCaseId(task?.id) && nonemptyText(task.label)
      && ['OPEN', 'COMPLETED', 'CANCELLED'].includes(task.status)
      && timestamp(task.createdAt) && timestamp(task.updatedAt))
    && new Set(tasks.map(task => task.id)).size === tasks.length
    && tasks.filter(task => task.status === 'OPEN').length === preparation.openTaskCount
}

/** Fail closed when the two API projections cannot describe the same visible case revision. */
export function clinicalSnapshotIsConsistent(context, preparation) {
  if (context?.schemaVersion !== CLINICAL_CONTEXT_SCHEMA
      || !isPreparationCaseId(preparation?.caseId) || context.contextId !== preparation.caseId
      || !nonemptyText(preparation.patientReference)
      || !validRevision(preparation.revision)
      || context.preparationRevision !== preparation.revision
      || !isKnownPreparationCaseStatus(preparation.caseStatus)
      || context.preparationStatus !== preparation.caseStatus
      || !sameTimestamp(context.scheduledAt, preparation.scheduledAt)
      || !timestamp(context.generatedAt)
      || !Array.isArray(context.facts) || !context.facts.every(validFact)
      || new Set(context.facts.map(fact => fact.id)).size !== context.facts.length
      || !validDocuments(context.documents, preparation.attachment)
      || !validTasks(preparation)
      || !preparationCommandPolicy(preparation).trusted) return false

  const current = context.facts.filter(fact => fact.lifecycle.status === 'CURRENT')
  if (current.length > 1 || preparation.patientReason?.present !== (current.length === 1)) return false
  const reason = current[0]
  if (reason && (preparation.patientReason.text !== reason.value.text
      || preparation.patientReason.provenance !== reason.provenance.type
      || !sameTimestamp(preparation.patientReason.recordedAt, reason.temporal.recordedAt))) return false

  const expectedReadiness = !reason ? 'INCOMPLETE'
    : reason.review.status === 'REJECTED' ? 'NEEDS_CORRECTION' : 'COMPLETE'
  if (context.readiness !== expectedReadiness || !Array.isArray(context.preparationItems)
      || context.preparationItems.length !== 2) return false
  const reasonItem = context.preparationItems.find(item => item.code === 'CONSULTATION_REASON')
  const documentItem = context.preparationItems.find(item => item.code === 'PATIENT_ATTACHMENT')
  return reasonItem?.requirement === 'REQUIRED'
    && reasonItem.status === (!reason ? 'MISSING'
      : reason.review.status === 'REJECTED' ? 'NEEDS_CORRECTION' : 'COMPLETED')
    && documentItem?.requirement === 'OPTIONAL'
    && documentItem.status === (context.documents.length ? 'AVAILABLE' : 'NOT_PROVIDED')
}

export function canReviewClinicalReason(context, preparation, capabilities) {
  const reason = currentClinicalReason(context)
  return clinicalSnapshotIsConsistent(context, preparation)
    && Array.isArray(capabilities) && capabilities.includes('CONFIRM_READY_FOR_CONSULTATION')
    && REVIEWABLE_STATUSES.has(preparation.caseStatus)
    && reason?.review.status === 'UNREVIEWED'
}

/** Compare the displayed snapshot with fresh responses immediately before an explicit export. */
export function clinicalExportIsCurrent(displayed, fresh, caseId) {
  return displayed?.preparation?.caseId === caseId
    && fresh?.preparation?.caseId === caseId
    && clinicalSnapshotIsConsistent(displayed?.context, displayed?.preparation)
    && clinicalSnapshotIsConsistent(fresh?.context, fresh?.preparation)
    && displayed.preparation.revision === fresh.preparation.revision
    && displayed.preparation.patientReference === fresh.preparation.patientReference
    && JSON.stringify(displayed.preparation) === JSON.stringify(fresh.preparation)
    && JSON.stringify({ ...displayed.context, generatedAt: null })
      === JSON.stringify({ ...fresh.context, generatedAt: null })
}

export function buildClinicalVignette(context, preparation) {
  if (!clinicalSnapshotIsConsistent(context, preparation)) {
    throw new Error('Clinical snapshot is inconsistent')
  }
  const reason = currentClinicalReason(context)
  const lines = [
    'PRÉPARATION DE CONSULTATION — TwinCare',
    'Référence patient non vérifiée : ' + preparation.patientReference,
    'Consultation : ' + clinicalDate(preparation.scheduledAt),
    'Préparation : ' + readinessPresentation(preparation.readiness.state).label,
    ''
  ]
  if (!reason) {
    lines.push('Motif : non encore renseigné.')
  } else if (reason.review.status === 'REJECTED') {
    // The rejected wording must not be propagated as a clinical fact to the practice software.
    lines.push('Motif : à corriger après relecture ; contenu rejeté exclu de cette vignette.')
  } else {
    lines.push('Motif : ' + reason.value.text,
      'Source : ' + provenanceLabel(reason.provenance.type),
      'Recueilli le : ' + clinicalDate(reason.temporal.recordedAt),
      'Certitude : ' + certaintyLabel(reason.certainty),
      'Relecture : ' + reviewLabel(reason.review.status)
        + (reason.review.reviewedAt ? ' le ' + clinicalDate(reason.review.reviewedAt) : ''))
    if (reason.temporal.effectiveFrom) lines.push('Début rapporté : ' + clinicalDate(reason.temporal.effectiveFrom))
    if (reason.temporal.effectiveTo) lines.push('Fin rapportée : ' + clinicalDate(reason.temporal.effectiveTo))
  }
  lines.push('', 'Tâches de préparation :')
  if (!preparation.tasks.length) lines.push('Aucune tâche renseignée.')
  preparation.tasks.forEach(task => lines.push('- ' + taskStatusLabel(task.status) + ' : ' + task.label))
  lines.push('', 'Documents transmis :')
  if (!context.documents.length) lines.push('Aucun document transmis.')
  context.documents.forEach(document => lines.push('- ' + document.displayFilename
    + ' — déposé par le patient le ' + clinicalDate(document.uploadedAt) + ' ; contenu non analysé.'))
  lines.push('', 'Vignette issue des informations recueillies ; aucune interprétation diagnostique automatisée.')
  return lines.join('\n')
}

/** A deliberately selected portable envelope; neither opaque API fields nor binary documents are exported. */
export function buildClinicalExport(context, preparation, organizationId) {
  if (!normalizeOrganizationId(organizationId) || !clinicalSnapshotIsConsistent(context, preparation)) {
    throw new Error('Clinical export input is inconsistent')
  }
  return {
    schemaVersion: PREPARATION_EXPORT_SCHEMA,
    exportedAt: context.generatedAt,
    organizationId: normalizeOrganizationId(organizationId),
    preparation: {
      caseId: preparation.caseId,
      patientReference: preparation.patientReference,
      patientReferenceVerified: false,
      appointmentReference: preparation.appointmentReference,
      scheduledAt: preparation.scheduledAt,
      status: preparation.caseStatus,
      revision: preparation.revision,
      readiness: {
        state: preparation.readiness.state, blockers: [...preparation.readiness.blockers],
        ruleSetId: preparation.readiness.ruleSetId, ruleSetVersion: preparation.readiness.ruleSetVersion,
        provenance: preparation.readiness.provenance
      },
      readyConfirmation: {
        confirmed: preparation.readyConfirmation.confirmed,
        confirmedAt: preparation.readyConfirmation.confirmedAt,
        confirmedByUserId: preparation.readyConfirmation.confirmedByUserId,
        reviewedCaseRevision: preparation.readyConfirmation.reviewedCaseRevision,
        ruleSetId: preparation.readyConfirmation.ruleSetId,
        ruleSetVersion: preparation.readyConfirmation.ruleSetVersion
      },
      tasks: preparation.tasks.map(task => ({
        id: task.id, label: task.label, status: task.status,
        createdAt: task.createdAt, updatedAt: task.updatedAt, closedAt: task.closedAt
      }))
    },
    clinicalContext: {
      schemaVersion: context.schemaVersion, contextId: context.contextId,
      preparationRevision: context.preparationRevision, preparationStatus: context.preparationStatus,
      scheduledAt: context.scheduledAt, readiness: context.readiness,
      preparationItems: context.preparationItems.map(item => ({
        code: item.code, requirement: item.requirement, status: item.status
      })),
      facts: context.facts.map(fact => ({
        id: fact.id, kind: fact.kind, value: { text: fact.value.text },
        provenance: { type: fact.provenance.type }, certainty: fact.certainty,
        temporal: { recordedAt: fact.temporal.recordedAt,
          effectiveFrom: fact.temporal.effectiveFrom, effectiveTo: fact.temporal.effectiveTo },
        lifecycle: { status: fact.lifecycle.status, supersededAt: fact.lifecycle.supersededAt },
        review: { status: fact.review.status, reviewedAt: fact.review.reviewedAt }
      })),
      documents: context.documents.map(document => ({
        id: document.id, displayFilename: document.displayFilename, mediaType: document.mediaType,
        sizeBytes: document.sizeBytes, provenance: document.provenance,
        uploadedAt: document.uploadedAt, updatedAt: document.updatedAt
      })),
      generatedAt: context.generatedAt
    }
  }
}
