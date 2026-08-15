import axios from 'axios'

const configuredBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').trim()

const api = axios.create({
  baseURL: configuredBaseUrl.replace(/\/$/, ''),
  headers: {
    Accept: 'application/json'
  }
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export async function openProtectedDocument(documentId) {
  if (!Number.isInteger(Number(documentId))) {
    throw new Error('Invalid document identifier')
  }

  const previewWindow = window.open('', '_blank')
  if (previewWindow) {
    previewWindow.opener = null
  }

  try {
    const response = await api.get(`/api/documents/${documentId}/content`, {
      responseType: 'blob',
      headers: { Accept: 'application/pdf,image/jpeg,image/png' }
    })

    const objectUrl = URL.createObjectURL(response.data)
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

export default api
