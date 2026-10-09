/* Ejecutar exclusivamente contra Vite y backend conectados a una base QA. */
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const path = require('node:path')
const password = process.env.POS_TEST_PASSWORD
if (!password) throw new Error('Define POS_TEST_PASSWORD')
const baseURL = process.env.POS_UI_URL || 'http://127.0.0.1:15198'
assert(/^http:\/\/(127\.0\.0\.1|localhost):/.test(baseURL), 'Usar Vite QA local')
const output = process.env.POS_VALIDATION_DIR || path.resolve(__dirname, '../../docs/validation/commercial08')

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
    const body = await response.text()
    return body ? JSON.parse(body) : null
  }
  const watch = page => { page.on('pageerror', error => errors.push(error.message)) }
  try {
    await call('POST', '/api/auth/login', { usuario: process.env.POS_TEST_USER || 'admin', contrasena: password })
    const suffix = Date.now().toString().slice(-8)
    const roles = await call('GET', '/api/roles'), users = {}
    for (const role of ['COCINERO', 'BARTENDER']) {
      users[role] = `qa08_${role}_${suffix}`
      await call('POST', '/api/usuarios', { usuario: users[role], contrasena: password, nombreCompleto: `Prueba ${role}`, estado: 'activo', rolIds: [roles.find(r => r.nombre === role).id] })
    }
    const company = await call('POST', '/api/configuracion', { ruc: `20${suffix}1`, razonSocial: `RECREO MI TRAMPITA QA ${suffix}`, direccion: 'Av. Principal 123 · Tarapoto' })
    const customer = await call('POST', '/api/clientes', { numeroDocumento: suffix, nombresRazonSocial: 'Ana Torres', telefono: '999888777', correo: `ana${suffix}@example.com`, fechaNacimiento: '1994-10-20' })
    const provider = await call('POST', '/api/proveedores', { rucDni: `10${suffix}1`, razonSocial: 'Proveedor QA' })
    const category = (await call('GET', '/api/categorias'))[0]
    const products = []
    for (const [index, [nombre, precioVenta, areaDestino]] of [['Arroz con pato', 32.5, 'COCINA'], ['Jarra de maracuyá', 15, 'BAR']].entries()) {
      products.push(await call('POST', '/api/productos', { categoria: { id: category.id }, proveedor: { id: provider.id }, codigoBarras: `QA08-${suffix}-${index}`, nombre, precioCompra: 5, precioVenta, stockActual: 100, stockMinimo: 2, areaDestino }))
    }
    const area = await call('POST', '/api/areas', { nombre: `Terraza QA ${suffix}`, estado: 'ACTIVA' })
    const highest = Math.max(...(await call('GET', '/api/mesas')).map(m => m.numero)), tables = []
    for (let i = 1; i <= 4; i++) tables.push(await call('POST', '/api/mesas', { numero: highest + i, capacidad: 4, areaId: area.id }))
    const receipt = (await call('GET', '/api/tipos-comprobante')).find(r => r.nombre === 'BOLETA')
    const order = table => call('POST', '/api/ventas', { empresaId: company.id, clienteId: customer.id, tipoComprobanteId: receipt.id, numeroComprobante: `QA08-${crypto.randomUUID()}`, mesaId: table.id, items: products.map((p, i) => ({ productoId: p.id, cantidad: 1, observaciones: i ? 'Sin hielo' : 'Sin ají' })) })
    const deliver = async sale => {
      for (const item of sale.detalles) {
        const route = item.areaDestino === 'BAR' ? 'bar' : 'cocina'
        await call('PATCH', `/api/${route}/items/${item.id}/estado`, { estado: 'PREPARANDO', estadoActual: 'PENDIENTE' })
        await call('PATCH', `/api/${route}/items/${item.id}/estado`, { estado: 'LISTO', estadoActual: 'PREPARANDO' })
        await call('PATCH', `/api/ventas/items/${item.id}/servir`, { estado: 'SERVIDO', estadoActual: 'LISTO' })
      }
    }
    const waiting = await order(tables[0]), charge = await order(tables[1]), requested = await order(tables[2])
    await deliver(charge); await deliver(requested); await call('PATCH', `/api/ventas/${requested.id}/solicitar-cuenta`, {})
    const page = await context.newPage(); watch(page)
    await page.addInitScript(() => { window.__prints = 0; window.print = () => { window.__prints++ } })
    await page.goto('/'); await page.locator('.sidebar').getByRole('button', { name: /Caja y recepción/ }).click()
    const register = page.getByRole('region', { name: 'Turno de caja' })
    // La sección es un landmark por su aria-label.
    await register.locator('header select').selectOption(String(company.id))
    await register.getByLabel('Sencillo inicial (S/)').fill('100')
    await register.getByRole('button', { name: 'Abrir turno', exact: true }).click()
    await register.locator('.cash-shift-status').waitFor()
    await page.screenshot({ path: path.join(output, '01-caja.png'), fullPage: true })
    await page.locator('.cashier-other-tables summary').click()
    const card = page.locator('.cashier-card').filter({ has: page.getByRole('heading', { name: `Mesa ${String(tables[1].numero).padStart(2, '0')}`, exact: true }) })
    await card.getByRole('button', { name: /Cobrar/ }).click()
    let modal = page.getByRole('dialog')
    await modal.getByRole('button', { name: 'Abono / pago anticipado' }).click()
    await modal.getByLabel('Abono (S/)', { exact: true }).fill('5')
    await modal.getByRole('button', { name: 'Yape', exact: true }).click()
    await modal.getByRole('button', { name: 'Registrar abono', exact: true }).click()
    await modal.getByText('Abono registrado. El saldo se actualizó.').waitFor()
    assert.equal(Number((await call('GET', `/api/ventas/${charge.id}`)).saldoPendiente), 51.05)
    await modal.getByRole('button', { name: 'Pago total', exact: true }).click()
    await modal.getByRole('button', { name: 'Efectivo', exact: true }).click()
    await modal.getByLabel('Monto recibido (S/)').fill('100')
    await modal.getByRole('button', { name: /Continuar a comprobante/ }).click()
    await modal.getByRole('button', { name: 'Factura', exact: true }).click()
    await modal.getByLabel('RUC', { exact: true }).fill('20123456789')
    await modal.getByLabel('Razón social', { exact: true }).fill('Servicios del Recreo SAC')
    await modal.getByLabel('Dirección fiscal', { exact: true }).fill('Av. Los Jardines 120')
    assert.equal(await modal.getByLabel('Formato de impresión').inputValue(), 'A4')
    await page.setViewportSize({ width: 390, height: 844 })
    await modal.screenshot({ path: path.join(output, '02-pago-movil.png') })
    await page.setViewportSize({ width: 1440, height: 1100 })
    await modal.getByRole('button', { name: 'Emitir y cerrar mesa', exact: true }).click()
    await page.waitForFunction(() => window.__prints === 1)
    const emitted = await call('GET', `/api/ventas/${charge.id}`)
    assert.equal(emitted.comprobante.tipoComprobante, 'FACTURA'); assert.equal(Number(emitted.pagos.at(-1).vuelto), 48.95)
    const savePrint = async (name, expectedFormat) => {
      await page.emulateMedia({ media: 'print' })
      const document = page.locator('.pos-print-document')
      assert(await document.evaluate((element, cls) => element.classList.contains(cls), expectedFormat))
      assert.equal(await page.locator('#root').evaluate(e => getComputedStyle(e).display), 'none')
      await document.screenshot({ path: path.join(output, `${name}.png`) })
      await page.pdf({ path: path.join(output, `${name}.pdf`), preferCSSPageSize: true, printBackground: true })
      await page.emulateMedia({ media: 'screen' })
      await page.evaluate(() => window.dispatchEvent(new Event('afterprint')))
      await page.waitForFunction(() => !document.querySelector('.pos-print-document'))
    }
    await savePrint('03-factura-a4', 'print-a4')
    await page.getByLabel('Formato para reimpresión').selectOption('80mm')
    await page.locator('.cashier-receipts').getByRole('button', { name: `Imprimir comprobante de la venta ${charge.id}` }).click()
    await page.waitForFunction(() => window.__prints === 2)
    await savePrint('04-ticket-80mm', 'print-thermal')
    await register.getByRole('button', { name: 'Cerrar turno y realizar arqueo' }).click()
    await register.getByLabel('Efectivo contado en caja (S/)').fill('151.05')
    await register.getByLabel('Observaciones del arqueo').fill('Cuadre verificado')
    await register.getByRole('button', { name: 'Confirmar cierre y arqueo' }).click()
    await register.getByText('Últimos cierres de caja').click()
    await register.getByRole('button', { name: 'Reporte A4' }).click()
    await page.waitForFunction(() => window.__prints === 3)
    await savePrint('05-cierre-a4', 'print-a4')
    await call('POST', '/api/caja/sesiones/abrir', { empresaId: company.id, montoInicial: 50, claveOperacion: crypto.randomUUID() })
    await page.getByRole('button', { name: 'Pedido WhatsApp', exact: true }).click()
    const form = page.locator('.online-order-form')
    await form.getByLabel('Empresa del pedido').selectOption(String(company.id))
    await form.getByLabel('Cliente del pedido').selectOption(String(customer.id))
    await form.getByLabel('Producto', { exact: true }).selectOption(String(products[1].id))
    await form.getByLabel('Observaciones', { exact: true }).fill('Sin hielo, para llevar')
    await form.getByLabel('Modalidad').selectOption('ADELANTO')
    await form.getByLabel('Adelanto (S/)').fill('5')
    await form.getByRole('button', { name: 'Enviar pedido a Cocina / Bar', exact: true }).click()
    await form.waitFor({ state: 'hidden' })
    const online = (await call('GET', '/api/pedidos-online')).find(s => s.empresa.id === company.id)
    assert(online); assert.equal(Number(online.totalPagado), 5); assert.equal(online.detalles[0].areaDestino, 'BAR')
    await page.locator('.sidebar').getByRole('button', { name: /Pedidos online/ }).click()
    await page.locator('.online-kanban').waitFor(); await page.screenshot({ path: path.join(output, '06-online.png'), fullPage: true })
    const red = await order(tables[1]); await deliver(red)
    await page.locator('.sidebar').getByRole('button', { name: /Mesas y ventas/ }).click()
    await page.getByRole('button', { name: area.nombre, exact: true }).click()
    for (const state of ['free', 'occupied', 'billing', 'waiting']) assert(await page.locator(`.table-tile.${state}`).count() > 0)
    assert.equal(await page.locator('.table-tile.occupied').first().evaluate(e => getComputedStyle(e).getPropertyValue('--tone').trim()), '#991b1b')
    await page.screenshot({ path: path.join(output, '07-mesas.png'), fullPage: true })
    await page.locator('.sidebar').getByRole('button', { name: 'Marketing', exact: true }).click()
    await page.getByRole('button', { name: 'Exportar CSV para Facebook Ads' }).waitFor()
    const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Exportar CSV para Facebook Ads' }).click()])
    await download.saveAs(path.join(output, 'clientes-meta.csv'))
    const csv = await fs.readFile(path.join(output, 'clientes-meta.csv'), 'utf8')
    assert(csv.startsWith('email,phone,country\r\n')); assert(csv.includes(`ana${suffix}@example.com,51999888777,pe`))
    await page.screenshot({ path: path.join(output, '08-marketing.png'), fullPage: true })
    for (const [role, station, areaName] of [['COCINERO', 'Cocina', 'COCINA'], ['BARTENDER', 'Bar', 'BAR']]) {
      const roleContext = await browser.newContext({ baseURL, viewport: { width: 1440, height: 1000 } })
      const token = await (await roleContext.request.get('/api/auth/csrf')).json()
      await roleContext.request.post('/api/auth/login', { headers: { [token.headerName]: token.token }, data: { usuario: users[role], contrasena: password } })
      const rolePage = await roleContext.newPage(); watch(rolePage)
      await rolePage.addInitScript(() => { window.__prints = 0; window.print = () => { window.__prints++ } })
      await rolePage.goto('/'); await rolePage.locator('.kds-kanban').waitFor()
      assert.equal(await rolePage.locator('.sidebar').getByRole('button', { name: /Caja y recepción|Mesas y ventas|Marketing/ }).count(), 0)
      await rolePage.screenshot({ path: path.join(output, `09-${station.toLowerCase()}.png`), fullPage: true })
      const ticket = rolePage.locator('.kds-ticket').filter({ hasText: `COMANDA #${waiting.id}` }).first()
      await ticket.getByRole('button', { name: `Imprimir comanda · ${station}` }).click()
      await rolePage.waitForFunction(() => window.__prints === 1)
      await rolePage.emulateMedia({ media: 'print' })
      const command = rolePage.locator('.pos-print-document')
      const text = await command.innerText()
      assert(text.includes(areaName)); assert(!text.includes('S/')); assert(!text.includes('IGV')); assert(text.includes(role === 'COCINERO' ? 'Sin ají' : 'Sin hielo'))
      await command.screenshot({ path: path.join(output, `10-comanda-${station.toLowerCase()}.png`) })
      await roleContext.close()
    }
    assert.deepEqual(errors, [])
    await fs.writeFile(path.join(output, 'resultado.json'), JSON.stringify({ passed: true, viewport: [1440, 390], errors, companyId: company.id, saleId: charge.id, checks: ['apertura y arqueo', 'Yape parcial', 'efectivo y vuelto', 'factura A4', 'ticket 80mm', 'cierre A4', 'WhatsApp con adelanto', 'Kanban', 'cuatro colores de mesas', 'CSV Meta', 'roles y comandas Cocina/Bar sin precios'] }, null, 2))
    console.log(`PASS UI: caja, pagos, formatos, mesas, Kanban, CSV y roles de Cocina/Bar. Evidencias: ${output}`)
  } catch (error) {
    const page = context.pages().at(-1)
    if (page) {
      await page.screenshot({ path: path.join(output, 'fallo.png'), fullPage: true }).catch(() => {})
      console.error((await page.locator('.content').innerText().catch(() => '')).slice(0, 2000))
    }
    throw error
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
