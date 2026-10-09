import { useEffect, useState } from 'react'
import { Globe, Check, X, RefreshCw } from 'lucide-react'
import { api } from '../../api'
import usePolling from '../../hooks/usePolling'
import useOrderAlerts from '../../hooks/useOrderAlerts'
import { money } from '../OrderStatus'
import WebStoreSettings from './WebStoreSettings'
import '../../styles/web-orders.css'

export default function WebOrdersInbox({ isAdmin, onAccepted }) {
  const { data, error, loading, refresh } = usePolling('/api/web/solicitudes')
  const [types, setTypes] = useState([]); const [type, setType] = useState(''); const [busy, setBusy] = useState(null)
  const [actionError, setActionError] = useState(''); const [message, setMessage] = useState(''); const [rejection, setRejection] = useState(null); const [reason, setReason] = useState('')
  const alerts = useOrderAlerts(data?.map(s => `web-${s.id}`))
  useEffect(() => { let active = true; api.list('/api/tipos-comprobante').then(rows => { if (!active) return; const allowed = rows.filter(t => !/factura/i.test(t.nombre)); setTypes(allowed); setType(String(allowed.find(t => /boleta/i.test(t.nombre))?.id || allowed[0]?.id || '')) }).catch(e => active && setActionError(e.message)); return () => { active = false } }, [])
  const accept = async request => {
    if (busy !== null) return; setBusy(request.id); setActionError(''); setMessage('')
    try { await api.patch(`/api/web/solicitudes/${request.id}/aceptar`, { tipoComprobanteId: Number(type) }); setMessage(`${request.referencia} confirmado y enviado a Cocina / Bar.`); await onAccepted?.() }
    catch (e) { setActionError(e.message) } finally { await refresh(); setBusy(null) }
  }
  const reject = async (event, request) => {
    event.preventDefault(); if (busy !== null) return; setBusy(request.id); setActionError('')
    try { await api.patch(`/api/web/solicitudes/${request.id}/rechazar`, { motivo: reason.trim() }); setRejection(null); setReason(''); setMessage(`${request.referencia} rechazado. El cliente verá el motivo.`) }
    catch (e) { setActionError(e.message) } finally { await refresh(); setBusy(null) }
  }
  return <section className="panel web-orders-inbox" aria-label="Pedidos de la página web"><header><div><span className="eyebrow">RECEPCIÓN WEB</span><h2><Globe size={20} />Pedidos de la página <b>{data?.length || 0}</b></h2><p className="muted">Confirma disponibilidad y entrega antes de enviar a preparación.</p></div><button className="secondary-button" onClick={refresh}><RefreshCw size={16} />Actualizar</button></header>
    {isAdmin && <WebStoreSettings />}
    {(error || actionError) && <div className="form-error" role="alert">{actionError || error}</div>}{message && <p className="sale-success" role="status">{message}</p>}
    {alerts.incoming > 0 && <div className="operation-alert" role="status"><span>{alerts.incoming} nuevo(s) pedido(s) web por confirmar.</span><button onClick={alerts.dismiss}>Entendido</button></div>}
    {loading ? <p className="muted">Consultando pedidos…</p> : !data?.length ? <p className="web-orders-empty">Cuando un cliente envíe su carrito, su pedido aparecerá aquí.</p> : <>
      <label className="web-inbox-type">Comprobante inicial<select value={type} onChange={e => setType(e.target.value)} disabled={busy !== null}>{types.map(t => <option key={t.id} value={t.id}>{t.nombre} · {t.serie}</option>)}</select><small>Los datos de factura se completan al cobrar.</small></label>
      <div className="web-inbox-grid">{data.map(request => <article className="web-request-card" key={request.id}><header><div><span className="eyebrow">{request.referencia} · {request.tipoEntrega === 'DELIVERY' ? 'Delivery' : 'Recojo'}</span><h3>{request.nombre}</h3></div><strong>S/ {money(request.total)}</strong></header><p className="muted">{request.empresa} · {new Date(request.fechaCreacion).toLocaleString('es-PE')}</p><p>Teléfono: {request.telefono} · DNI: {request.dni}</p>{request.direccion && <p><b>Entrega:</b> {request.direccion}</p>}
        <ul>{request.items.map((item, index) => <li key={index}><strong>{item.cantidad} × {item.nombre}</strong>{item.observaciones && <small>{item.observaciones}</small>}</li>)}</ul>{request.observaciones && <p className="web-request-note">{request.observaciones}</p>}
        {rejection === request.id ? <form onSubmit={e => reject(e, request)}><label>Motivo para el cliente<textarea required maxLength={255} value={reason} onChange={e => setReason(e.target.value)} disabled={busy !== null} /></label><div className="web-request-actions"><button className="primary-button" disabled={busy !== null || !reason.trim()}>Confirmar rechazo</button><button type="button" className="secondary-button" disabled={busy !== null} onClick={() => setRejection(null)}>Cancelar</button></div></form> : <div className="web-request-actions"><button className="primary-button" disabled={busy !== null || !type} onClick={() => accept(request)}><Check size={16} />{busy === request.id ? 'Confirmando…' : 'Confirmar y enviar'}</button><button className="secondary-button" disabled={busy !== null} onClick={() => { setRejection(request.id); setReason('') }}><X size={16} />Rechazar</button></div>}
      </article>)}</div></>}
  </section>
}
