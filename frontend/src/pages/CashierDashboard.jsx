import { useMemo, useState } from 'react'
import { BadgeDollarSign, ReceiptText, Plus, Printer, RefreshCw, Volume2, VolumeX, Truck, Utensils } from 'lucide-react'
import { usePrint } from '../components/PrintManager'
import CashRegister from '../components/CashRegister'
import WebOrdersInbox from '../components/web/WebOrdersInbox'
import { permissions } from '../permissions'
import OnlineOrderKanban from '../components/OnlineOrderKanban'
import { api } from '../api'
import usePolling from '../hooks/usePolling'
import useOrderAlerts from '../hooks/useOrderAlerts'
import WhatsAppOrderForm from '../components/WhatsAppOrderForm'
import PaymentModal from '../components/cashier/PaymentModal'
import CancelItemButton from '../components/CancelItemButton'
import OrderStatus, { money, PaymentBadge } from '../components/OrderStatus'
import '../styles/cashier.css'

export default function CashierDashboard({ session }) {
  const { isAdmin } = permissions(session)
  const { data, loading, error, refresh } = usePolling('/api/caja/resumen')
  const [creating, setCreating] = useState(false)
  const [paymentId, setPaymentId] = useState(null)
  const [busy, setBusy] = useState(null)
  const [actionError, setActionError] = useState('')
  const [lastReceipt, setLastReceipt] = useState(null)
  const [printFormat, setPrintFormat] = useState('auto')
  const printDocument = usePrint()
  const keys = useMemo(() => data && [
    ...data.mesasPorCobrar.map(sale => `cuenta-${sale.id}`), ...data.pedidosOnline.map(sale => `online-${sale.id}`),
    ...[...data.mesasPorCobrar, ...data.otrasMesas, ...data.pedidosOnline].flatMap(sale => sale.detalles.filter(item => item.estadoPreparacion === 'LISTO').map(item => `listo-${item.id}`))
  ], [data])
  const alerts = useOrderAlerts(keys)
  const print = sale => printDocument({ type: 'RECEIPT', sale, format: printFormat === 'auto' ? sale.comprobante?.tipoComprobante === 'FACTURA' ? 'A4' : '80mm' : printFormat })
  const serve = async item => {
    if (busy !== null) return
    setBusy(item.id); setActionError('')
    try { await api.patch(`/api/ventas/items/${item.id}/servir`, { estado: 'SERVIDO', estadoActual: 'LISTO' }) }
    catch (err) { setActionError(err.message) }
    finally { await refresh(); setBusy(null) }
  }
  const renderCard = sale => <article className="cashier-card" key={sale.id}>
    <header><div><span className="eyebrow">CUENTA #{sale.id} · {new Date(sale.fechaVenta).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })}</span><h3>{sale.mesa ? `Mesa ${String(sale.mesa.numero).padStart(2, '0')}` : `${sale.origenPedido} · ${sale.tipoEntrega === 'DELIVERY' ? 'Delivery' : 'Recojo'}`}</h3><p>{sale.mesa?.area?.nombre || (sale.cliente?.nombresRazonSocial || sale.comprobante?.clienteNombre || 'Consumidor final')}</p></div><PaymentBadge sale={sale} /></header>
    {!sale.mesa && <div className="cashier-delivery">{(sale.telefonoEntrega || sale.cliente?.telefono) && <span>{sale.telefonoEntrega || sale.cliente?.telefono}</span>}{sale.direccionEnvio && <span>{sale.direccionEnvio}</span>}</div>}
    {sale.observacionesPedido && <p className="web-request-note">{sale.observacionesPedido}</p>}
    <ul className="cashier-items">{sale.detalles.map(item => <li key={item.id}><div><strong>{item.cantidad} × {item.producto.nombre}</strong><OrderStatus state={item.estadoPreparacion} />{item.motivoCancelacion && <small>{item.motivoCancelacion}</small>}</div>{item.estadoPreparacion === 'LISTO' && <button className="secondary-button" disabled={busy !== null} onClick={() => serve(item)}>Despachado</button>}{sale.estado === 'ABIERTA' && <CancelItemButton item={item} onUpdated={refresh} />}</li>)}</ul>
    <div className="cashier-card-total"><span>Total <b>S/ {money(sale.total)}</b></span><span>Saldo <strong>S/ {money(sale.saldoPendiente)}</strong></span></div>
    <button className="primary-button full" onClick={() => setPaymentId(sale.id)}><BadgeDollarSign size={17} />{sale.estado === 'CERRADA' ? 'Ver pagos y comprobante' : Number(sale.saldoPendiente) === 0 ? 'Emitir comprobante' : 'Cobrar / registrar abono'}</button>
    {sale.comprobante && <button className="secondary-button full" onClick={() => print(sale)}><Printer size={15} />Imprimir {sale.comprobante.numero}</button>}
  </article>
  return <>
    <div className="page-heading compact cashier-heading"><div><span className="eyebrow">MI TRAMPITA · CAJA Y RECEPCIÓN</span><h1>Cuentas claras, buen servicio.</h1><p className="muted">Cobros, adelantos y pedidos de WhatsApp en un solo lugar.</p></div><button className="primary-button" disabled={creating} onClick={() => setCreating(true)}><Plus size={17} />Pedido WhatsApp</button></div>
    <div className="cashier-toolbar"><button className="secondary-button sound-button" aria-pressed={alerts.sound} onClick={alerts.toggleSound}>{alerts.sound ? <Volume2 size={16} /> : <VolumeX size={16} />}{alerts.sound ? 'Sonido activo' : 'Activar sonido'}</button><button className="secondary-button" onClick={refresh}><RefreshCw size={15} />Actualizar</button></div>
    <CashRegister />
    <WebOrdersInbox isAdmin={isAdmin} onAccepted={refresh} />
    {(error || actionError) && <div className="api-error" role="alert">{actionError || error}</div>}
    {alerts.incoming > 0 && <div className="operation-alert" role="status"><span>{alerts.incoming} novedad{alerts.incoming !== 1 ? 'es' : ''}: cuentas solicitadas, pedidos nuevos o platos listos.</span><button onClick={alerts.dismiss}>Entendido</button></div>}
    {lastReceipt && <div className="operation-alert" role="status"><span>{lastReceipt.comprobante?.tipoComprobante} {lastReceipt.comprobante?.numero} emitida.</span><button onClick={() => print(lastReceipt)}><Printer size={15} />Imprimir comprobante</button></div>}
    {creating && <WhatsAppOrderForm onCancel={() => setCreating(false)} onCreated={() => { setCreating(false); refresh() }} />}
    {loading ? <div className="loading">Cargando caja…</div> : data && <>
      <div className="cashier-metrics"><div><span>Mesas por cobrar</span><strong>{data.mesasPorCobrar.length}</strong><small>Cuenta solicitada por el mozo</small></div><div><span>Pedidos online activos</span><strong>{data.pedidosOnline.length}</strong><small>WhatsApp, Web y recepción</small></div><div><span>Saldo por cobrar</span><strong>S/ {money([...data.mesasPorCobrar, ...data.pedidosOnline].reduce((sum, sale) => sum + Number(sale.saldoPendiente), 0))}</strong><small>Mesas solicitadas y online</small></div></div>
      <div className="cashier-columns"><section className="cashier-column"><header className="cashier-column-head"><span><Utensils size={19} />Mesas por Cobrar</span><b>{data.mesasPorCobrar.length}</b></header>{data.mesasPorCobrar.map(renderCard)}{!data.mesasPorCobrar.length && <div className="cashier-empty"><ReceiptText size={28} /><p>Todo al día en el salón.</p><small>Cuando el mozo solicite la cuenta, aparecerá aquí.</small></div>}
        {data.otrasMesas.length > 0 && <details className="cashier-other-tables"><summary>Mesas consumiendo · abonos anticipados ({data.otrasMesas.length})</summary>{data.otrasMesas.map(renderCard)}</details>}
      </section><section className="cashier-column online"><header className="cashier-column-head"><span><Truck size={19} />Pedidos Online / Delivery</span><b>{data.pedidosOnline.length}</b></header><OnlineOrderKanban orders={data.pedidosOnline} renderCard={renderCard} />{!data.pedidosOnline.length && <div className="cashier-empty"><Truck size={28} /><p>Sin pedidos pendientes.</p><small>Registra el siguiente pedido desde WhatsApp.</small></div>}</section></div>
      {data.comprobantesRecientes.length > 0 && <section className="cashier-receipts"><h2>Comprobantes recientes</h2>{data.comprobantesRecientes.map(sale => <div key={sale.id}><span><strong>{sale.comprobante?.numero || sale.numeroComprobante}</strong><small>{(sale.cliente?.nombresRazonSocial || sale.comprobante?.clienteNombre || 'Consumidor final')}</small></span><b>S/ {money(sale.total)}</b><button className="secondary-button" onClick={() => print(sale)} aria-label={`Imprimir comprobante de la venta ${sale.id}`}><Printer size={16} /></button></div>)}</section>}
    </>}
    <label>Formato para reimpresión<select value={printFormat} onChange={e => setPrintFormat(e.target.value)}><option value="auto">Automático: factura A4, ticket 80 mm</option><option value="80mm">Térmico 80 mm</option><option value="A4">A4</option></select></label>
    {paymentId && <PaymentModal key={paymentId} saleId={paymentId} onClose={() => setPaymentId(null)} onUpdated={sale => { if (sale.comprobante) setLastReceipt(sale); refresh() }} />}
  </>
}
