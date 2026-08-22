const INVITATION_TOKEN_LENGTH = 43
const INVITATION_TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/u

/**
 * @typedef {{ kind: 'NONE' } | { kind: 'INVALID' } | { kind: 'TOKEN', token: string | null }} InvitationCapture
 */

export const InvitationCaptureKind = Object.freeze({
  NONE: 'NONE',
  TOKEN: 'TOKEN',
  INVALID: 'INVALID',
})

/**
 * @param {{ pathname: string, search: string }} location
 * @param {Pick<History, 'replaceState'>} history
 */
function scrubSensitiveLocation(location, history) {
  const query = new URLSearchParams(location.search)
  query.delete('token')
  const remainingQuery = query.toString()
  const cleanUrl = `${location.pathname}${remainingQuery ? `?${remainingQuery}` : ''}`
  history.replaceState(null, '', cleanUrl)
}

/**
 * @param {{ pathname: string, search: string, hash: string }} location
 * @param {Pick<History, 'replaceState'>} history
 * @returns {InvitationCapture}
 */
export function captureInvitationSecret(location, history) {
  const query = new URLSearchParams(location.search)
  const hasQueryToken = query.has('token')
  const rawFragment = location.hash.startsWith('#') ? location.hash.slice(1) : location.hash

  if (hasQueryToken || rawFragment) {
    scrubSensitiveLocation(location, history)
  }

  if (hasQueryToken) {
    return Object.freeze({ kind: InvitationCaptureKind.INVALID })
  }

  if (!rawFragment) {
    return Object.freeze({ kind: InvitationCaptureKind.NONE })
  }

  const exactPrefix = 'token='
  if (!rawFragment.startsWith(exactPrefix) || rawFragment.includes('&')) {
    return Object.freeze({ kind: InvitationCaptureKind.INVALID })
  }

  const encodedToken = rawFragment.slice(exactPrefix.length)
  let token
  try {
    token = decodeURIComponent(encodedToken)
  } catch {
    return Object.freeze({ kind: InvitationCaptureKind.INVALID })
  }

  if (token.length !== INVITATION_TOKEN_LENGTH || !INVITATION_TOKEN_PATTERN.test(token)) {
    return Object.freeze({ kind: InvitationCaptureKind.INVALID })
  }

  // The caller clears this in-memory value immediately after handing it to the exchange flow.
  return { kind: InvitationCaptureKind.TOKEN, token }
}
