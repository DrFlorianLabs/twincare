const configuredBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').trim()
const API_BASE_URL = configuredBaseUrl.replace(/\/$/, '')

export function apiUrl(path) {
  if (!path.startsWith('/')) {
    throw new Error('API path must start with /')
  }
  return `${API_BASE_URL}${path}`
}

export function authHeaders(headers = {}) {
  const token = localStorage.getItem('token')
  return {
    ...headers,
    ...(token ? { Authorization: `Bearer ${token}` } : {})
  }
}

export async function authFetch(path, options = {}) {
  return fetch(apiUrl(path), {
    ...options,
    headers: authHeaders(options.headers || {})
  })
}

export async function openProtectedDocument(documentId) {
  if (!Number.isInteger(Number(documentId))) {
    throw new Error('Invalid document identifier')
  }

  // Open synchronously from the user gesture to avoid popup blockers while the
  // authenticated request is still in flight.
  const previewWindow = window.open('', '_blank')
  if (previewWindow) {
    previewWindow.opener = null
  }

  try {
    const response = await authFetch(`/api/documents/${documentId}/content`, {
      method: 'GET',
      headers: { Accept: 'application/pdf,image/jpeg,image/png' }
    })

    if (!response.ok) {
      throw new Error(`Document request failed with status ${response.status}`)
    }

    const blob = await response.blob()
    const objectUrl = URL.createObjectURL(blob)

    if (previewWindow) {
      previewWindow.location.replace(objectUrl)
    } else {
      const link = document.createElement('a')
      link.href = objectUrl
      link.target = '_blank'
      link.rel = 'noopener noreferrer'
      link.click()
    }

    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000)
  } catch (error) {
    if (previewWindow && !previewWindow.closed) {
      previewWindow.close()
    }
    throw error
  }
}
