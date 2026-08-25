const API_URL = import.meta.env.VITE_API_URL || ''

async function request(path, options = {}) {
  let response
  try {
    response = await fetch(`${API_URL}${path}`, {
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      ...options,
    })
  } catch {
    throw new Error('No se pudo conectar con el backend. Verifica que esté ejecutándose en el puerto 9090.')
  }
  if (!response.ok) {
    const body = await response.text()
    let message = body
    try {
      const parsed = JSON.parse(body)
      message = parsed.message || parsed.error || body
    } catch { /* La API también puede devolver texto plano. */ }
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
