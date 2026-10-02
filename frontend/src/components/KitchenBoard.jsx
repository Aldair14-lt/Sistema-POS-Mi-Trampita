import { useMemo, useState } from 'react'
import { ChefHat, RefreshCw, Clock } from 'lucide-react'
import { api } from '../api'
import usePolling from '../hooks/usePolling'
import OrderStatus from './OrderStatus'

export default function KitchenBoard() {
  const { data, error, loading, refresh } = usePolling('/api/cocina/items')
  const [busy, setBusy] = useState(null)
  const [actionError, setActionError] = useState('')
  const tickets = useMemo(() => {
    const grouped = new Map()
    for (const item of data || []) {
      if (!grouped.has(item.ventaId)) grouped.set(item.ventaId, { ...item, items: [] })
      grouped.get(item.ventaId).items.push(item)
    }
    return [...grouped.values()] // La API ya ordena por fecha de cada tanda.
  }, [data])
  const update = async (item) => {
    if (busy) return
    setBusy(item.id); setActionError('')
    try {
      await api.patch(`/api/cocina/items/${item.id}/estado`, {
        estado: item.estadoPreparacion === 'PENDIENTE' ? 'EN_PREPARACION' : 'LISTO',
        estadoActual: item.estadoPreparacion
      })
    } catch (err) { setActionError(err.message) }
    finally { await refresh(); setBusy(null) }
  }
  return <>
    <div className="page-heading compact"><div><span className="eyebrow">Operación / KDS</span>
      <h1><ChefHat size={30} /> Cocina</h1><p className="muted">Comandas en orden de llegada · actualización cada 5 segundos.</p></div>
      <button className="secondary-button" onClick={refresh}><RefreshCw size={16} /> Actualizar</button></div>
    {(error || actionError) && <div className="api-error" role="alert">{actionError || error}</div>}
    {loading ? <div className="loading">Cargando cocina…</div> : <div className="kitchen-grid">
      {tickets.map(ticket => <article className="panel kitchen-ticket" key={ticket.ventaId}>
        <div className="panel-head"><div><span className="eyebrow">Pedido #{ticket.ventaId}</span>
          <h3>{ticket.origenPedido === 'ONLINE' ? `Online · ${ticket.tipoEntrega === 'DELIVERY' ? 'Delivery' : 'Recojo'}` : `Mesa ${ticket.mesaNumero}`}</h3></div>
          <span className="ticket-time"><Clock size={14} /> {new Date(ticket.fechaPedido).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })}</span></div>
        <ul className="kitchen-items">{ticket.items.map(item => <li key={item.id}>
          <div><strong>{item.cantidad} × {item.producto}</strong><OrderStatus state={item.estadoPreparacion} />
            <small>Enviado {new Date(item.fechaPedido).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })}</small></div>
          <button className={item.estadoPreparacion === 'PENDIENTE' ? 'secondary-button' : 'primary-button'}
            disabled={busy !== null} onClick={() => update(item)}>
            {busy === item.id ? 'Guardando…' : item.estadoPreparacion === 'PENDIENTE' ? 'Preparando' : 'Listo'}</button>
        </li>)}</ul>
      </article>)}
      {!tickets.length && <div className="panel empty">No hay platos pendientes. Las nuevas comandas aparecerán aquí.</div>}
    </div>}
  </>
}
