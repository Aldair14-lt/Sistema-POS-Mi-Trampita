const money = value => Number(value || 0).toFixed(2)
export default function Receipt({ sale }) {
  if (!sale) return null
  const command = sale.printType === 'COMANDA'
  const c = command ? null : sale.comprobante
  const details = c?.detalles || sale.detalles || []
  return <section className="print-only receipt-paper">
    <div className="receipt-header"><strong>{c?.empresaNombre || sale.empresa?.razonSocial || 'RECREO MI TRAMPITA'}</strong>
      {!command && <><span>RUC: {c?.empresaRuc || sale.empresa?.ruc}</span><span>{c?.empresaDireccion || sale.empresa?.direccion}</span></>}</div>
    <div className="receipt-title"><strong>{command ? 'COMANDA · PEDIDO COMPLETO' : c?.tipo || sale.tipoComprobante?.nombre}</strong>
      <span>{c?.numero || sale.numeroComprobante}</span><strong>{sale.origenPedido !== 'LOCAL' ? `Online · ${sale.tipoEntrega}` : `Mesa ${sale.mesa?.numero} · ${sale.mesa?.area?.nombre}`}</strong></div>
    <div className="receipt-customer"><span>Cliente: {c?.clienteNombre || sale.cliente?.nombresRazonSocial}</span>
      {!command && <span>Documento: {c?.clienteDocumento || sale.cliente?.numeroDocumento}</span>}
      <span>Fecha: {new Date((command ? sale.fechaVenta : c?.fechaEmision || sale.fechaCobro) || Date.now()).toLocaleString('es-PE')}</span></div>
    <table><thead><tr><th>Descripción</th><th>Cant.</th>{!command && <><th>P. unit.</th><th>Total</th></>}</tr></thead>
      <tbody>{details.map(detail => <tr key={detail.id}><td>{c ? detail.producto : detail.producto?.nombre}</td><td>{detail.cantidad}</td>
        {!command && <><td>S/ {money(detail.precioUnitario)}</td><td>S/ {money(detail.subtotal)}</td></>}</tr>)}</tbody></table>
    {!command && <><div className="receipt-totals"><span>Subtotal: S/ {money(c?.subtotal ?? sale.subtotal)}</span><span>IGV: S/ {money(c?.igv ?? sale.igv)}</span>
      <strong>Total: S/ {money(c?.total ?? sale.total)}</strong><span>Pagado: S/ {money(sale.totalPagado)}</span>
      <span>Saldo: S/ {money(sale.saldoPendiente)}</span></div>
      {(sale.pagos || []).map(pago => <p key={pago.id}>Abono: S/ {money(pago.monto)} · {pago.metodoPago}</p>)}
      <p>Gracias por su visita.</p></>}
  </section>
}
