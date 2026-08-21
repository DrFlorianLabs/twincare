import axios from 'axios'

const configuredBaseUrl = (import.meta.env?.VITE_API_BASE_URL || '').trim()
const SESSION_TOKEN_KEY = 'twincare.professional-token'

const api = axios.create({
  baseURL: configuredBaseUrl.replace(/\/$/, ''),
  headers: {
    Accept: 'application/json'
  }
})

api.interceptors.request.use((config) => {
  const token = professionalToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function browserStorage(name = 'sessionStorage') {
  if (typeof window === 'undefined') return null
  try {
    return window[name]
  } catch {
    return null
  }
}

export function professionalToken(storage = browserStorage()) {
  try {
    return storage?.getItem(SESSION_TOKEN_KEY) || ''
  } catch {
    return ''
  }
}

export function storeProfessionalSession(token, storage = browserStorage()) {
  if (typeof token !== 'string' || !token.trim()) {
    throw new Error('Authentication token is unavailable')
  }
  if (!storage) throw new Error('Browser session storage is unavailable')
  storage.setItem(SESSION_TOKEN_KEY, token)
}

export function clearProfessionalSession(
  storage = browserStorage(),
  legacyStorage = browserStorage('localStorage')
) {
  try {
    storage?.removeItem(SESSION_TOKEN_KEY)
  } catch {
    // A blocked storage API is equivalent to an already-cleared session.
  }
  try {
    // Remove historical prototype keys without ever reusing their client-side identity.
    legacyStorage?.removeItem('token')
    legacyStorage?.removeItem('role')
    legacyStorage?.removeItem('userId')
  } catch {
    // Historical storage is never required by the active application.
  }
}

export default api
