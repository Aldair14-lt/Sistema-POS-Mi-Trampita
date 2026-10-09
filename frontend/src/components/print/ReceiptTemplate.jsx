import PrintHeader, { money, date } from './PrintHeader'
export default function ReceiptTemplate({ sale }) {
  const c = sale.comprobante
  return <><PrintHeader name={c.empresaNombre} ruc={c.empresaRuc} address={c.empresaDireccion} />
    <div className="print-document-title"><h1>{c.tipoComprobante?.replaceAll('_', ' ') || c.tipo}</h1><strong>{c.numero}</strong></div>
    <div className="print-customer"><span>Fecha: {date(c.fechaEmision)}</span><strong>Cliente: {c.clienteNombre}</strong>{c.clienteDocumento && <span>{c.ruc ? 'RUC' : c.dni ? 'DNI' : 'Documento'}: {c.clienteDocumento}</span>}{c.clienteDireccion && <span>Dirección: {c.clienteDireccion}</span>}<span>{sale.mesa ? `Mesa ${sale.mesa.numero}` : `Pedido #${sale.id} · ${sale.tipoEntrega}`}</span></div>
    <table><thead><tr><th>Descripción</th><th>Cant.</th><th>P. unit.</th><th>Importe</th></tr></thead><tbody>{c.detalles.map(d => <tr key={d.id}><td>{d.producto}</td><td>{d.cantidad}</td><td>{money(d.precioUnitario)}</td><td>{money(d.subtotal)}</td></tr>)}</tbody></table>
    <div className="print-totals"><span>Subtotal: S/ {money(c.subtotal)}</span><span>IGV: S/ {money(c.igv)}</span><strong>TOTAL: S/ {money(c.total)}</strong></div>
    <div className="print-payments"><h2>Métodos de pago</h2>{sale.pagos.map(p => <p key={p.id}><span>{p.metodoPago} · S/ {money(p.monto)}</span><span>Recibido: S/ {money(p.montoRecibido)}</span><span>Vuelto: S/ {money(p.vuelto)}</span></p>)}</div>
    <div className="print-signatures"><span>Firma de recepción</span><span>Sello</span></div>
    <footer className="print-footer"><p>{c.tipoComprobante === 'NOTA_VENTA' ? 'NOTA DE VENTA · DOCUMENTO INTERNO' : 'REGISTRO INTERNO · SIN VALIDACIÓN SUNAT'}</p><p>Gracias por visitar Recreo Mi Trampita.</p></footer>
  </>
}
