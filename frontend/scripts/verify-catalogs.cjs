/* Exclusivamente sobre Vite y backend conectados a una base QA local. */
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const path = require('node:path')
const baseURL = process.env.POS_UI_URL
assert(/^http:\/\/(127\.0\.0\.1|localhost):/.test(baseURL || ''), 'Define POS_UI_URL para Vite QA local')
assert(process.env.POS_TEST_PASSWORD, 'Define POS_TEST_PASSWORD')
assert(process.env.POS_VALIDATION_DIR, 'Define POS_VALIDATION_DIR')

async function main() {
  const output = process.env.POS_VALIDATION_DIR
  await fs.mkdir(output, { recursive: true })
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const context = await browser.newContext({ baseURL, viewport: { width: 1440, height: 1100 } })
  const page = await context.newPage(), errors = [], checked = [], created = {}
  page.on('pageerror', e => errors.push(e.message))
  page.on('dialog', d => d.accept())
  const call = async (url, data) => {
    const csrf = await (await context.request.get('/api/auth/csrf')).json()
    const response = await context.request.post(url, { data, headers: { [csrf.headerName]: csrf.token } })
    assert(response.ok(), `${url}: ${response.status()}`)
    return response.json()
  }
  const nav = async label => {
    await page.locator('.sidebar').getByRole('button', { name: label, exact: true }).first().click()
    await page.getByRole('heading', { level: 1, name: label, exact: true }).waitFor()
    await page.locator('.result-count').waitFor()
  }
  const save = async (endpoint, method) => {
    const [response] = await Promise.all([
      page.waitForResponse(r => new URL(r.url()).pathname === endpoint && r.request().method() === method),
      page.getByRole('dialog').getByRole('button', { name: 'Guardar', exact: true }).click()
    ])
    assert(response.ok(), `${method} ${endpoint}: ${response.status()} ${await response.text()}`)
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    return response.json()
  }
  const suffix = Date.now().toString().slice(-8)
  const definitions = [
    ['Categorías', 'categorias', { nombre: `UI categoría ${suffix}`, descripcion: 'Descripción UI' }, 'nombre'],
    ['Marcas', 'marcas', { nombre: `UI marca ${suffix}` }, 'nombre'],
    ['Proveedores', 'proveedores', { rucDni: `UI-${suffix}`, razonSocial: `UI proveedor ${suffix}`, correo: 'ui@example.com' }, 'razonSocial'],
    ['Clientes', 'clientes', { numeroDocumento: `UI-${suffix}`, nombresRazonSocial: `UI cliente ${suffix}`, correo: 'ui@example.com', fechaNacimiento: '1995-10-09' }, 'nombresRazonSocial'],
    ['Configuración', 'configuracion', { ruc: `UI-${suffix}`, razonSocial: `UI empresa ${suffix}`, direccion: 'Dirección UI' }, 'razonSocial'],
    ['Comprobantes', 'tipos-comprobante', { nombre: 'NOTA DE VENTA', serie: `U${suffix.slice(-6)}`, descripcion: 'Descripción UI' }, 'serie'],
    ['Áreas', 'areas', { nombre: `UI área ${suffix}` }, 'nombre'],
    ['Mesas', 'mesas', {}, 'numero'],
    ['Productos', 'productos', { codigoBarras: `UI-${suffix}`, nombre: `UI producto ${suffix}`, precioCompra: '2.00', precioVenta: '8.50', stockActual: '20', stockMinimo: '2' }, 'nombre']
  ]
  try {
    await call('/api/auth/login', { usuario: 'admin', contrasena: process.env.POS_TEST_PASSWORD })
    await page.goto('/')
    for (const [label, resource, values, display] of definitions) {
      await nav(label)
      await page.getByRole('button', { name: /^Nuevo / }).click()
      const dialog = page.getByRole('dialog')
      for (const [field, value] of Object.entries(values)) await dialog.locator(`input[name="${field}"]`).fill(value)
      if (resource === 'mesas') {
        const tables = await (await context.request.get('/api/mesas')).json()
        values.numero = String(Math.max(...tables.map(t => t.numero)) + 1)
        await dialog.locator('[name="numero"]').fill(values.numero)
        await dialog.locator('[name="areaId"]').selectOption(String(created.areas.record.id))
      }
      if (resource === 'productos') {
        for (const [field, ref] of [['categoriaId', 'categorias'], ['marcaId', 'marcas'], ['proveedorId', 'proveedores']]) {
          await dialog.locator(`[name="${field}"]`).selectOption(String(created[ref].record.id))
        }
      }
      const record = await save('/api/' + resource, 'POST')
      created[resource] = { record, label, display, value: String(record[display]) }
      await page.locator('.search-box input').fill(String(record[display]))
      const row = page.getByRole('table', { name: label, exact: true }).locator('tbody tr').filter({ hasText: String(record[display]) })
      await row.first().waitFor()
      assert.equal(await row.count(), 1)
      await row.getByRole('button', { name: /^Modificar / }).click()
      const editedValue = resource === 'mesas' ? String(Number(values.numero) + 100) :
        resource === 'tipos-comprobante' ? `E${suffix.slice(-6)}` : `Editado ${resource} ${suffix}`
      await page.getByRole('dialog').locator(`[name="${display}"]`).fill(editedValue)
      const edited = await save('/api/' + resource + '/' + record.id, 'PUT')
      assert.equal(String(edited[display]), editedValue)
      created[resource].value = editedValue
      await page.locator('.search-box input').fill(editedValue)
      await page.getByRole('table', { name: label, exact: true }).getByText(editedValue, { exact: true }).waitFor()
      checked.push(label + ': crear, buscar y editar')
    }
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({ path: path.join(output, '01-productos-movil.png'), fullPage: true })
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
    await page.setViewportSize({ width: 1440, height: 1100 })
    for (const [resource, entry] of Object.entries(created).reverse()) {
      await nav(entry.label)
      await page.locator('.search-box input').fill(entry.value)
      const [response] = await Promise.all([
        page.waitForResponse(r => new URL(r.url()).pathname === `/api/${resource}/${entry.record.id}` && r.request().method() === 'DELETE'),
        page.getByRole('table', { name: entry.label, exact: true }).locator('tbody tr').getByRole('button', { name: /^Eliminar / }).click()
      ])
      assert.equal(response.status(), 204)
      await page.getByText('No hay registros para mostrar.', { exact: true }).waitFor()
      checked.push(entry.label + ': eliminar')
    }
    await nav('Roles')
    assert.equal(await page.getByRole('button', { name: /^Nuevo / }).count(), 0)
    await page.getByRole('table', { name: 'Roles' }).locator('tbody tr').nth(4).waitFor()
    assert.equal(await page.getByRole('table', { name: 'Roles' }).locator('tbody tr').count(), 5)
    await page.locator('.sidebar').getByRole('button', { name: 'Inicio', exact: true }).click()
    await page.getByRole('heading', { name: /Buen día/ }).waitFor()
    await page.screenshot({ path: path.join(output, '02-inicio.png'), fullPage: true })
    assert.deepEqual(errors, [])
    await fs.writeFile(path.join(output, 'resultado.json'), JSON.stringify({ passed: true, checked, errors }, null, 2))
    console.log('PASS UI catálogos: crear, editar, buscar y eliminar nueve recursos; Inicio, Roles y productos en móvil.')
  } catch (e) {
    await page.screenshot({ path: path.join(output, 'fallo.png'), fullPage: true }).catch(() => {})
    throw e
  } finally { await browser.close() }
}
main().catch(e => { console.error(e); process.exitCode = 1 })
