const CONTROL_CHARACTERS = /[\u0000-\u001F\u007F]/u
const INVITATION_TOKEN = /^[A-Za-z0-9_-]{43}$/u
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu
const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/u
const POSSIBLE_CLOCK_SHIFTS_MINUTES = [30, 60, 90, 120, 150, 180]

export const PREPARATION_START_OUTCOME = Object.freeze({
  COMPLETED: 'COMPLETED',
  STALE: 'STALE',
  ALREADY_RUNNING: 'ALREADY_RUNNING'
})

function boundedText(value, maximumLength, required) {
  if (typeof value !== 'string') return null
  const normalized = value.trim()
  if ((!normalized && required)
      || normalized.length > maximumLength
      || CONTROL_CHARACTERS.test(normalized)) {
    return null
  }
  return normalized
}

export function normalizePreparationDraft(draft, now = Date.now()) {
  const patientReference = boundedText(draft?.patientReference, 128, true)
  const appointmentReference = boundedText(draft?.appointmentReference || '', 128, false)
  const scheduledAt = parseUnambiguousLocalDateTime(draft?.scheduledAt)

  if (!patientReference
      || appointmentReference === null
      || !scheduledAt
      || Number.isNaN(scheduledAt.getTime())
      || scheduledAt.getTime() <= now) {
    return null
  }

  return Object.freeze({
    patientReference,
    ...(appointmentReference ? { appointmentReference } : {}),
    scheduledAt: scheduledAt.toISOString()
  })
}

export function isNormalizedPreparationDraft(draft) {
  const patientReference = boundedText(draft?.patientReference, 128, true)
  if (!patientReference || patientReference !== draft.patientReference) return false

  const hasAppointmentReference = Object.prototype.hasOwnProperty.call(
    draft,
    'appointmentReference'
  )
  if (hasAppointmentReference) {
    const appointmentReference = boundedText(draft.appointmentReference, 128, true)
    if (!appointmentReference || appointmentReference !== draft.appointmentReference) return false
  }

  if (typeof draft.scheduledAt !== 'string') return false
  const scheduledAt = new Date(draft.scheduledAt)
  return !Number.isNaN(scheduledAt.getTime())
    && scheduledAt.toISOString() === draft.scheduledAt
}

function sameLocalMinute(date, parts) {
  return date.getFullYear() === parts.year
    && date.getMonth() + 1 === parts.month
    && date.getDate() === parts.day
    && date.getHours() === parts.hour
    && date.getMinutes() === parts.minute
}

export function parseUnambiguousLocalDateTime(value) {
  if (typeof value !== 'string') return null
  const match = LOCAL_DATE_TIME.exec(value)
  if (!match) return null
  const parts = {
    year: Number(match[1]),
    month: Number(match[2]),
    day: Number(match[3]),
    hour: Number(match[4]),
    minute: Number(match[5])
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime()) || !sameLocalMinute(parsed, parts)) return null

  for (const shiftMinutes of POSSIBLE_CLOCK_SHIFTS_MINUTES) {
    const shiftMilliseconds = shiftMinutes * 60_000
    if (sameLocalMinute(new Date(parsed.getTime() - shiftMilliseconds), parts)
        || sameLocalMinute(new Date(parsed.getTime() + shiftMilliseconds), parts)) {
      return null
    }
  }
  return parsed
}

export function localTimeZoneLabel() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'fuseau local de cet appareil'
  } catch {
    return 'fuseau local de cet appareil'
  }
}

export function isCreationRequestId(value) {
  return typeof value === 'string' && UUID.test(value)
}

export function createPreparationRequestTracker(
  createId = () => globalThis.crypto?.randomUUID?.()
) {
  if (typeof createId !== 'function') throw new Error('Request identifier factory is required')
  let requestId = ''
  let payload = null
  let ambiguous = false
  return Object.freeze({
    current() {
      if (!requestId) {
        const candidate = createId()
        if (!isCreationRequestId(candidate)) {
          throw new Error('Secure request identifier is unavailable')
        }
        requestId = candidate
      }
      return requestId
    },
    payload(candidate) {
      if (!payload) {
        if (!isNormalizedPreparationDraft(candidate)) {
          throw new Error('Normalized preparation payload is required')
        }
        payload = Object.freeze({ ...candidate })
      }
      return payload
    },
    reset() {
      requestId = ''
      payload = null
      ambiguous = false
    },
    markAmbiguous() {
      if (!requestId) throw new Error('Creation request identifier is unavailable')
      ambiguous = true
    },
    isAmbiguous() {
      return ambiguous
    }
  })
}

function createSingleFlightExecutor(operation, label) {
  if (typeof operation !== 'function') throw new Error(`${label} function is required`)
  const runningScopes = new Set()

  return async function execute({ scope, stillApplies, operationArguments }) {
    if (!scope || typeof stillApplies !== 'function' || !Array.isArray(operationArguments)) {
      throw new Error(`${label} execution context is invalid`)
    }
    if (runningScopes.has(scope)) {
      return Object.freeze({ outcome: PREPARATION_START_OUTCOME.ALREADY_RUNNING })
    }

    runningScopes.add(scope)
    try {
      try {
        const result = await operation(...operationArguments)
        return stillApplies()
          ? Object.freeze({ outcome: PREPARATION_START_OUTCOME.COMPLETED, result })
          : Object.freeze({ outcome: PREPARATION_START_OUTCOME.STALE })
      } catch (error) {
        if (!stillApplies()) {
          return Object.freeze({ outcome: PREPARATION_START_OUTCOME.STALE })
        }
        throw error
      }
    } finally {
      runningScopes.delete(scope)
    }
  }
}

export function createPreparationCreationExecutor(createCase) {
  return createSingleFlightExecutor(createCase, 'Preparation creation')
}

export function createPatientInvitationExecutor(issueInvitation) {
  return createSingleFlightExecutor(issueInvitation, 'Patient invitation')
}

export function patientPreparationBaseUrl(value) {
  if (typeof value !== 'string' || !value.trim()) return ''
  const candidate = value.trim()
  if (candidate.includes('?') || candidate.includes('#')) return ''
  try {
    const url = new URL(candidate)
    const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
    if (!(url.protocol === 'https:' || (url.protocol === 'http:' && loopback))
        || url.username
        || url.password
        || url.search
        || url.hash) {
      return ''
    }
    return url.toString()
  } catch {
    return ''
  }
}

export function buildPatientInvitationLink(baseUrl, invitationToken) {
  const normalizedBase = patientPreparationBaseUrl(baseUrl)
  if (!normalizedBase
      || typeof invitationToken !== 'string'
      || !INVITATION_TOKEN.test(invitationToken)) {
    throw new Error('Patient invitation link input is invalid')
  }
  const url = new URL(normalizedBase)
  url.hash = `token=${invitationToken}`
  return url.toString()
}

export async function copyPatientInvitationLink(link, clipboard) {
  if (typeof link !== 'string'
      || link.includes('?')
      || link.indexOf('#') !== link.lastIndexOf('#')) {
    throw new Error('Patient invitation link is invalid')
  }
  let url
  try {
    url = new URL(link)
  } catch {
    throw new Error('Patient invitation link is invalid')
  }
  const token = url.hash.startsWith('#token=') ? url.hash.slice(7) : ''
  const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
  if (!(url.protocol === 'https:' || (url.protocol === 'http:' && loopback))
      || url.username
      || url.password
      || url.search
      || !INVITATION_TOKEN.test(token)) {
    throw new Error('Patient invitation link is invalid')
  }
  if (!clipboard || typeof clipboard.writeText !== 'function') {
    throw new Error('Clipboard is unavailable')
  }
  await clipboard.writeText(link)
}
