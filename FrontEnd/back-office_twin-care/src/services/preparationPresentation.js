const READINESS = Object.freeze({
  INCOMPLETE: { label: 'À compléter', color: 'warning', icon: 'mdi-progress-alert' },
  ACTION_REQUIRED: { label: 'Action requise', color: 'error', icon: 'mdi-alert-circle-outline' },
  REVIEWABLE: { label: 'À relire', color: 'info', icon: 'mdi-eye-check-outline' },
  READY: { label: 'Prête', color: 'success', icon: 'mdi-check-circle-outline' },
  NOT_APPLICABLE: { label: 'Terminée', color: 'grey', icon: 'mdi-archive-check-outline' }
})

const BLOCKERS = Object.freeze({
  PATIENT_REASON_MISSING: 'Motif du patient non encore renseigné',
  OPEN_PREPARATION_TASKS: 'Une ou plusieurs tâches restent ouvertes'
})

const TERMINAL_CASE_STATUSES = new Set(['CLOSED', 'EXPIRED', 'CANCELLED'])
const TASK_CREATION_CASE_STATUSES = new Set(['COLLECTING', 'READY_FOR_REVIEW', 'NEEDS_ACTION'])

const TASK_STATUS = Object.freeze({
  OPEN: 'À faire',
  COMPLETED: 'Terminée',
  CANCELLED: 'Annulée'
})

export function readinessPresentation(state) {
  return READINESS[state] || {
    label: 'État indisponible',
    color: 'error',
    icon: 'mdi-shield-alert-outline'
  }
}

export function blockerLabel(blocker) {
  return BLOCKERS[blocker] || 'Vérification professionnelle nécessaire'
}

export function isKnownBlocker(blocker) {
  return Object.hasOwn(BLOCKERS, blocker)
}

export function taskStatusLabel(status) {
  return TASK_STATUS[status] || 'État indisponible'
}

export function formatDateTime(value) {
  if (!value) return 'Échéance non renseignée'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Date indisponible'
  return new Intl.DateTimeFormat('fr-FR', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(date)
}

export function formatBytes(value) {
  if (!Number.isSafeInteger(value) || value < 0) return 'Taille indisponible'
  if (value < 1_000) return `${value} octets`
  if (value < 1_000_000) return `${(value / 1_000).toFixed(1)} Ko`
  return `${(value / 1_000_000).toFixed(1)} Mo`
}

export function isKnownReadiness(state) {
  return Object.hasOwn(READINESS, state)
}

export function isPreparationCaseId(value) {
  return typeof value === 'string'
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(value)
}

export function isPreparationTaskLabel(value) {
  if (typeof value !== 'string') return false
  const normalized = value.trim()
  return normalized.length > 0 && Array.from(normalized).length <= 240
}

export function preparationCommandPolicy(view) {
  const readiness = view?.readiness
  const blockers = readiness?.blockers
  const patientReasonPresent = view?.patientReason?.present === true
  const confirmationPresent = view?.readyConfirmation?.confirmed === true
  const status = view?.caseStatus
  const openTaskCount = view?.openTaskCount

  if (readiness?.ruleSetId !== 'CORE_PREPARATION'
      || readiness?.ruleSetVersion !== 1
      || readiness?.provenance !== 'DERIVED_DETERMINISTIC'
      || !isKnownReadiness(readiness?.state)
      || !Array.isArray(blockers)
      || blockers.some(blocker => !isKnownBlocker(blocker))
      || new Set(blockers).size !== blockers.length
      || !Number.isSafeInteger(openTaskCount)
      || openTaskCount < 0
      || typeof view?.patientReason?.present !== 'boolean'
      || typeof view?.readyConfirmation?.confirmed !== 'boolean') {
    return frozenCommandPolicy(false, false, false, false)
  }

  const blockerSet = new Set(blockers)
  const onlyReasonMissing = blockers.length === 1 && blockerSet.has('PATIENT_REASON_MISSING')
  const onlyOpenTasks = blockers.length === 1 && blockerSet.has('OPEN_PREPARATION_TASKS')
  const noBlockers = blockers.length === 0

  const consistent = (
    readiness.state === 'INCOMPLETE'
      && (status === 'CREATED' || status === 'PATIENT_STARTED')
      && !patientReasonPresent
      && openTaskCount === 0
      && onlyReasonMissing
      && !confirmationPresent
  ) || (
    readiness.state === 'ACTION_REQUIRED'
      && status === 'NEEDS_ACTION'
      && patientReasonPresent
      && openTaskCount > 0
      && onlyOpenTasks
      && !confirmationPresent
  ) || (
    readiness.state === 'REVIEWABLE'
      && (status === 'COLLECTING' || status === 'READY_FOR_REVIEW')
      && patientReasonPresent
      && openTaskCount === 0
      && noBlockers
      && !confirmationPresent
  ) || (
    readiness.state === 'READY'
      && status === 'READY_FOR_CONSULTATION'
      && patientReasonPresent
      && openTaskCount === 0
      && noBlockers
      && confirmationPresent
  ) || (
    readiness.state === 'NOT_APPLICABLE'
      && TERMINAL_CASE_STATUSES.has(status)
      && noBlockers
  )

  if (!consistent) return frozenCommandPolicy(false, false, false, false)

  return frozenCommandPolicy(
    true,
    TASK_CREATION_CASE_STATUSES.has(status) && !confirmationPresent,
    status === 'NEEDS_ACTION' && openTaskCount > 0 && !confirmationPresent,
    readiness.state === 'REVIEWABLE'
  )
}

function frozenCommandPolicy(trusted, createTask, changeTaskStatus, confirmReady) {
  return Object.freeze({ trusted, createTask, changeTaskStatus, confirmReady })
}

export function professionalErrorKind(error) {
  return new Map([
    [401, 'UNAUTHENTICATED'],
    [403, 'FORBIDDEN'],
    [404, 'NOT_FOUND'],
    [409, 'CONFLICT'],
    [503, 'UNAVAILABLE']
  ]).get(error?.response?.status) || 'UNKNOWN'
}

export function preparationScopeKey(organizationId, caseId) {
  return typeof organizationId === 'string' && isPreparationCaseId(caseId)
    ? `${organizationId}\u0000${caseId}`
    : ''
}

export function scopedResultApplies(startedScope, currentScope, startedEpoch, currentEpoch) {
  return Boolean(startedScope)
    && startedScope === currentScope
    && Number.isSafeInteger(startedEpoch)
    && startedEpoch === currentEpoch
}

export function normalizeOrganizationId(value) {
  if (typeof value !== 'string') return ''
  const normalized = value.trim()
  if (!normalized || normalized.length > 128 || /[\u0000-\u001F\u007F]/u.test(normalized)) return ''
  return normalized
}

export function safeDownloadFilename(value, fallback = 'document-preparation') {
  const source = typeof value === 'string'
    ? value
    : typeof fallback === 'string' ? fallback : 'document-preparation'
  const safe = source.replace(/[\u0000-\u001F\u007F/\\:]/gu, '_').trim().slice(0, 180)
  return safe || 'document-preparation'
}

export function filenameFromContentDisposition(header, fallback) {
  if (typeof header !== 'string') return safeDownloadFilename(fallback)
  const encoded = header.match(/filename\*=UTF-8''([^;]+)/iu)
  if (encoded) {
    try {
      return safeDownloadFilename(decodeURIComponent(encoded[1]), fallback)
    } catch {
      return safeDownloadFilename(fallback)
    }
  }
  const quoted = header.match(/filename="([^"]+)"/iu)
  return safeDownloadFilename(quoted?.[1], fallback)
}
