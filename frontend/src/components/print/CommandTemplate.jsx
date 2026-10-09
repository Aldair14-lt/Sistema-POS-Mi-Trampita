import PrintHeader, { date } from './PrintHeader'
export default function CommandTemplate({ sale, area }) {
  const details = sale.detalles.filter(d => d.estadoPreparacion !== 'CANCELADO' && (!area || d.areaDestino === area))
  return <div className="print-command"><PrintHeader name={sale.empresa?.nombreComercial || sale.empresa?.razonSocial} />
    <h1>COMANDA · {area || 'PEDIDO COMPLETO'}</h1><h2>{sale.mesa ? `MESA ${sale.mesa.numero}` : `${sale.tipoEntrega} #${sale.id}`}</h2>
    <p>Pedido #{sale.id}<br />Fecha: {date(sale.fechaVenta)}<br />Mozo / recepción: {sale.usuario?.nombreCompleto}</p>
    {sale.observacionesPedido && <p>Indicaciones del pedido: {sale.observacionesPedido}</p>}
    <ol>{details.map(d => <li key={d.id}><strong>{d.cantidad} × {d.producto.nombre}</strong>{d.observaciones && <p>OBS: {d.observaciones}</p>}<small>{date(d.fechaPedido)}</small></li>)}</ol>
    {!details.length && <p>Sin ítems para esta estación.</p>}
  </div>
}
