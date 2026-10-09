import { useEffect, useMemo, useRef, useState } from 'react'
import { Globe, Plus, RefreshCw, Volume2, VolumeX, Printer } from 'lucide-react'
import { usePrint } from './PrintManager'
import OnlineOrderKanban from './OnlineOrderKanban'
import useOrderAlerts from '../hooks/useOrderAlerts'
import { api } from '../api'
import { permissions } from '../permissions'
import usePolling from '../hooks/usePolling'
import OrderStatus, { money, PaymentBadge } from './OrderStatus'
import UniversalPaymentModal from './UniversalPaymentModal'
import OnlineOrderForm from './OnlineOrderForm'
import WebOrdersInbox from './web/WebOrdersInbox'

export default function OnlineOrdersBoard({ session }) {
  const { canCharge, isAdmin } = permissions(session)
  const { data, error, loading, refresh } = usePolling('/api/pedidos-online')
  const [paymentId, setPaymentId] = useState(null)
  const [creating, setCreating] = useState(false)
  const [busy, setBusy] = useState(null)
  const [actionError, setActionError] = useState('')
  const [notification, setNotification] = useState('')
  const seen = useRef(null)
  const [closed, setClosed] = useState(null)
  const printDocument = usePrint()
  const readyKeys = useMemo(() => data?.flatMap(sale => sale.detalles.filter(item => item.estadoPreparacion === 'LISTO').map(item => item.id)), [data])
  const alerts = useOrderAlerts(readyKeys)
  const print = sale => printDocument({ type: 'RECEIPT', sale, format: sale.comprobante?.tipoComprobante === 'FACTURA' ? 'A4' : '80mm' })
  useEffect(() => {
    if (!data) return
    const ids = new Set(data.map(sale => sale.id))
    if (seen.current) {
      const incoming = data.filter(sale => !seen.current.has(sale.id))
      if (incoming.length) setNotification(`${incoming.length} nuevo(s) pedido(s) online recibido(s).`)
    }
    seen.current = ids
  }, [data])
  const serve = async item => {
    if (busy) return
    setBusy(item.id); setActionError('')
    try { await api.patch(`/api/ventas/items/${item.id}/servir`, { estado: 'SERVIDO', estadoActual: 'LISTO' }) }
    catch (err) { setActionError(err.message) }
    finally { await refresh(); setBusy(null) }
  }
  return <>
    <div className="page-heading compact"><div><span className="eyebrow">Operación / Recepción</span><h1><Globe size={30} /> Pedidos online</h1>
      <p className="muted">Recojo y delivery · los pedidos siguen activos hasta cobrar y entregar.</p></div>
      <div className="table-actions"><button className="secondary-button" onClick={refresh}><RefreshCw size={16} /> Actualizar</button>
        {canCharge && <button className="primary-button" disabled={creating} onClick={() => setCreating(true)}><Plus size={16} /> Recibir pedido</button>}</div></div>
    <div className="cashier-toolbar"><button className="secondary-button sound-button" aria-pressed={alerts.sound} onClick={alerts.toggleSound}>{alerts.sound ? <Volume2 size={16} /> : <VolumeX size={16} />}{alerts.sound ? 'Sonido activo' : 'Activar sonido'}</button></div>
    {alerts.incoming > 0 && <div className="operation-alert" role="status"><span>{alerts.incoming} plato(s) listo(s) para despachar.</span><button onClick={alerts.dismiss}>Entendido</button></div>}
    {closed && <div className="operation-alert"><span>{closed.comprobante.numero} emitida.</span><button onClick={() => print(closed)}><Printer size={15} />Imprimir</button></div>}
    {(actionError || error) && <div className="api-error" role="alert">{actionError || error}</div>}
    {notification && <div className="sale-success" role="status">{notification}<button className="secondary-button" onClick={() => setNotification('')}>Entendido</button></div>}
    <WebOrdersInbox isAdmin={isAdmin} onAccepted={refresh} />
    {creating && <OnlineOrderForm onCancel={() => setCreating(false)} onCreated={() => { setCreating(false); refresh(); setNotification('Pedido recibido y enviado a cocina.') }} />}
    {loading ? <div className="loading">Cargando recepción…</div> : <OnlineOrderKanban orders={data || []} renderCard={sale => <article className="panel kitchen-ticket" key={sale.id}>
        <div className="panel-head"><div><span className="eyebrow">{sale.origenPedido} · Pedido #{sale.id} · {new Date(sale.fechaVenta).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })}</span>
          <h3>{sale.tipoEntrega === 'DELIVERY' ? 'Delivery' : 'Recojo en local'}</h3></div><PaymentBadge sale={sale} /></div>
        <div className="online-customer"><strong>{sale.cliente.nombresRazonSocial}</strong>{(sale.telefonoEntrega || sale.cliente.telefono) && <span>{sale.telefonoEntrega || sale.cliente.telefono}</span>}
          {sale.direccionEnvio && <span>{sale.direccionEnvio}</span>}</div>
    {sale.observacionesPedido && <p className="web-request-note">{sale.observacionesPedido}</p>}
        <ul className="kitchen-items">{sale.detalles.map(item => <li key={item.id}><div><strong>{item.cantidad} × {item.producto.nombre}</strong><OrderStatus state={item.estadoPreparacion} /></div>
          {item.estadoPreparacion === 'LISTO' && <button className="primary-button" disabled={busy !== null} onClick={() => serve(item)}>Entregado</button>}</li>)}</ul>
        <div className="online-summary"><span>Total S/ {money(sale.total)}</span><strong>Saldo S/ {money(sale.saldoPendiente)}</strong></div>
        {canCharge && <button className="secondary-button full" onClick={() => setPaymentId(sale.id)}>Pagos e historial / finalizar</button>}
      </article>} />}
    {paymentId && <UniversalPaymentModal key={paymentId} saleId={paymentId} onClose={() => setPaymentId(null)} onUpdated={sale => { if (sale.comprobante) setClosed(sale); refresh() }} />}
  </>
}
