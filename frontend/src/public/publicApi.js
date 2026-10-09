import { API_URL, ApiError } from '../api'

async function request(path, options = {}) {
  let response
  try {
    response = await fetch(`${API_URL}/api/public${path}`, { ...options, credentials: 'omit', headers: { 'Content-Type': 'application/json' } })
  } catch { throw new ApiError('No se pudo conectar. Revisa tu conexión y vuelve a intentar.', 0) }
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new ApiError(body?.message || 'No se pudo completar la solicitud.', response.status)
  return body
}
export const publicApi = {
  menu: slug => request(`/tiendas/${encodeURIComponent(slug)}/menu`),
  send: (slug, order) => request(`/tiendas/${encodeURIComponent(slug)}/pedidos`, { method: 'POST', body: JSON.stringify(order) }),
  status: (slug, code) => request(`/tiendas/${encodeURIComponent(slug)}/pedidos/${encodeURIComponent(code)}`),
}
export const soles = value => new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(Number(value || 0))
export function operationId() {
  if (crypto.randomUUID) return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128
  const hex = [...bytes].map(b => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
export function cartTotal(cart, products) {
  const subtotal = cart.reduce((sum, item) => sum + Math.round(Number(products.find(p => p.id === item.productoId)?.precioBase || 0) * 100) * item.cantidad, 0)
  return (subtotal + Math.round(subtotal * 0.18)) / 100
}
export function storageRead(storage, key, fallback) { try { return JSON.parse(storage.getItem(key)) ?? fallback } catch { return fallback } }
export function storageWrite(storage, key, value) { try { value == null ? storage.removeItem(key) : storage.setItem(key, JSON.stringify(value)) } catch { /* El pedido sigue funcionando si el navegador bloquea almacenamiento. */ } }
