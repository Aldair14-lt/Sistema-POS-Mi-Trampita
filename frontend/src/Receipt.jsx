const money = value => Number(value || 0).toFixed(2)
export default function Receipt({ sale }) {
  if (!sale) return null
  const command = sale.printType === 'COMANDA'
  return <section className="print-only receipt-paper">
    <div className="receipt-header"><strong>{sale.empresa?.razonSocial || 'RECREO MI TRAMPITA'}</strong>
      {!command && <><span>RUC: {sale.empresa?.ruc}</span><span>{sale.empresa?.direccion}</span></>}</div>
    <div className="receipt-title"><strong>{command ? 'COMANDA · PEDIDO COMPLETO' : sale.tipoComprobante?.nombre}</strong>
      <span>{sale.numeroComprobante}</span><strong>{sale.origenPedido === 'ONLINE' ? `Online · ${sale.tipoEntrega}` : `Mesa ${sale.mesa?.numero} · ${sale.mesa?.area?.nombre}`}</strong></div>
    <div className="receipt-customer"><span>Cliente: {sale.cliente?.nombresRazonSocial}</span>
      {!command && <span>Documento: {sale.cliente?.numeroDocumento}</span>}
      <span>Fecha: {new Date((command ? sale.fechaVenta : sale.fechaCobro) || Date.now()).toLocaleString('es-PE')}</span></div>
    <table><thead><tr><th>Descripción</th><th>Cant.</th>{!command && <><th>P. unit.</th><th>Total</th></>}</tr></thead>
      <tbody>{(sale.detalles || []).map(detail => <tr key={detail.id}><td>{detail.producto?.nombre}</td><td>{detail.cantidad}</td>
        {!command && <><td>S/ {money(detail.precioUnitario)}</td><td>S/ {money(detail.subtotal)}</td></>}</tr>)}</tbody></table>
    {!command && <><div className="receipt-totals"><span>Subtotal: S/ {money(sale.subtotal)}</span><span>IGV: S/ {money(sale.igv)}</span>
      <strong>Total: S/ {money(sale.total)}</strong><span>Pagado: S/ {money(sale.totalPagado)}</span>
      <span>Saldo: S/ {money(sale.saldoPendiente)}</span></div>
      {(sale.pagos || []).map(pago => <p key={pago.id}>Abono: S/ {money(pago.monto)} · {pago.metodoPago}</p>)}
      <p>Gracias por su visita.</p></>}
  </section>
}
