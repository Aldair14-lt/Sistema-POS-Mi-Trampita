/* Solo QA: POS_UI_URL, POS_TEST_PASSWORD y PLAYWRIGHT_MODULE. */
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const path = require('node:path')
const baseURL = process.env.POS_UI_URL || 'http://127.0.0.1:15199'
assert(/^http:\/\/(127\.0\.0\.1|localhost):/.test(baseURL), 'Usar Vite QA local')
const password = process.env.POS_TEST_PASSWORD
assert(password, 'Define POS_TEST_PASSWORD')
const output = path.resolve(__dirname, '../../docs/validation/public09')

async function main() {
  await fs.mkdir(output, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const admin = await browser.newContext({ baseURL, viewport: { width: 1440, height: 1100 } })
  const customer = await browser.newContext({ baseURL, viewport: { width: 1440, height: 1100 } })
  const errors = [], publicRequests = []
  const call = async (method, url, data) => {
    const headers = {}
    if (method !== 'GET') { const csrf = await (await admin.request.get('/api/auth/csrf')).json(); headers[csrf.headerName] = csrf.token }
    const response = await admin.request.fetch(url, { method, data, headers })
    assert(response.ok(), `${method} ${url}: ${response.status()} ${await response.text()}`)
    const body = await response.text(); return body ? JSON.parse(body) : null
  }
  const page = await customer.newPage(), desk = await admin.newPage()
  for (const tab of [page, desk]) tab.on('pageerror', e => errors.push(e.message))
  page.on('request', request => { const pathname = new URL(request.url()).pathname; if (pathname.startsWith('/api/')) publicRequests.push(pathname) })
  try {
    await call('POST', '/api/auth/login', { usuario: 'admin', contrasena: password })
    for (const previous of (await call('GET', '/api/productos')).filter(p => p.visibleWeb && /^(QA09-|QA-WEB-)/.test(p.codigoBarras))) await call('PUT', `/api/productos/${previous.id}`, { ...previous, visibleWeb: false })
    const suffix = Date.now().toString().slice(-8)
    const company = await call('POST', '/api/configuracion', { ruc: `20${suffix}1`, razonSocial: 'RECREO MI TRAMPITA QA '+suffix, nombreComercial: 'Recreo Mi Trampita', direccion: 'Av. Los Jardines 123 · Tarapoto', telefono: '999888777' })
    const provider = await call('POST', '/api/proveedores', { rucDni: `10${suffix}1`, razonSocial: 'Proveedor QA menú' })
    const categories = await call('GET', '/api/categorias')
    const food = categories.find(c => c.nombre === 'Platos del recreo') || await call('POST', '/api/categorias', { nombre: 'Platos del recreo' })
    const drink = categories.find(c => c.nombre === 'Bebidas') || await call('POST', '/api/categorias', { nombre: 'Bebidas' })
    const products = []
    for (const [index, [nombre, precioVenta, categoria, areaDestino, stockActual, visibleWeb, descripcion]] of [
      ['Arroz con pato', 32.5, food, 'COCINA', 20, true, 'Arroz al culantro, pato suave y salsa criolla. Un clásico para compartir.'],
      ['Ceviche clásico', 28, food, 'COCINA', 20, true, 'Pescado fresco, limón y el toque justo de ají.'],
      ['Jarra de maracuyá', 15, drink, 'BAR', 20, true, 'Refrescante y natural, para acompañar tu almuerzo.'],
      ['Juane de la casa', 20, food, 'COCINA', 0, true, 'Nuestro sabor de la selva, preparado con cariño.'],
      ['Insumo privado QA', 1, food, 'COCINA', 50, false, 'No debe publicarse']
    ].entries()) products.push(await call('POST', '/api/productos', { categoria: { id: categoria.id }, proveedor: { id: provider.id }, codigoBarras: `QA09-${suffix}-${index}`, nombre, descripcion, precioCompra: 5, precioVenta, stockActual, stockMinimo: 2, areaDestino, visibleWeb }))
    const slug = 'menu-qa-'+suffix
    await call('PUT', `/api/web/configuracion/${company.id}`, { slug, activa: true, recojo: true, delivery: true, mensaje: 'Atendemos de 11:00 a. m. a 5:00 p. m. · Delivery sujeto a cobertura del local.' })
    await page.goto('/pedir/'+slug)
    await page.getByRole('heading', { name: 'Nuestra carta' }).waitFor()
    await page.getByRole('button', { name: 'Agregar Arroz con pato', exact: true }).waitFor()
    assert.equal(await page.getByText('Insumo privado QA').count(), 0)
    assert(await page.getByRole('button', { name: 'Agregar Juane de la casa', exact: true }).isDisabled())
    await page.screenshot({ path: path.join(output, '01-menu-escritorio.png'), fullPage: true })
    await page.getByRole('button', { name: 'Bebidas', exact: true }).click()
    await page.getByRole('button', { name: 'Agregar Jarra de maracuyá', exact: true }).click()
    await page.getByRole('button', { name: 'Todos', exact: true }).click()
    await page.getByRole('button', { name: 'Agregar Arroz con pato', exact: true }).click()
    await page.reload()
    assert.equal(await page.locator('.web-desktop-cart .web-cart-heading > span').innerText(), '2')
    await page.setViewportSize({ width: 320, height: 844 })
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await page.setViewportSize({ width: 390, height: 844 })
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await page.screenshot({ path: path.join(output, '02-menu-movil.png') })
    await page.getByRole('button', { name: /Ver mi pedido/ }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel('Nombre completo', { exact: true }).fill('Lucía Pedido Web')
    await dialog.getByLabel('Teléfono de contacto', { exact: true }).fill('999888777')
    await dialog.getByLabel('DNI para tu boleta', { exact: true }).fill(suffix)
    await dialog.getByLabel('Delivery', { exact: true }).check()
    await dialog.getByLabel('Dirección y referencia de entrega').fill('Av. Los Jardines 120, portón verde')
    await dialog.getByLabel('Comentario para el local').fill('Llamar al llegar')
    await dialog.locator('.web-cart-lines li').filter({ hasText: 'Arroz con pato' }).getByLabel('¿Alguna indicación?').fill('Sin ají')
    await dialog.screenshot({ path: path.join(output, '03-carrito-movil.png') })
    const originalProduct = (await call('GET', '/api/productos')).find(p => p.id === products[0].id)
    const changedProduct = await call('PUT', `/api/productos/${originalProduct.id}`, { ...originalProduct, precioVenta: 33.5 })
    await dialog.getByRole('button', { name: /Enviar pedido/ }).click()
    await dialog.getByRole('alert').getByText(/Cambió el importe/).waitFor()
    assert.equal((await call('GET', '/api/web/solicitudes')).filter(s => s.empresaId === company.id).length, 0)
    await call('PUT', `/api/productos/${changedProduct.id}`, { ...changedProduct, precioVenta: originalProduct.precioVenta })
    await Promise.all([page.waitForResponse(r => r.url().endsWith(`/tiendas/${slug}/menu`) && r.status() === 200), dialog.getByRole('button', { name: 'Actualizar carta', exact: true }).click()])
    assert.equal(await dialog.getByLabel('Nombre completo', { exact: true }).inputValue(), 'Lucía Pedido Web')
    let drop = true
    await page.route(`**/api/public/tiendas/${slug}/pedidos`, async route => {
      const response = await route.fetch()
      if (drop) { drop = false; await route.abort('failed') } else await route.fulfill({ response })
    })
    await dialog.getByRole('button', { name: /Enviar pedido/ }).click()
    await dialog.getByRole('button', { name: 'Comprobar / reintentar envío' }).waitFor()
    assert(await dialog.getByLabel('Nombre completo', { exact: true }).isDisabled())
    const pendingBefore = await call('GET', '/api/web/solicitudes')
    const request = pendingBefore.find(s => s.nombre === 'Lucía Pedido Web' && s.empresaId === company.id)
    assert(request)
    assert.equal(Number(request.total), 56.05)
    assert.equal((await call('GET', '/api/productos')).find(p => p.id === products[0].id).stockActual, 20)
    await page.reload()
    await page.getByRole('dialog').getByRole('button', { name: 'Comprobar / reintentar envío' }).click()
    await page.getByRole('heading', { name: 'Pedido recibido', exact: true }).waitFor()
    const pendingAfter = await call('GET', '/api/web/solicitudes')
    assert.equal(pendingAfter.filter(s => s.empresaId === company.id).length, 1)
    assert.equal(pendingAfter.find(s => s.empresaId === company.id).id, request.id)
    await page.screenshot({ path: path.join(output, '04-seguimiento-recibido.png') })
    await desk.goto('/')
    await desk.getByRole('button', { name: 'Pedidos online', exact: true }).click()
    const inbox = desk.getByRole('region', { name: 'Pedidos de la página web' })
    await inbox.locator('summary').click()
    await inbox.locator('.web-store-settings select').selectOption(String(company.id))
    await inbox.locator('.web-store-settings input[type=checkbox]').first().waitFor()
    assert.equal(await inbox.locator('.web-share-link a').getAttribute('href'), baseURL+'/pedir/'+slug)
    await inbox.screenshot({ path: path.join(output, '05-recepcion-caja.png') })
    const card = inbox.locator('.web-request-card').filter({ has: desk.getByRole('heading', { name: 'Lucía Pedido Web', exact: true }) })
    await card.getByRole('button', { name: 'Confirmar y enviar', exact: true }).click()
    await inbox.getByText(`${request.referencia} confirmado y enviado a Cocina / Bar.`, { exact: true }).waitFor()
    await page.getByRole('heading', { name: 'Pedido confirmado', exact: true }).waitFor({ timeout: 20000 })
    const sale = (await call('GET', '/api/pedidos-online')).find(s => s.empresa.id === company.id && s.origenPedido === 'WEB')
    assert(sale && sale.detalles.length === 2 && sale.pagos.length === 0)
    assert.equal((await call('GET', '/api/productos')).find(p => p.id === products[0].id).stockActual, 19)
    for (const item of sale.detalles) {
      const station = item.areaDestino === 'BAR' ? 'bar' : 'cocina'
      for (const [estadoActual, estado] of [['PENDIENTE','PREPARANDO'],['PREPARANDO','LISTO']]) await call('PATCH', `/api/${station}/items/${item.id}/estado`, { estadoActual, estado })
    }
    await page.getByRole('heading', { name: '¡Tu pedido está listo!', exact: true }).waitFor({ timeout: 20000 })
    await page.screenshot({ path: path.join(output, '06-seguimiento-listo.png') })
    assert(publicRequests.every(url => url.startsWith('/api/public/')), 'La página pública llamó al API interno')
    assert.equal((await customer.cookies()).length, 0)
    assert.deepEqual(errors, [])
    const result = { passed: true, viewport: [1440,390,320], errors, slug, companyId: company.id, requestId: request.id, saleId: sale.id,
      checks: ['menú sin login','categorías y agotados','carrito persistente','recojo y delivery','móvil sin desborde','cambio de precio exige actualizar sin perder contacto','envío con respuesta perdida y reintento después de recargar','una solicitud y una confirmación','stock y KDS Cocina/Bar','seguimiento recibido/confirmado/listo','publicación y enlace','sin cookies ni llamadas al POS privado'] }
    await fs.writeFile(path.join(output, 'resultado.json'), JSON.stringify(result, null, 2))
    console.log('PASS navegador menú público: '+result.checks.join(', '))
  } catch (error) { await page.screenshot({ path: path.join(output, 'fallo.png'), fullPage: true }).catch(() => {}); throw error }
  finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
