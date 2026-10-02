/* SOLO sobre una base QA. Ejecuta API real y navegador; conserva fixtures y capturas. */
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const path = require('node:path')
const baseURL = process.env.POS_UI_URL || 'http://127.0.0.1:15173'
const password = process.env.POS_TEST_PASSWORD
if (!password) throw new Error('Define POS_TEST_PASSWORD para la cuenta de pruebas')
const output = path.resolve(__dirname, '../../docs/validation/caja07')

async function main() {
  await fs.mkdir(output, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const admin = await browser.newContext({ baseURL })
  const call = async (context, method, url, data) => {
    const headers = {}
    if (method !== 'GET') {
      const token = await (await context.request.get('/api/auth/csrf')).json()
      headers[token.headerName] = token.token
    }
    const response = await context.request.fetch(url, { method, data, headers })
    assert(response.ok(), `${method} ${url}: ${response.status()} ${await response.text()}`)
    return response.status() === 204 ? null : response.json()
  }
  const api = (method, url, data) => call(admin, method, url, data)
  const suffix = Date.now().toString().slice(-7)
  try {
    await api('POST', '/api/auth/login', { usuario: process.env.POS_TEST_USER || 'qa_admin_07', contrasena: password })
    const roles = await api('GET', '/api/roles')
    const users = {}
    for (const role of ['CAJA', 'MOZO', 'COCINERO']) {
      users[role] = `visual_${role.toLowerCase()}_${suffix}`
      await api('POST', '/api/usuarios', { usuario: users[role], contrasena: password, nombreCompleto: `Prueba ${role}`, estado: 'activo', rolIds: [roles.find(r => r.nombre === role).id] })
    }
    const company = await api('POST', '/api/configuracion', { ruc: `20${suffix}01`, razonSocial: 'RECREO MI TRAMPITA', direccion: 'Av. Principal 123 · Tarapoto' })
    const customer = await api('POST', '/api/clientes', { numeroDocumento: `1${suffix}`, nombresRazonSocial: 'Ana Torres', telefono: '999888777' })
    const provider = await api('POST', '/api/proveedores', { rucDni: `10${suffix}01`, razonSocial: 'Proveedor de prueba' })
    const category = (await api('GET', '/api/categorias'))[0], brand = (await api('GET', '/api/marcas'))[0]
    const products = []
    for (const [index, [nombre, precioVenta]] of [['Arroz con pato', 32.5], ['Jarra de maracuyá', 15]].entries()) {
      products.push(await api('POST', '/api/productos', { categoria: { id: category.id }, marca: { id: brand.id }, proveedor: { id: provider.id }, codigoBarras: `VISUAL-${suffix}-${index}`, nombre, precioCompra: 10, precioVenta, stockActual: 50, stockMinimo: 2 }))
    }
    const area = await api('POST', '/api/areas', { nombre: `Terraza ${suffix}`, estado: 'ACTIVA' })
    const highest = Math.max(...(await api('GET', '/api/mesas')).map(m => m.numero))
    const tables = []
    for (let i = 1; i <= 4; i++) tables.push(await api('POST', '/api/mesas', { numero: highest + i, capacidad: i === 2 ? 6 : 4, areaId: area.id }))
    const receipt = (await api('GET', '/api/tipos-comprobante')).find(r => r.nombre === 'BOLETA')
    const sales = []
    for (let i = 0; i < 3; i++) sales.push(await api('POST', '/api/ventas', { empresaId: company.id, clienteId: customer.id, tipoComprobanteId: receipt.id, numeroComprobante: `VISUAL-${suffix}-${i}`, mesaId: tables[i].id, items: products.map(p => ({ productoId: p.id, cantidad: 1 })) }))
    const transition = (item, estado, estadoActual) => api('PATCH', `/api/cocina/items/${item.id}/estado`, { estado, estadoActual })
    for (const sale of [sales[0], sales[2]]) {
      for (const item of sale.detalles) {
        await transition(item, 'PREPARANDO', 'PENDIENTE'); await transition(item, 'LISTO', 'PREPARANDO')
        await api('PATCH', `/api/ventas/items/${item.id}/servir`, { estado: 'SERVIDO', estadoActual: 'LISTO' })
      }
    }
    await api('PATCH', `/api/ventas/${sales[0].id}/solicitar-cuenta`, {})
    await transition(sales[1].detalles[0], 'PREPARANDO', 'PENDIENTE')
    const pageErrors = []
    const context = await browser.newContext({ baseURL, viewport: { width: 1440, height: 1050 } })
    const page = await context.newPage()
    page.on('pageerror', err => pageErrors.push(err.message))
    const login = async (target, username) => {
      await target.goto('/')
      await target.getByLabel('Usuario', { exact: true }).fill(username)
      await target.getByLabel('Contraseña', { exact: true }).fill(password)
      await target.getByRole('button', { name: 'Entrar', exact: true }).click()
    }
    await login(page, users.CAJA)
    await page.getByRole('heading', { name: 'Cuentas claras, buen servicio.' }).waitFor()
    const card = page.locator('.cashier-card').filter({ has: page.getByRole('heading', { name: `Mesa ${String(tables[0].numero).padStart(2, '0')}`, exact: true }) })
    await card.getByRole('button', { name: 'Cobrar / registrar abono' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByRole('button', { name: 'Abono / pago anticipado' }).click()
    await dialog.getByLabel('Abono (S/)').fill('20')
    await dialog.getByLabel('Monto recibido (S/)').fill('50')
    assert.match(await dialog.locator('.payment-change').textContent(), /30.00/)
    await page.screenshot({ path: path.join(output, '01-pago-efectivo.png'), fullPage: false })
    await dialog.getByRole('button', { name: 'Registrar abono', exact: true }).click()
    await dialog.getByText('Abono registrado. El saldo se actualizó.').waitFor()
    await dialog.getByRole('button', { name: 'Pago total', exact: true }).click()
    await dialog.getByRole('button', { name: 'Yape', exact: true }).click()
    assert.equal(await dialog.getByLabel('Monto recibido (S/)').count(), 0)
    await dialog.getByRole('button', { name: 'Continuar a comprobante' }).click()
    await dialog.getByRole('button', { name: 'Factura', exact: true }).click()
    await dialog.getByLabel('RUC', { exact: true }).fill('20123456789')
    await dialog.getByLabel('Razón social', { exact: true }).fill('Servicios Amazónicos SAC')
    await dialog.getByLabel('Dirección fiscal', { exact: true }).fill('Av. Los Jardines 120 · Tarapoto')
    await page.screenshot({ path: path.join(output, '02-factura.png'), fullPage: false })
    await page.setViewportSize({ width: 390, height: 844 })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
    await page.screenshot({ path: path.join(output, '03-factura-movil.png'), fullPage: false })
    await dialog.getByRole('button', { name: 'Boleta', exact: true }).click()
    assert.equal(await dialog.getByLabel('RUC', { exact: true }).count(), 0)
    await dialog.getByRole('button', { name: 'Ticket simple', exact: true }).click()
    assert.equal(await dialog.getByLabel(/DNI/).count(), 0)
    await dialog.getByRole('button', { name: 'Factura', exact: true }).click()
    await page.setViewportSize({ width: 1440, height: 1050 })
    await dialog.getByRole('button', { name: 'Emitir y cerrar mesa', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    const paid = await api('GET', `/api/ventas/${sales[0].id}`)
    assert.equal(paid.estado, 'CERRADA'); assert.equal(paid.pagos.length, 2)
    assert.equal(paid.comprobante.tipoComprobante, 'FACTURA'); assert.equal(paid.mesa.estado, 'LIBRE')
    await page.evaluate(() => { window.print = () => {} })
    await page.getByRole('button', { name: 'Imprimir comprobante', exact: true }).click()
    await page.emulateMedia({ media: 'print' })
    await page.locator('.thermal-receipt').screenshot({ path: path.join(output, '04-ticket.png') })
    await page.emulateMedia({ media: 'screen' })

    const mozoContext = await browser.newContext({ baseURL, viewport: { width: 1440, height: 1000 } })
    const mozo = await mozoContext.newPage(); mozo.on('pageerror', err => pageErrors.push(err.message))
    await login(mozo, users.MOZO)
    await mozo.getByRole('button', { name: area.nombre, exact: true }).click()
    await mozo.locator('.table-tile').first().waitFor()
    assert.equal(await mozo.getByRole('button', { name: 'Pedidos online', exact: true }).count(), 0)
    await mozo.screenshot({ path: path.join(output, '05-mesas.png'), fullPage: false })
    const cookContext = await browser.newContext({ baseURL, viewport: { width: 1600, height: 1000 } })
    const cook = await cookContext.newPage(); cook.on('pageerror', err => pageErrors.push(err.message))
    await login(cook, users.COCINERO)
    await cook.getByRole('heading', { name: 'Cada plato, en su momento.' }).waitFor()
    const ticket = cook.locator('.kds-ticket').filter({ hasText: `COMANDA #${sales[1].id}` }).filter({ has: cook.getByRole('button', { name: 'Listo', exact: true }) })
    await ticket.getByRole('button', { name: 'Listo', exact: true }).click()
    await mozo.locator('.table-ready').filter({ hasText: 'listo' }).first().waitFor()
    await cook.getByRole('region', { name: 'Listos para recoger' }).getByText(`COMANDA #${sales[1].id}`, { exact: true }).waitFor()
    await cook.screenshot({ path: path.join(output, '06-cocina.png'), fullPage: false })
    // Flujo real de recepción: crear un pedido de WhatsApp con adelanto Yape desde la UI.
    await page.getByRole('button', { name: 'Pedido WhatsApp', exact: true }).click()
    const form = page.locator('.online-order-form')
    await form.getByLabel('Cliente del pedido', { exact: true }).selectOption(String(customer.id))
    await form.getByLabel('Producto', { exact: true }).first().selectOption(String(products[0].id))
    await form.getByLabel('Modalidad', { exact: true }).selectOption('ADELANTO')
    await form.getByLabel('Adelanto (S/)').fill('5')
    await form.getByRole('button', { name: 'Enviar pedido a cocina' }).click()
    await form.waitFor({ state: 'hidden' })
    const online = (await api('GET', '/api/pedidos-online')).filter(v => v.cliente.id === customer.id && v.origenPedido === 'WHATSAPP')
    assert.equal(online.length, 1); assert.equal(Number(online[0].totalPagado), 5)
    assert.equal(online[0].pagos[0].metodoPago, 'YAPE')
    assert.deepEqual(pageErrors, [])
    await fs.writeFile(path.join(output, 'resultado.json'), JSON.stringify({ baseURL, fecha: new Date().toISOString(), venta: paid.id, comprobante: paid.comprobante.numero, pedidoWhatsApp: online[0].id, verificaciones: ['abono efectivo y vuelto', 'factura, boleta y ticket condicionales', 'modal móvil sin desbordamiento', 'cierre libera mesa', 'recibo térmico', 'roles exclusivos', 'KDS listo notifica a Mozo', 'WhatsApp con adelanto Yape'], erroresJavaScript: pageErrors }, null, 2))
    console.log('PASS UI: cobro parcial/total, vuelto, comprobantes, móvil, impresión, mesas, KDS en vivo, roles y WhatsApp.')
  } catch (error) {
    for (const [i, context] of browser.contexts().entries()) {
      const page = context.pages()[0]; if (page) {
        await page.screenshot({ path: path.join(output, `failure-${i}.png`) }).catch(() => {})
        if (await page.locator('.online-order-form').count()) console.error(await page.locator('.online-order-form').innerText())
      }
    }
    throw error
  } finally { await browser.close() }
}
main().catch(err => { console.error(err); process.exitCode = 1 })
