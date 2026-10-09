/* Exclusivamente para Vite y backend conectados a bases QA locales. */
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const path = require('node:path')
const baseURL = process.env.POS_UI_URL || 'http://127.0.0.1:15199'
assert(/^http:\/\/(127\.0\.0\.1|localhost):/.test(baseURL), 'Usar Vite QA local')
assert(process.env.POS_TEST_PASSWORD, 'Define POS_TEST_PASSWORD')
const output = process.env.POS_VALIDATION_DIR || path.resolve(__dirname, '../../docs/validation/audit10/recovery')

async function main() {
  await fs.mkdir(output, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const context = await browser.newContext({ baseURL, viewport: { width: 1440, height: 1100 } })
  const errors = []
  const call = async (method, url, data) => {
    const headers = {}
    if (method !== 'GET') { const token = await (await context.request.get('/api/auth/csrf')).json(); headers[token.headerName] = token.token }
    const response = await context.request.fetch(url, { method, data, headers })
    assert(response.ok(), `${method} ${url}: ${response.status()} ${await response.text()}`)
    const text = await response.text(); return text ? JSON.parse(text) : null
  }
  const page = await context.newPage()
  page.on('pageerror', e => errors.push(e.message))
  await page.addInitScript(() => { window.__prints = 0; window.print = () => { window.__prints++ } })
  try {
    await call('POST', '/api/auth/login', { usuario: 'admin', contrasena: process.env.POS_TEST_PASSWORD })
    const suffix = Date.now().toString().slice(-8)
    const company = await call('POST', '/api/configuracion', { ruc: `20${suffix}1`, razonSocial: `QA auditoria ${suffix}`, direccion: 'Av. QA 123' })
    const customer = await call('POST', '/api/clientes', { numeroDocumento: suffix, nombresRazonSocial: 'Comprador QA auditoria' })
    const provider = await call('POST', '/api/proveedores', { rucDni: `10${suffix}1`, razonSocial: 'Proveedor QA auditoria' })
    const category = (await call('GET', '/api/categorias'))[0]
    const products = []
    for (let i = 0; i < 2; i++) products.push(await call('POST', '/api/productos', {
      categoria: { id: category.id }, proveedor: { id: provider.id }, codigoBarras: `QA10-${suffix}-${i}`,
      nombre: `${i ? 'Adicional' : 'Inicial'} auditoria ${suffix}`, precioCompra: 1.25, precioVenta: 10.5, stockActual: 20, stockMinimo: 1, areaDestino: 'COCINA'
    }))
    const area = await call('POST', '/api/areas', { nombre: `QA auditoria ${suffix}`, estado: 'ACTIVA' })
    const max = Math.max(...(await call('GET', '/api/mesas')).map(m => m.numero))
    const table = await call('POST', '/api/mesas', { numero: max + 1, capacidad: 4, areaId: area.id })
    const type = (await call('GET', '/api/tipos-comprobante')).find(t => t.nombre === 'BOLETA')
    const sale = await call('POST', '/api/ventas', { empresaId: company.id, clienteId: customer.id, tipoComprobanteId: type.id,
      numeroComprobante: `QA10-${crypto.randomUUID()}`, mesaId: table.id, items: [{ productoId: products[0].id, cantidad: 1 }] })
    await call('POST', '/api/caja/sesiones/abrir', { empresaId: company.id, montoInicial: 20, claveOperacion: crypto.randomUUID() })
    const selectSale = async () => {
      await page.locator('.sidebar').getByRole('button', { name: /Mesas y ventas/ }).click()
      await page.getByLabel('Venta / mesa').selectOption(String(sale.id))
      await page.locator('.open-sale-summary').waitFor()
    }
    await page.goto('/'); await selectSale()
    await page.getByPlaceholder('Buscar por nombre o código...').fill(products[1].codigoBarras)
    await page.locator('.product-card').filter({ hasText: products[1].nombre }).click()
    const additionalRequests = []
    const additionalPath = `/api/ventas/${sale.id}/items`
    await page.route(`**${additionalPath}`, async route => {
      additionalRequests.push(route.request().postDataJSON())
      const response = await route.fetch(); assert(response.ok())
      if (additionalRequests.length === 1) await route.fulfill({ status: 200, contentType: 'application/json', body: '' })
      else await route.fulfill({ response })
    })
    await page.getByRole('button', { name: /Enviar adicional/ }).click()
    await page.getByRole('button', { name: 'Comprobar / reintentar comanda' }).waitFor()
    assert(await page.locator('.cart-item input').isDisabled())
    await page.reload(); await page.locator('.sidebar').getByRole('button', { name: /Mesas y ventas/ }).click()
    await page.getByRole('button', { name: 'Comprobar / reintentar comanda' }).click()
    await page.getByText(/Adicional registrado una sola vez/).waitFor()
    assert.equal(additionalRequests.length, 2); assert.deepEqual(additionalRequests[0], additionalRequests[1])
    let updated = await call('GET', `/api/ventas/${sale.id}`)
    assert.equal(updated.detalles.length, 2)
    assert.equal((await call('GET', '/api/productos')).find(p => p.id === products[1].id).stockActual, 19)
    await page.getByRole('button', { name: 'Comanda COCINA', exact: true }).click()
    await page.waitForFunction(() => window.__prints === 1)
    const printed = await page.locator('.pos-print-document').innerText()
    assert(printed.includes(products[1].nombre)); assert(!printed.includes(products[0].nombre)); assert(!printed.includes('S/'))
    await page.evaluate(() => window.dispatchEvent(new Event('afterprint')))
    await page.screenshot({ path: path.join(output, '01-adicional-recuperado.png'), fullPage: true })
    await page.getByRole('button', { name: 'Pagos parciales / cerrar cuenta' }).click()
    let modal = page.getByRole('dialog')
    await modal.getByRole('button', { name: 'Abono / pago anticipado' }).click()
    await modal.getByLabel('Abono (S/)', { exact: true }).fill('5.25')
    await modal.getByRole('button', { name: 'Yape', exact: true }).click()
    const paymentRequests = []
    const paymentPath = `/api/ventas/${sale.id}/pagos`, salePath = `/api/ventas/${sale.id}`
    await page.route(`**${paymentPath}`, async route => {
      paymentRequests.push(route.request().postDataJSON())
      const response = await route.fetch(); assert(response.ok())
      if (paymentRequests.length === 1) await route.fulfill({ status: 200, contentType: 'application/json', body: '{' })
      else await route.fulfill({ response })
    })
    await page.route(`**${salePath}`, route => route.fulfill({ status: 503, contentType: 'application/json', body: '{"message":"Corte QA"}' }))
    await modal.getByRole('button', { name: 'Registrar abono', exact: true }).click()
    await modal.getByRole('button', { name: 'Comprobar / reintentar operación' }).waitFor()
    await page.unroute(`**${salePath}`)
    await page.reload(); await selectSale()
    await page.getByRole('button', { name: 'Pagos parciales / cerrar cuenta' }).click()
    modal = page.getByRole('dialog')
    await modal.getByRole('button', { name: 'Comprobar / reintentar operación' }).click()
    await modal.getByText('Abono registrado. El saldo se actualizó.').waitFor()
    assert.equal(paymentRequests.length, 2); assert.deepEqual(paymentRequests[0], paymentRequests[1])
    updated = await call('GET', salePath)
    assert.equal(updated.pagos.length, 1); assert.equal(Number(updated.totalPagado), 5.25)
    assert.equal(await page.evaluate(id => sessionStorage.getItem(`pos-pending-payment-${id}`), sale.id), null)
    await modal.screenshot({ path: path.join(output, '02-abono-recuperado.png') })
    await modal.getByRole('button', { name: 'Cerrar pagos', exact: true }).click()
    await page.locator('.sidebar').getByRole('button', { name: 'Productos', exact: true }).click()
    await page.getByRole('button', { name: 'Nuevo producto', exact: true }).click()
    const form = page.locator('.modal')
    await form.getByLabel('Categoria Id').selectOption(String(category.id))
    await form.getByLabel('Proveedor Id').selectOption(String(provider.id))
    await form.getByLabel('Codigo Barras').fill(`QA10-UI-${suffix}`)
    await form.getByLabel('Nombre', { exact: true }).fill('Precio decimal QA '+suffix)
    await form.getByLabel('Precio Compra').fill('1.25'); await form.getByLabel('Precio Venta').fill('12.50')
    await form.getByRole('button', { name: 'Guardar', exact: true }).click()
    await form.waitFor({ state: 'hidden' })
    assert.equal(Number((await call('GET', '/api/productos')).find(p => p.codigoBarras === `QA10-UI-${suffix}`).precioVenta), 12.5)
    const deniedStorage = await browser.newContext({ baseURL })
    await deniedStorage.addInitScript(() => {
      for (const key of ['localStorage', 'sessionStorage']) Object.defineProperty(window, key, { configurable: true, get() { throw new DOMException('Storage denied', 'SecurityError') } })
    })
    const login = await deniedStorage.newPage(); login.on('pageerror', e => errors.push(e.message))
    await login.goto('/'); await login.getByRole('heading', { name: 'Bienvenido de vuelta' }).waitFor()
    await deniedStorage.close()
    assert.deepEqual(errors, [])
    await fs.writeFile(path.join(output, 'resultado.json'), JSON.stringify({ passed: true, errors, saleId: sale.id,
      checks: ['adicional perdido y recarga sin doble stock', 'comanda imprime solo nueva tanda', 'JSON de pago corrupto y recarga sin doble abono', 'precios decimales en CRUD', 'almacenamiento restringido sin pantalla vacia'] }, null, 2))
    console.log('PASS UI auditoria: recuperacion, stock, pagos, impresion por tanda, decimales y almacenamiento.')
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
