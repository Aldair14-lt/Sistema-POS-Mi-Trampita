import { useEffect, useRef, useState } from 'react'
import { X } from 'lucide-react'
import { api } from '../api'
import usePolling from '../hooks/usePolling'
import { money } from './OrderStatus'

/** Se usa en mesas y recepción. Los abonos y el cierre son operaciones independientes. */
export default function UniversalPaymentModal({ saleId, onClose, onUpdated }) {
  const { data: sale, error: loadError, loading, refresh } = usePolling(`/api/ventas/${saleId}`)
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState('efectivo')
  const [received, setReceived] = useState('')
  const [reference, setReference] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [uncertain, setUncertain] = useState(false)
  const pending = useRef(null)
  const dialog = useRef(null)
  const canClose = sale?.estado === 'ABIERTA' && Number(sale.saldoPendiente) === 0 && sale.detalles.every(d => d.estadoPreparacion === 'SERVIDO')

  useEffect(() => {
    const previous = document.activeElement
    dialog.current?.focus()
    return () => previous?.focus()
  }, [])
  const keydown = event => {
    if (event.key === 'Escape' && !busy && !uncertain) onClose()
    if (event.key !== 'Tab') return
    const controls = [...dialog.current.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled)')]
    const first = controls[0], last = controls[controls.length - 1]
    if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) { event.preventDefault(); last?.focus() }
    else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialog.current)) { event.preventDefault(); first?.focus() }
  }
  const confirmResult = async updated => {
    pending.current = null; setUncertain(false); setAmount(''); setReceived(''); setReference('')
    setNotice('Abono registrado.'); onUpdated(updated); await refresh()
  }
  const pay = async event => {
    event.preventDefault()
    if (busy || !sale) return
    setError(''); setNotice('')
    if (!pending.current) {
      const monto = Number(amount)
      if (!Number.isFinite(monto) || monto <= 0 || monto > Number(sale.saldoPendiente)) return setError('Ingresa un abono mayor que cero y menor o igual al saldo.')
      if (method === 'efectivo' && Number(received) < monto) return setError('El efectivo recibido debe cubrir el abono.')
      pending.current = { monto, metodoPago: method, montoRecibido: method === 'efectivo' ? Number(received) : null,
        referencia: reference.trim(), claveOperacion: crypto.randomUUID() }
    }
    setBusy(true)
    try {
      const updated = await api.create(`/api/ventas/${saleId}/pagos`, pending.current)
      await confirmResult(updated)
    } catch (err) {
      // Conservar la solicitud exacta en fallos de red para reintentar con la misma clave.
      if (err.status === 0 || err.status >= 500) {
        const updated = await refresh()
        if (updated?.pagos.some(p => p.claveOperacion === pending.current.claveOperacion)) await confirmResult(updated)
        else { setUncertain(true); setError('No se pudo confirmar el abono. Reintenta la misma operación para comprobarlo sin duplicar el cobro.') }
      } else { pending.current = null; setUncertain(false); setError(err.message); await refresh() }
    } finally { setBusy(false) }
  }
  const closeAccount = async () => {
    if (busy || !canClose || uncertain) return
    setBusy(true); setError('')
    try { const updated = await api.patch(`/api/ventas/${saleId}/cerrar`, {}); onUpdated(updated); onClose() }
    catch (err) { setError(err.message); await refresh() }
    finally { setBusy(false) }
  }
  return <div className="modal-backdrop" onKeyDown={keydown}>
    <section className="modal payment-modal" role="dialog" aria-modal="true" aria-labelledby="payment-title" tabIndex={-1} ref={dialog}>
      <div className="modal-header"><h2 id="payment-title">Pagos · Cuenta #{saleId}</h2><button className="icon-button" disabled={busy || uncertain} onClick={onClose} aria-label="Cerrar pagos"><X size={20} /></button></div>
      {(error || loadError) && <div className="form-error" role="alert">{error || loadError}</div>}
      {notice && <p className="sale-success" role="status">{notice}</p>}
      {loading ? <p className="loading">Cargando cuenta…</p> : sale && <>
        <div className="payment-summary"><div>Total<strong>S/ {money(sale.total)}</strong></div><div>Abonado<strong>S/ {money(sale.totalPagado)}</strong></div><div>Saldo<strong>S/ {money(sale.saldoPendiente)}</strong></div></div>
        {sale.estado === 'ABIERTA' && (Number(sale.saldoPendiente) > 0 || uncertain) && <form onSubmit={pay}>
          <fieldset disabled={busy || uncertain}><legend>Registrar un abono</legend><div className="inline-fields">
            <label>Abono (S/)<input type="number" required min="0.01" max={sale.saldoPendiente} step="0.01" value={amount} onChange={e => setAmount(e.target.value)} /></label>
            <label>Método<select value={method} onChange={e => setMethod(e.target.value)}><option value="efectivo">Efectivo</option><option value="tarjeta">Tarjeta</option><option value="transferencia">Transferencia</option><option value="yape_plin">Yape / Plin</option></select></label></div>
          {method === 'efectivo' && <div className="inline-fields"><label>Efectivo recibido (S/)<input type="number" required min={amount || '0.01'} step="0.01" value={received} onChange={e => setReceived(e.target.value)} /></label><p>Vuelto: S/ {money(Math.max(0, Number(received) - Number(amount)))}</p></div>}
          <label>Referencia (opcional)<input maxLength={100} value={reference} onChange={e => setReference(e.target.value)} /></label></fieldset>
          <button className="primary-button full" disabled={busy}>{busy ? 'Registrando…' : uncertain ? 'Comprobar / reintentar abono' : 'Registrar abono'}</button>
        </form>}
        <h3>Historial de pagos</h3><ul className="payment-history">{sale.pagos.map(p => <li key={p.id}>
          <div><strong>S/ {money(p.monto)} · {p.metodoPago}</strong><small>{new Date(p.fechaPago).toLocaleString('es-PE')} · {p.registradoPor}</small>{p.referencia && <small>{p.referencia}</small>}</div>
          {Number(p.vuelto) > 0 && <span>Vuelto S/ {money(p.vuelto)}</span>}</li>)}</ul>
        {!sale.pagos.length && <p className="muted">Todavía no hay abonos.</p>}
        {sale.estado === 'ABIERTA' && Number(sale.saldoPendiente) === 0 && <>
          {!canClose && <p className="muted">Cuenta pagada. Entrega todos los platos para finalizar.</p>}
          <button className="secondary-button full" disabled={busy || !canClose || uncertain} onClick={closeAccount}>{sale.mesa ? 'Cerrar cuenta y liberar mesa' : 'Finalizar pedido online'}</button>
        </>}
      </>}
    </section>
  </div>
}
