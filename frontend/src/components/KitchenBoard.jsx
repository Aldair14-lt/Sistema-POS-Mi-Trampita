import { useEffect, useMemo, useState } from 'react'
import { ChefHat, RefreshCw, Clock3, Volume2, VolumeX, Flame, CircleCheck } from 'lucide-react'
import { api } from '../api'
import usePolling from '../hooks/usePolling'
import useOrderAlerts from '../hooks/useOrderAlerts'
import '../styles/kitchen.css'

const time = date => new Date(date).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })
export default function KitchenBoard() {
  const { data, error, loading, refresh } = usePolling('/api/cocina/items')
  const [busy, setBusy] = useState(null)
  const [actionError, setActionError] = useState('')
  const [now, setNow] = useState(Date.now())
  const keys = useMemo(() => data?.filter(item => item.estadoPreparacion === 'PENDIENTE').map(item => item.id), [data])
  const alerts = useOrderAlerts(keys)
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 30000); return () => clearInterval(timer) }, [])
  const tickets = useMemo(() => {
    const grouped = new Map()
    for (const item of data || []) {
      const state = item.estadoPreparacion === 'PENDIENTE' ? 'PENDIENTE' : 'PREPARANDO'
      const key = `${item.ventaId}-${state}`
      if (!grouped.has(key)) grouped.set(key, { ...item, key, state, items: [] })
      grouped.get(key).items.push(item)
    }
    return [...grouped.values()].sort((a, b) => new Date(a.fechaPedido) - new Date(b.fechaPedido) || a.id - b.id)
  }, [data])
  const update = async item => {
    if (busy !== null) return
    setBusy(item.id); setActionError('')
    try { await api.patch(`/api/cocina/items/${item.id}/estado`, {
      estado: item.estadoPreparacion === 'PENDIENTE' ? 'PREPARANDO' : 'LISTO', estadoActual: item.estadoPreparacion
    }) } catch (err) { setActionError(err.message) }
    finally { await refresh(); setBusy(null) }
  }
  return <section className="kds-screen">
    <header className="kds-header"><div><span className="kds-eyebrow"><ChefHat size={16} /> MI TRAMPITA · COCINA</span><h1>Cada plato, en su momento.</h1><p>Comandas por orden de llegada · conexión en vivo</p></div>
      <div className="kds-controls"><button type="button" aria-pressed={alerts.sound} onClick={alerts.toggleSound}>{alerts.sound ? <Volume2 size={18} /> : <VolumeX size={18} />}{alerts.sound ? 'Sonido activo' : 'Activar sonido'}</button><button type="button" onClick={refresh} aria-label="Actualizar cocina"><RefreshCw size={18} /></button></div></header>
    {(error || actionError) && <div className="kds-error" role="alert">{actionError || error}</div>}
    {alerts.incoming > 0 && <div className="kds-alert" role="status"><span>● {alerts.incoming} nuevo{alerts.incoming !== 1 ? 's' : ''} plato{alerts.incoming !== 1 ? 's' : ''} en cocina</span><button onClick={alerts.dismiss}>Entendido</button></div>}
    {loading ? <p className="kds-empty">Cargando comandas…</p> : <div className="kds-kanban">{[['PENDIENTE', 'Por preparar', Clock3], ['PREPARANDO', 'En preparación', Flame]].map(([state, label, Icon]) => {
      const column = tickets.filter(ticket => ticket.state === state)
      return <section className={`kds-column ${state.toLowerCase()}`} key={state} aria-label={label}>
        <header className="kds-column-title"><span><Icon size={19} />{label}</span><b>{column.reduce((sum, ticket) => sum + ticket.items.length, 0)}</b></header>
        <div className="kds-ticket-list">{column.map(ticket => {
          const minutes = Math.max(0, Math.floor((now - new Date(ticket.fechaPedido)) / 60000))
          return <article className={`kds-ticket ${minutes >= 20 ? 'overdue' : ''}`} key={ticket.key}>
            <header><div><span className="kds-ticket-id">COMANDA #{ticket.ventaId}</span><h2>{ticket.origenPedido === 'LOCAL' ? `Mesa ${String(ticket.mesaNumero).padStart(2, '0')}` : `${ticket.origenPedido === 'WHATSAPP' ? 'WhatsApp' : ticket.origenPedido === 'WEB' ? 'Web' : 'Online'} · ${ticket.tipoEntrega === 'DELIVERY' ? 'Delivery' : 'Recojo'}`}</h2></div><span className="kds-age"><Clock3 size={13} />{minutes} min</span></header>
            <ul>{ticket.items.map(item => <li key={item.id}><span className="kds-quantity">{item.cantidad}</span><div className="kds-dish"><strong>{item.producto}</strong><small>Recibido {time(item.fechaPedido)}</small></div><button type="button" disabled={busy !== null} onClick={() => update(item)} className={state === 'PREPARANDO' ? 'kds-ready-button' : ''}>{busy === item.id ? 'Guardando…' : state === 'PENDIENTE' ? <><Flame size={16} />Preparando</> : <><CircleCheck size={16} />Listo</>}</button></li>)}</ul>
            <footer>{state === 'PENDIENTE' ? 'Inicia la preparación de cada plato' : 'Al marcar Listo, Mozo y Caja reciben el aviso'}</footer>
          </article>
        })}{!column.length && <div className="kds-empty"><CircleCheck size={28} /><p>{state === 'PENDIENTE' ? 'Todo al día.' : 'Sin platos en preparación.'}</p><small>Las comandas aparecerán aquí.</small></div>}</div>
      </section>
    })}</div>}
  </section>
}
