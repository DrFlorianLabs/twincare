import api from './api.js'
import {
  filenameFromContentDisposition,
  isPreparationCaseId,
  normalizeOrganizationId,
  safeDownloadFilename
} from './preparationPresentation.js'
import {
  isCreationRequestId,
  isNormalizedPreparationDraft
} from './preparationStart.js'

function segment(value) {
  return encodeURIComponent(String(value))
}

function preparationPath(organizationId, suffix = '') {
  const organization = normalizeOrganizationId(organizationId)
  if (!organization) throw new Error('Organization configuration is unavailable')
  return `/api/v1/organizations/${segment(organization)}/preparation-cases${suffix}`
}

export async function authenticateProfessional(email, password) {
  const response = await api.post('/api/auth/login', { email, motDePasse: password })
  return response.data
}

export async function getProfessionalContext(options = {}) {
  const response = await api.get('/api/v1/professional-context', options)
  return response.data
}

export async function listPreparations(organizationId, page = 0, size = 20, options = {}) {
  const response = await api.get(preparationPath(organizationId), {
    ...options,
    params: { page, size }
  })
  return response.data
}

export async function getPreparation(organizationId, caseId, options = {}) {
  const response = await api.get(
    preparationPath(organizationId, `/${segment(caseId)}/preparation-view`),
    options
  )
  return response.data
}

export async function createPreparationCase(organizationId, requestId, draft) {
  if (!isCreationRequestId(requestId) || !isNormalizedPreparationDraft(draft)) {
    throw new Error('Preparation creation input is invalid')
  }
  const response = await api.post(preparationPath(organizationId), {
    requestId,
    ...draft
  })
  return response.data
}

export async function issuePatientInvitation(organizationId, caseId) {
  if (!isPreparationCaseId(caseId)) {
    throw new Error('Patient invitation input is invalid')
  }
  const response = await api.post(
    preparationPath(organizationId, `/${segment(caseId)}/invitations`)
  )
  return response.data
}

export async function createPreparationTask(organizationId, caseId, input) {
  const response = await api.post(
    preparationPath(organizationId, `/${segment(caseId)}/tasks`),
    input
  )
  return response.data
}

export async function closePreparationTask(organizationId, caseId, taskId, status) {
  const response = await api.put(
    preparationPath(organizationId, `/${segment(caseId)}/tasks/${segment(taskId)}/status`),
    { status }
  )
  return response.data
}

export async function confirmPreparationReady(organizationId, caseId, expectedRevision) {
  const response = await api.put(
    preparationPath(organizationId, `/${segment(caseId)}/ready-confirmation`),
    { expectedRevision }
  )
  return response.data
}

export async function closePreparationCase(organizationId, caseId, expectedRevision) {
  if (!isPreparationCaseId(caseId)
      || !Number.isSafeInteger(expectedRevision)
      || expectedRevision <= 0) {
    throw new Error('Case closure input is invalid')
  }
  const response = await api.put(
    preparationPath(organizationId, `/${segment(caseId)}/closure`),
    { expectedRevision }
  )
  return response.data
}

export async function downloadPreparationAttachment(
  organizationId,
  caseId,
  fallbackFilename,
  expectedSize,
  options = {}
) {
  const response = await api.get(
    preparationPath(organizationId, `/${segment(caseId)}/attachment`),
    {
      ...options,
      responseType: 'blob',
      headers: { Accept: 'application/pdf,image/jpeg,image/png' }
    }
  )
  const filename = filenameFromContentDisposition(
    response.headers['content-disposition'],
    safeDownloadFilename(fallbackFilename)
  )
  if (Number.isSafeInteger(expectedSize) && expectedSize >= 0 && response.data.size !== expectedSize) {
    throw new Error('Downloaded attachment size is inconsistent')
  }
  const objectUrl = URL.createObjectURL(response.data)
  try {
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = filename
    link.rel = 'noopener noreferrer'
    document.body.appendChild(link)
    link.click()
    link.remove()
  } finally {
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1_000)
  }
}
