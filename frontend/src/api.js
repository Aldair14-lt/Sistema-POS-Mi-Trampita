const API_URL = import.meta.env.VITE_API_URL || ''

async function request(path, options = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  })
  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Error ${response.status}`)
  }
  if (response.status === 204) return null
  return response.json()
}

export const api = {
  list: (path) => request(path),
  create: (path, data) => request(path, { method: 'POST', body: JSON.stringify(data) }),
  update: (path, data) => request(path, { method: 'PUT', body: JSON.stringify(data) }),
  remove: (path) => request(path, { method: 'DELETE' }),
}
