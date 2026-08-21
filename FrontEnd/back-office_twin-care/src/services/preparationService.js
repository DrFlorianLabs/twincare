import api from './api'
import {
  filenameFromContentDisposition,
  normalizeOrganizationId,
  safeDownloadFilename
} from './preparationPresentation'

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
