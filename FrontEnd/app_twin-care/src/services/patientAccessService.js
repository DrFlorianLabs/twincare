const PATIENT_ACCESS_PATH = '/api/v1/patient-access'
const DEFAULT_REQUEST_TIMEOUT_MS = 12_000

/**
 * @typedef {'NETWORK' | 'UNAUTHORIZED' | 'UNEXPECTED_RESPONSE'} PatientAccessErrorKindValue
 * @typedef {{ expiresAt: string }} PatientSession
 * @typedef {{ present: false } | { present: true, reason: string, recordedAt: string }} PatientReason
 * @typedef {Object} PatientAccessClient
 * @property {(token: string) => Promise<void>} exchangeInvitation
 * @property {() => Promise<PatientSession>} restoreSession
 * @property {() => Promise<PatientReason>} readReason
 * @property {(reason: string) => Promise<void>} saveReason
 * @property {() => void} clearSecrets
 * @typedef {Object} PatientAccessClientOptions
 * @property {typeof fetch} [fetchImplementation]
 * @property {string} [apiBase]
 * @property {number} [requestTimeoutMs]
 */

export const PatientAccessErrorKind = Object.freeze({
  NETWORK: 'NETWORK',
  UNAUTHORIZED: 'UNAUTHORIZED',
  UNEXPECTED_RESPONSE: 'UNEXPECTED_RESPONSE',
})

export class PatientAccessError extends Error {
  /** @param {PatientAccessErrorKindValue} kind */
  constructor(kind) {
    super('Patient access request failed')
    this.name = 'PatientAccessError'
    this.kind = kind
  }
}

/** @param {string | null | undefined} apiBase */
function normalizeApiBase(apiBase) {
  return (apiBase ?? '').trim().replace(/\/+$/u, '')
}

/**
 * @param {string} apiBase
 * @param {string} path
 */
function endpoint(apiBase, path) {
  return `${apiBase}${PATIENT_ACCESS_PATH}${path}`
}

/**
 * @param {typeof fetch} fetchImplementation
 * @param {string} url
 * @param {RequestInit} options
 * @param {number} requestTimeoutMs
 * @returns {Promise<Response>}
 */
async function performFetch(fetchImplementation, url, options, requestTimeoutMs) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), requestTimeoutMs)
  let response
  try {
    response = await fetchImplementation(url, {
      credentials: 'include',
      cache: 'no-store',
      signal: controller.signal,
      ...options,
    })
  } catch {
    throw new PatientAccessError(PatientAccessErrorKind.NETWORK)
  } finally {
    clearTimeout(timeout)
  }

  if (response.status === 401 || response.status === 403) {
    throw new PatientAccessError(PatientAccessErrorKind.UNAUTHORIZED)
  }
  if (response.status === 408 || response.status === 429 || response.status >= 500) {
    throw new PatientAccessError(PatientAccessErrorKind.NETWORK)
  }
  if (!response.ok) {
    throw new PatientAccessError(PatientAccessErrorKind.UNEXPECTED_RESPONSE)
  }
  return response
}

/**
 * @param {Response} response
 * @returns {Promise<Record<string, unknown>>}
 */
async function safeJson(response) {
  try {
    const body = await response.json()
    if (body === null || typeof body !== 'object' || Array.isArray(body)) {
      throw new PatientAccessError(PatientAccessErrorKind.UNEXPECTED_RESPONSE)
    }
    return /** @type {Record<string, unknown>} */ (body)
  } catch {
    throw new PatientAccessError(PatientAccessErrorKind.UNEXPECTED_RESPONSE)
  }
}

/**
 * @param {PatientAccessClientOptions} [options]
 * @returns {Readonly<PatientAccessClient>}
 */
export function createPatientAccessClient({
  fetchImplementation = globalThis.fetch,
  apiBase = import.meta.env?.VITE_API_BASE_URL,
  requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS,
} = {}) {
  if (typeof fetchImplementation !== 'function') {
    throw new TypeError('A fetch implementation is required')
  }
  if (!Number.isSafeInteger(requestTimeoutMs) || requestTimeoutMs <= 0) {
    throw new TypeError('A positive request timeout is required')
  }

  const normalizedApiBase = normalizeApiBase(apiBase)
  /** @type {string | null} */
  let csrfToken = null

  /** @type {PatientAccessClient} */
  const client = {
    /** @param {string} token */
    async exchangeInvitation(token) {
      await performFetch(
        fetchImplementation,
        endpoint(normalizedApiBase, '/exchange'),
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ token }),
        },
        requestTimeoutMs,
      )
    },

    async restoreSession() {
      const response = await performFetch(
        fetchImplementation,
        endpoint(normalizedApiBase, '/session'),
        { method: 'GET' },
        requestTimeoutMs,
      )
      const nextCsrfToken = response.headers.get('X-TC-CSRF')
      const body = await safeJson(response)
      if (!nextCsrfToken || body?.status !== 'ACTIVE' || typeof body.expiresAt !== 'string') {
        csrfToken = null
        throw new PatientAccessError(PatientAccessErrorKind.UNEXPECTED_RESPONSE)
      }
      csrfToken = nextCsrfToken
      return Object.freeze({ expiresAt: body.expiresAt })
    },

    async readReason() {
      const response = await performFetch(
        fetchImplementation,
        endpoint(normalizedApiBase, '/reason'),
        { method: 'GET' },
        requestTimeoutMs,
      )
      const body = await safeJson(response)
      if (body?.present === false) {
        return Object.freeze({ present: false })
      }
      if (
        body?.present !== true ||
        typeof body.reason !== 'string' ||
        typeof body.recordedAt !== 'string'
      ) {
        throw new PatientAccessError(PatientAccessErrorKind.UNEXPECTED_RESPONSE)
      }
      return Object.freeze({
        present: true,
        reason: body.reason,
        recordedAt: body.recordedAt,
      })
    },

    /** @param {string} reason */
    async saveReason(reason) {
      if (!csrfToken) {
        throw new PatientAccessError(PatientAccessErrorKind.UNAUTHORIZED)
      }
      await performFetch(
        fetchImplementation,
        endpoint(normalizedApiBase, '/reason'),
        {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            'X-TC-CSRF': csrfToken,
          },
          body: JSON.stringify({ reason }),
        },
        requestTimeoutMs,
      )
    },

    clearSecrets() {
      csrfToken = null
    },
  }
  return Object.freeze(client)
}
