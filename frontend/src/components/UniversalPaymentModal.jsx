import { useEffect, useRef, useState } from 'react'
import { X, ReceiptText, Wallet } from 'lucide-react'
import { api } from '../api'
import usePolling from '../hooks/usePolling'
import { money } from './OrderStatus'
import '../styles/cashier.css'

export default function UniversalPaymentModal({ saleId, onClose, onUpdated }) {
  const { data: sale, error: loadError, loading, refresh } = usePolling(`/api/ventas/${saleId}`)
  const [mode, setMode] = useState('partial')
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState('efectivo')
  const [received, setReceived] = useState('')
  const [reference, setReference] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [uncertain, setUncertain] = useState(false)
  const pending = useRef(null), dialog = useRef(null)
  const saldo = Number(sale?.saldoPendiente || 0)
  const complete = !sale?.mesa || sale.detalles.every(item => item.estadoPreparacion === 'SERVIDO')
  const totalMode = mode === 'total' || saldo === 0
  const payable = totalMode ? saldo : Number(amount)
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
  const confirm = async updated => {
    pending.current = null; setUncertain(false); setAmount(''); setReceived(''); setReference('')
    onUpdated(updated)
    if (updated.estado === 'CERRADA') onClose()
    else { setNotice('Abono registrado. El saldo se actualizó.'); await refresh() }
  }
  const submit = async event => {
    event.preventDefault()
    if (busy || !sale) return
    setError(''); setNotice('')
    if (!pending.current) {
      if (saldo > 0 && (!Number.isFinite(payable) || payable <= 0 || payable > saldo)) return setError('El abono debe ser mayor que cero y no superar el saldo.')
      if (saldo > 0 && method === 'efectivo' && (!Number.isFinite(Number(received)) || Number(received) < payable)) return setError('El efectivo recibido debe cubrir el pago.')
      if (totalMode && !complete) return setError('Entrega los platos antes de cerrar la mesa. Puedes registrar un abono mientras tanto.')
      const payment = saldo > 0 ? { monto: payable, metodoPago: method, montoRecibido: method === 'efectivo' ? Number(received) : null, referencia: reference.trim(), claveOperacion: crypto.randomUUID() } : null
      pending.current = { path: `/api/ventas/${saleId}/${totalMode ? 'cobrar' : 'pagos'}`, body: totalMode ? { pago: payment } : payment, key: payment?.claveOperacion, closes: totalMode }
    }
    setBusy(true)
    try { await confirm(await api.create(pending.current.path, pending.current.body)) }
    catch (err) {
      // Reutilizar solicitud y UUID exactos: una respuesta perdida no debe duplicar el cobro.
      if (err.status === 0 || err.status >= 500) {
        const updated = await refresh(), request = pending.current
        const paid = !request.key || updated?.pagos.some(payment => payment.claveOperacion === request.key)
        if (updated && paid && (!request.closes || (updated.estado === 'CERRADA' && updated.comprobante))) await confirm(updated)
        else { setUncertain(true); setError('No se pudo confirmar la operación. Reintenta el mismo cobro para verificarlo sin duplicarlo.') }
      } else { pending.current = null; setUncertain(false); setError(err.message); await refresh() }
    } finally { setBusy(false) }
  }
  return <div className="modal-backdrop" onKeyDown={keydown}>
    <section className="modal payment-modal" role="dialog" aria-modal="true" aria-labelledby="payment-title" tabIndex={-1} ref={dialog}>
      <div className="modal-header"><div><span className="eyebrow">CAJA · CUENTA #{saleId}</span><h2 id="payment-title">{sale?.mesa ? `Cobrar mesa ${sale.mesa.numero}` : 'Cobrar pedido online'}</h2></div><button className="icon-button" disabled={busy || uncertain} onClick={onClose} aria-label="Cerrar pagos"><X size={20} /></button></div>
      {(error || loadError) && <div className="form-error" role="alert">{error || loadError}</div>}{notice && <p className="sale-success" role="status">{notice}</p>}
      {loading ? <p className="loading">Cargando cuenta…</p> : sale && <>
        <div className="payment-summary"><div>Total<strong>S/ {money(sale.total)}</strong></div><div>Abonado<strong>S/ {money(sale.totalPagado)}</strong></div><div>Saldo<strong>S/ {money(saldo)}</strong></div></div>
        {sale.estado === 'ABIERTA' && <form onSubmit={submit}>
          <fieldset disabled={busy || uncertain}>
            {saldo > 0 && <><div className="payment-modes" role="group" aria-label="Modalidad de cobro"><button type="button" aria-pressed={mode === 'partial'} onClick={() => setMode('partial')}><Wallet size={17} />Abono parcial</button><button type="button" aria-pressed={mode === 'total'} onClick={() => setMode('total')}><ReceiptText size={17} />Pago total</button></div>
              <div className="inline-fields"><label>{totalMode ? 'Saldo a pagar (S/)' : 'Abono (S/)'}<input type="number" required min="0.01" max={saldo} step="0.01" readOnly={totalMode} value={totalMode ? money(saldo) : amount} onChange={event => setAmount(event.target.value)} /></label><label>Método<select value={method} onChange={event => setMethod(event.target.value)}><option value="efectivo">Efectivo</option><option value="yape">Yape</option><option value="plin">Plin</option><option value="tarjeta">Tarjeta</option><option value="transferencia">Transferencia</option></select></label></div>
              {method === 'efectivo' && <div className="inline-fields"><label>Efectivo recibido (S/)<input type="number" required min={payable || '0.01'} step="0.01" value={received} onChange={event => setReceived(event.target.value)} /></label><p className="payment-change">Vuelto<strong>S/ {money(Math.max(0, Number(received) - (payable || 0)))}</strong></p></div>}
              <label>Referencia de operación (opcional)<input maxLength={100} value={reference} onChange={event => setReference(event.target.value)} /></label></>}
          </fieldset>
          {totalMode && !complete && <p className="muted">La mesa tiene platos pendientes de entrega. Marca “Entregado” antes de emitir el comprobante.</p>}
          <button className="primary-button full" disabled={busy || (!uncertain && totalMode && !complete)}>{busy ? 'Procesando…' : uncertain ? 'Comprobar / reintentar operación' : totalMode ? `Emitir ${sale.tipoComprobante?.nombre || 'boleta'} y ${sale.mesa ? 'cerrar mesa' : 'finalizar cobro'}` : 'Registrar abono'}</button>
        </form>}
        {sale.comprobante && <p className="sale-success" role="status">{sale.comprobante.tipo} {sale.comprobante.numero} emitida.</p>}
        <h3>Historial de pagos</h3><ul className="payment-history">{sale.pagos.map(payment => <li key={payment.id}><div><strong>S/ {money(payment.monto)} · {payment.metodoPago.replace('_', ' / ')}</strong><small>{new Date(payment.fechaPago).toLocaleString('es-PE')} · {payment.registradoPor}</small>{payment.referencia && <small>{payment.referencia}</small>}</div>{Number(payment.vuelto) > 0 && <span>Vuelto S/ {money(payment.vuelto)}</span>}</li>)}</ul>
        {!sale.pagos.length && <p className="muted">Todavía no hay abonos.</p>}
      </>}
    </section>
  </div>
}
