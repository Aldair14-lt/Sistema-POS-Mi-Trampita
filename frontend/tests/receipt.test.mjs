import test from 'node:test'
import assert from 'node:assert/strict'
import { receiptType, receiptLabel, requiresIdentification } from '../src/utils/receipt.js'

test('el catálogo distingue ticket interno, boleta y factura', () => {
  assert.equal(receiptType('Nota de venta'), 'NOTA_VENTA')
  assert.equal(receiptLabel('NOTA DE VENTA'), 'Ticket simple')
  assert.equal(receiptType('Boleta de venta'), 'BOLETA')
  assert.equal(receiptType('Factura'), 'FACTURA')
  assert.equal(receiptType('Desconocido'), null)
})

test('la identificación depende del documento y del total en céntimos', () => {
  assert.equal(requiresIdentification('NOTA_VENTA', 1000), false)
  assert.equal(requiresIdentification('BOLETA', 700), false)
  assert.equal(requiresIdentification('BOLETA', 700.01), true)
  assert.equal(requiresIdentification('BOLETA', 10, true), true)
  assert.equal(requiresIdentification('FACTURA', 10), true)
})
