import '../styles/online.css'
const columns = [['RECIBIDO', 'Recibidos'], ['PREPARANDO', 'En preparación'], ['LISTO', 'Listos para entregar'], ['ENTREGADO', 'Entregados · por cobrar']]
export function onlineStage(sale) {
  const items = sale.detalles.filter(d => d.estadoPreparacion !== 'CANCELADO')
  if (items.length && items.every(d => d.estadoPreparacion === 'SERVIDO')) return 'ENTREGADO'
  if (items.length && items.every(d => ['LISTO', 'SERVIDO'].includes(d.estadoPreparacion))) return 'LISTO'
  return items.some(d => d.estadoPreparacion !== 'PENDIENTE') ? 'PREPARANDO' : 'RECIBIDO'
}
export default function OnlineOrderKanban({ orders, renderCard }) {
  return <div className="online-kanban">{columns.map(([state, label]) => {
    const sales = orders.filter(s => onlineStage(s) === state)
    return <section className={`online-kanban-column state-${state.toLowerCase()}`} aria-label={label} key={state}><h3>{label}<span>{sales.length}</span></h3>{sales.map(renderCard)}{!sales.length && <p className="muted online-empty">Sin pedidos</p>}</section>
  })}</div>
}
