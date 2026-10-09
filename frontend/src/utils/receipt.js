/** Reglas compartidas por la toma de pedidos y el cobro. Los importes se revalidan en el servidor. */
export function receiptType(name = '') {
  const normalized = name.toUpperCase()
  if (normalized.includes('FACTURA')) return 'FACTURA'
  if (normalized.includes('BOLETA')) return 'BOLETA'
  if (normalized.includes('NOTA')) return 'NOTA_VENTA'
  return null
}

export function requiresIdentification(type, total, requested = false) {
  return type === 'FACTURA' || (type === 'BOLETA' && (Math.round(Number(total || 0) * 100) > 70000 || requested))
}

export function receiptLabel(name) {
  return { FACTURA: 'Factura', BOLETA: 'Boleta', NOTA_VENTA: 'Ticket simple' }[receiptType(name)] || 'Otro'
}
