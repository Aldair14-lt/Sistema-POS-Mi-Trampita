import { PrintableDocument } from './components/PrintManager'

/** Compatibilidad con consumidores anteriores; usa las mismas plantillas del gestor. */
export default function Receipt({ sale }) {
  if (!sale) return null
  const command = sale.printType === 'COMANDA'
  if (!command && !sale.comprobante) return null
  const format = command ? '80mm' : sale.printFormat || (sale.comprobante.tipoComprobante === 'FACTURA' ? 'A4' : '80mm')
  return <PrintableDocument job={{ type: command ? 'COMMAND' : 'RECEIPT', format, sale, area: sale.areaDestino }} />
}
