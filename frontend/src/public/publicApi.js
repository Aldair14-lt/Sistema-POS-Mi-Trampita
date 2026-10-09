import { API_URL, ApiError, readHttp } from '../api'
export { storageRead, storageWrite } from '../utils/storage'

async function request(path, options = {}) {
  const body = await readHttp(`${API_URL}/api/public${path}`, { ...options, credentials: 'omit', headers: { 'Content-Type': 'application/json' } })
  if (!body || typeof body !== 'object') throw new ApiError('El servidor devolvió una respuesta incompleta. Reintenta el mismo pedido.', 502)
  return body
}
const tracking = body => {
  if (!/^[0-9a-f-]{36}$/i.test(body?.codigoSeguimiento || '') || typeof body?.estado !== 'string' || !Number.isFinite(Number(body.total)))
    throw new ApiError('No se pudo confirmar la respuesta. Reintenta el mismo pedido.', 502)
  return body
}
export const publicApi = {
  menu: async slug => {
    const menu = await request(`/tiendas/${encodeURIComponent(slug)}/menu`)
    if (!Array.isArray(menu.productos)) throw new ApiError('No se pudo leer la carta. Vuelve a intentar.', 502)
    return menu
  },
  send: async (slug, order) => tracking(await request(`/tiendas/${encodeURIComponent(slug)}/pedidos`, { method: 'POST', body: JSON.stringify(order) })),
  status: async (slug, code) => tracking(await request(`/tiendas/${encodeURIComponent(slug)}/pedidos/${encodeURIComponent(code)}`)),
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
