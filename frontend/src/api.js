export const API_URL = import.meta.env.VITE_API_URL || ''

export class ApiError extends Error {
  constructor(message, status) { super(message); this.status = status }
}
async function request(path, options = {}) {
  let response
  try {
    const headers = { 'Content-Type': 'application/json', ...options.headers }
    // Solicitar el token actual también cubre la rotación de sesión al iniciar/cerrar sesión.
    if (options.method && options.method !== 'GET') {
      const csrfResponse = await fetch(`${API_URL}/api/auth/csrf`, { credentials: 'include' })
      if (!csrfResponse.ok) throw new ApiError('No se pudo validar la sesión. Vuelve a ingresar.', csrfResponse.status)
      const csrf = await csrfResponse.json()
      headers[csrf.headerName] = csrf.token
    }
    response = await fetch(`${API_URL}${path}`, { ...options, credentials: 'include', headers })
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError('No se pudo conectar con el servidor. Verifica la conexión e inténtalo de nuevo.', 0)
  }
  if (!response.ok) {
    const body = await response.text()
    let message = `No se pudo completar la operación (HTTP ${response.status}).`
    try { const parsed = JSON.parse(body); message = parsed.message || parsed.error || message } catch { /* Respuesta sin JSON. */ }
    if (response.status === 401 && !path.startsWith('/api/auth/')) window.dispatchEvent(new Event('pos-session-expired'))
    throw new ApiError(message, response.status)
  }
  if (response.status === 204) return null
  return response.json()
}
export const api = {
  list: (path) => request(path),
  create: (path, data) => request(path, { method: 'POST', body: JSON.stringify(data) }),
  update: (path, data) => request(path, { method: 'PUT', body: JSON.stringify(data) }),
  patch: (path, data) => request(path, { method: 'PATCH', body: JSON.stringify(data) }),
  remove: (path) => request(path, { method: 'DELETE' }),
}