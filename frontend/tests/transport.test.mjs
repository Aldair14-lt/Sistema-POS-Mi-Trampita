import { test, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { api, readHttp, ApiError } from '../src/api.js'
import { storageRead, storageWrite } from '../src/utils/storage.js'
import { persistPayment, restorePayment } from '../src/utils/pendingOperation.js'

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch; delete globalThis.sessionStorage })

test('respuesta JSON dañada conserva un error recuperable para reintentos financieros', async () => {
  globalThis.fetch = async () => new Response('{"id":', { status: 200 })
  await assert.rejects(readHttp('/api/ventas/1/pagos'), error => error instanceof ApiError && error.status === 502)
})
test('un corte de red durante la lectura del cuerpo se trata como respuesta incierta', async () => {
  globalThis.fetch = async () => ({ ok: true, status: 200, text: async () => { throw new TypeError('corte') } })
  await assert.rejects(readHttp('/api/ventas/1/pagos'), error => error.status === 0)
})
test('rechazo sin cuerpo 204 funciona y las escrituras privadas incluyen CSRF y sesión', async () => {
  const requests = []
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options })
    return url.endsWith('/csrf') ? Response.json({ headerName: 'X-CSRF-TOKEN', token: 'test-token' }) : new Response(null, { status: 204 })
  }
  assert.equal(await api.patch('/api/web/solicitudes/1/rechazar', { motivo: 'Agotado' }), null)
  assert.equal(requests[1].options.headers['X-CSRF-TOKEN'], 'test-token')
  assert.equal(requests[1].options.credentials, 'include')
})
test('un POST con cuerpo vacío no se interpreta como pago confirmado', async () => {
  globalThis.fetch = async url => url.endsWith('/csrf') ? Response.json({ headerName: 'X-CSRF-TOKEN', token: 'test-token' }) : new Response('', { status: 200 })
  await assert.rejects(api.create('/api/ventas/1/pagos', {}), error => error.status === 502)
})
test('un PATCH con HTTP 200 vacío o null conserva la comanda para reintentar', async () => {
  for (const body of ['', 'null']) {
    globalThis.fetch = async url => url.endsWith('/csrf') ? Response.json({ headerName: 'X-CSRF-TOKEN', token: 'test-token' }) : new Response(body, { status: 200 })
    await assert.rejects(api.patch('/api/ventas/1/items', { claveOperacion: 'original' }), error => error.status === 502)
  }
})
test('almacenamiento denegado no impide abrir la interfaz ni conservar datos en memoria', () => {
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, get() { throw new Error('denegado') } })
  assert.equal(storageRead('sessionStorage', 'clave', null), null)
  assert.doesNotThrow(() => storageWrite('sessionStorage', 'clave', { dato: 1 }))
})
test('recargar y reintentar un abono recupera el mismo UUID y cuerpo; otra cuenta no lo recupera', () => {
  const values = new Map()
  globalThis.sessionStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
  const pending = { path: '/api/ventas/42/pagos', key: 'operacion-original', closes: false, body: { monto: 5, claveOperacion: 'operacion-original', metodoPago: 'YAPE' } }
  persistPayment(42, pending)
  assert.deepEqual(restorePayment(42), pending)
  assert.equal(restorePayment(43), null)
  persistPayment(42, null)
  assert.equal(restorePayment(42), null)
})
