export const API_URL = import.meta.env?.VITE_API_URL || ''

export class ApiError extends Error {
  constructor(message, status) { super(message); this.status = status }
}
/** El timeout incluye la lectura del cuerpo, no solo la recepción de cabeceras. */
export async function readHttp(url, options = {}, responseType = 'json') {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 30000)
  try {
    const response = await fetch(url, { ...options, signal: controller.signal })
    if (!response.ok) {
      const body = await response.text()
      let message = `No se pudo completar la operación (HTTP ${response.status}).`
      try { const parsed = JSON.parse(body); message = parsed.message || parsed.error || message } catch { /* Respuesta sin JSON. */ }
      throw new ApiError(message, response.status)
    }
    if (response.status === 204) return null
    if (responseType === 'blob') return await response.blob()
    const body = await response.text()
    const writes = ['POST', 'PUT', 'PATCH'].includes(options.method)
    if (!body && !writes) return null
    let result
    try { result = JSON.parse(body) } catch {
      // El servidor puede haber confirmado una escritura: conservar el UUID para comprobarla.
      throw new ApiError('El servidor devolvió una respuesta incompleta. Comprueba o reintenta la misma operación.', 502)
    }
    if (result == null && writes) throw new ApiError('No se pudo confirmar la respuesta. Comprueba o reintenta la misma operación.', 502)
    return result
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError('No se pudo conectar con el servidor. Verifica la conexión e inténtalo de nuevo.', 0)
  } finally { clearTimeout(timer) }
}
async function request(path, options = {}, responseType = 'json') {
  try {
    const headers = { 'Content-Type': 'application/json', ...options.headers }
    // Solicitar el token actual también cubre la rotación de sesión al iniciar/cerrar sesión.
    if (options.method && options.method !== 'GET') {
      const csrf = await readHttp(`${API_URL}/api/auth/csrf`, { credentials: 'include' })
      if (!csrf?.headerName || !csrf?.token) throw new ApiError('No se pudo validar la sesión. Vuelve a ingresar.', 502)
      headers[csrf.headerName] = csrf.token
    }
    const result = await readHttp(`${API_URL}${path}`, { ...options, credentials: 'include', headers }, responseType)
    if (result == null && ['POST', 'PUT'].includes(options.method) && path !== '/api/auth/logout')
      throw new ApiError('No se pudo confirmar la respuesta. Comprueba o reintenta la misma operación.', 502)
    return result
  } catch (error) {
    if (error.status === 401 && !path.startsWith('/api/auth/')) window.dispatchEvent(new Event('pos-session-expired'))
    throw error
  }
}
export const api = {
  download: (path) => request(path, {}, 'blob'),
  list: (path) => request(path),
  create: (path, data) => request(path, { method: 'POST', body: JSON.stringify(data) }),
  update: (path, data) => request(path, { method: 'PUT', body: JSON.stringify(data) }),
  patch: (path, data) => request(path, { method: 'PATCH', body: JSON.stringify(data) }),
  remove: (path) => request(path, { method: 'DELETE' }),
}
