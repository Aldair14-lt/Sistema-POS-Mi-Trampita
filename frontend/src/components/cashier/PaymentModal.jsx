import { useEffect, useRef, useState } from 'react'
import { X, ReceiptText, Wallet, Banknote, Smartphone, CreditCard, Check, ArrowRight, ArrowLeft } from 'lucide-react'
import { api } from '../../api'
import usePolling from '../../hooks/usePolling'
import { money } from '../OrderStatus'
import '../../styles/cashier.css'
import { usePrint } from '../PrintManager'
import { restorePayment, persistPayment } from '../../utils/pendingOperation'

const methods = [['efectivo', 'Efectivo', Banknote], ['yape', 'Yape', Smartphone], ['plin', 'Plin', Smartphone], ['tarjeta', 'Tarjeta', CreditCard]]
const receipts = [['NOTA_VENTA', 'Ticket simple'], ['BOLETA', 'Boleta'], ['FACTURA', 'Factura']]
const cents = value => Math.round(Number(value || 0) * 100)

export default function PaymentModal({ saleId, onClose, onUpdated }) {
  const { data: sale, error: loadError, loading, refresh } = usePolling(`/api/ventas/${saleId}`)
  const [mode, setMode] = useState('total')
  const [step, setStep] = useState(1)
  const [receipt, setReceipt] = useState('NOTA_VENTA')
  const [format, setFormat] = useState('80mm')
  const [printAfter, setPrintAfter] = useState(true)
  const print = usePrint()
  const [fiscal, setFiscal] = useState({ ruc: '', razonSocial: '', direccionFiscal: '', dni: '', nombreCliente: '' })
  const initialized = useRef(false), submitting = useRef(false)
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState('efectivo')
  const [received, setReceived] = useState('')
  const [reference, setReference] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [uncertain, setUncertain] = useState(() => Boolean(restorePayment(saleId)))
  const pending = useRef(restorePayment(saleId)), dialog = useRef(null)
  const saldo = Number(sale?.saldoPendiente || 0)
  const complete = !sale?.mesa || sale.detalles.every(item => ['SERVIDO', 'CANCELADO'].includes(item.estadoPreparacion))
  const totalMode = mode === 'total' || saldo === 0
  const payable = totalMode ? saldo : Number(amount)
  const identified = cents(sale?.total) > 70000 || Boolean(fiscal.dni)
  const field = (key, value) => setFiscal(current => ({ ...current, [key]: value }))
  useEffect(() => {
    if (!sale || initialized.current) return
    initialized.current = true
    const customer = sale.cliente || {}, name = sale.tipoComprobante?.nombre?.toUpperCase() || ''
    setReceipt(name.includes('FACTURA') ? 'FACTURA' : name.includes('BOLETA') ? 'BOLETA' : 'NOTA_VENTA')
    setFormat(name.includes('FACTURA') ? 'A4' : '80mm')
    setFiscal({ ruc: /^\d{11}$/.test(customer.numeroDocumento) ? customer.numeroDocumento : '',
      razonSocial: customer.nombresRazonSocial || '', direccionFiscal: customer.direccion || '',
      dni: /^\d{8}$/.test(customer.numeroDocumento) ? customer.numeroDocumento : '', nombreCliente: customer.nombresRazonSocial || '' })
  }, [sale])
  useEffect(() => {
    const previous = document.activeElement
    dialog.current?.focus()
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = overflow; previous?.focus() }
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
    pending.current = null; persistPayment(saleId, null); setUncertain(false); setAmount(''); setReceived(''); setReference('')
    onUpdated(updated)
    if (updated.estado === 'CERRADA') { if (printAfter && updated.comprobante) await print({ type: 'RECEIPT', format, sale: updated }); onClose() }
    else { setNotice('Abono registrado. El saldo se actualizó.'); await refresh() }
  }
  const submit = async event => {
    event.preventDefault()
    if (submitting.current || !sale) return
    setError(''); setNotice('')
    if (!pending.current) {
      if (saldo > 0 && (!Number.isFinite(payable) || payable <= 0 || payable > saldo)) return setError('El abono debe ser mayor que cero y no superar el saldo.')
      if (saldo > 0 && method === 'efectivo' && (!Number.isFinite(Number(received)) || Number(received) < payable)) return setError('El efectivo recibido debe cubrir el pago.')
      if (totalMode && !complete) return setError('Entrega los platos antes de cerrar la mesa. Puedes registrar un abono mientras tanto.')
      if (totalMode && step === 1) { setStep(2); return }
      if (totalMode && receipt === 'FACTURA' && (!/^\d{11}$/.test(fiscal.ruc) || !fiscal.razonSocial.trim() || !fiscal.direccionFiscal.trim())) return setError('Completa RUC de 11 dígitos, razón social y dirección fiscal.')
      if (totalMode && receipt === 'BOLETA' && identified && (!/^\d{8}$/.test(fiscal.dni) || !fiscal.nombreCliente.trim())) return setError('Completa DNI de 8 dígitos y nombre del cliente.')
      const payment = saldo > 0 ? { monto: payable, metodoPago: method, montoRecibido: method === 'efectivo' ? Number(received) : null, referencia: reference.trim(), claveOperacion: crypto.randomUUID() } : null
      // RUC se renderiza y se envía únicamente para FACTURA; cambiar a BOLETA no filtra datos fiscales.
      const facturacion = { tipoComprobante: receipt,
        factura: receipt === 'FACTURA' ? { ruc: fiscal.ruc, razonSocial: fiscal.razonSocial.trim(), direccionFiscal: fiscal.direccionFiscal.trim() } : null,
        dni: receipt === 'BOLETA' && fiscal.dni ? fiscal.dni : null,
        nombreCliente: receipt === 'BOLETA' ? fiscal.nombreCliente.trim() : null }
      pending.current = { path: `/api/ventas/${saleId}/${totalMode ? 'cobrar' : 'pagos'}`, body: totalMode ? { pago: payment, facturacion } : payment, key: payment?.claveOperacion, closes: totalMode }
      persistPayment(saleId, pending.current)
    }
    submitting.current = true; setBusy(true)
    try { await confirm(await api.create(pending.current.path, pending.current.body)) }
    catch (err) {
      // Reutilizar solicitud y UUID exactos: una respuesta perdida no debe duplicar el cobro.
      if (err.status === 0 || err.status >= 500) {
        const updated = await refresh(), request = pending.current
        const paid = !request.key || updated?.pagos.some(payment => payment.claveOperacion === request.key)
        if (updated && paid && (!request.closes || (updated.estado === 'CERRADA' && updated.comprobante))) await confirm(updated)
        else { setUncertain(true); setError('No se pudo confirmar la operación. Reintenta el mismo cobro para verificarlo sin duplicarlo.') }
      } else { pending.current = null; persistPayment(saleId, null); setUncertain(false); setError(err.message); await refresh() }
    } finally { submitting.current = false; setBusy(false) }
  }
  return <div className="modal-backdrop payment-backdrop" onKeyDown={keydown}>
    <section className="modal payment-modal advanced-payment" role="dialog" aria-modal="true" aria-labelledby="payment-title" tabIndex={-1} ref={dialog}>
      <div className="modal-header"><div><span className="eyebrow">CAJA · CUENTA #{saleId}</span><h2 id="payment-title">{sale?.mesa ? `Cobrar mesa ${sale.mesa.numero}` : 'Cobrar pedido online'}</h2></div><button className="icon-button" disabled={busy || uncertain} onClick={onClose} aria-label="Cerrar pagos"><X size={20} /></button></div>
      {(error || loadError) && <div className="form-error" role="alert">{error || loadError}</div>}{notice && <p className="sale-success" role="status">{notice}</p>}
      {uncertain && <p role="status" className="payment-hint">Hay un cobro pendiente de comprobar. Se conservará la misma operación al reintentar.</p>}
      {loading ? <p className="loading">Cargando cuenta…</p> : sale && <>
        <div className="payment-summary"><div>Total<strong>S/ {money(sale.total)}</strong></div><div>Abonado<strong>S/ {money(sale.totalPagado)}</strong></div><div>Saldo<strong>S/ {money(saldo)}</strong></div></div>
        {(sale.estado === 'ABIERTA' || uncertain) && <form onSubmit={submit}>
          <ol className="payment-steps" aria-label="Pasos del cobro"><li aria-current={step === 1 ? 'step' : undefined}><b>{step === 2 ? <Check size={14} /> : '1'}</b>Importe y pago</li><li aria-current={step === 2 ? 'step' : undefined}><b>2</b>Comprobante</li></ol>
          <fieldset disabled={busy || uncertain}>
            {step === 1 && saldo > 0 && <><div className="payment-modes" role="group" aria-label="Modalidad de cobro"><button type="button" aria-pressed={mode === 'partial'} onClick={() => setMode('partial')}><Wallet size={17} />Abono / pago anticipado</button><button type="button" aria-pressed={mode === 'total'} onClick={() => setMode('total')}><ReceiptText size={17} />Pago total</button></div>
              <label>{totalMode ? 'Saldo a pagar (S/)' : 'Abono (S/)'}<input type="number" required min="0.01" max={saldo} step="0.01" readOnly={totalMode} value={totalMode ? money(saldo) : amount} onChange={event => setAmount(event.target.value)} /></label>
              <div className="payment-methods" role="group" aria-label="Método de pago">{methods.map(([code, label, Icon]) => <button type="button" key={code} className={`method-${code}`} aria-pressed={method === code} onClick={() => setMethod(code)}><Icon size={23} /><span>{label}</span>{method === code && <Check size={13} className="method-check" />}</button>)}</div>
              {/* Vuelto en céntimos para evitar residuos de coma flotante. */}
              {method === 'efectivo' && <div className="cash-received"><label>Monto recibido (S/)<input type="number" required min={payable || '0.01'} max="99999999.99" step="0.01" value={received} onChange={event => setReceived(event.target.value)} placeholder="0.00" /></label><p className="payment-change" role="status">Vuelto a entregar<strong>S/ {money(Math.max(0, cents(received) - cents(payable)) / 100)}</strong></p></div>}
              <label>Referencia de operación (opcional)<input maxLength={100} value={reference} onChange={event => setReference(event.target.value)} /></label></>}
            {step === 2 && <>
              <div className="receipt-selector" role="group" aria-label="Tipo de comprobante">{receipts.map(([code, label]) => <button type="button" key={code} aria-pressed={receipt === code} onClick={() => { setReceipt(code); setFormat(code === 'FACTURA' ? 'A4' : '80mm') }}><ReceiptText size={19} />{label}</button>)}</div>
              <label>Formato de impresión<select value={format} onChange={e => setFormat(e.target.value)}><option value="80mm">Térmico · 80 mm</option><option value="A4">Hoja A4</option></select></label>
              <label className="print-preference"><input type="checkbox" checked={printAfter} onChange={e => setPrintAfter(e.target.checked)} />Imprimir al emitir el comprobante</label>
              {receipt === 'FACTURA' && <div className="fiscal-fields"><label>RUC<input required inputMode="numeric" pattern="[0-9]{11}" maxLength={11} value={fiscal.ruc} onChange={e => field('ruc', e.target.value.replace(/\D/g, ''))} placeholder="11 dígitos" /></label><label>Razón social<input required maxLength={150} value={fiscal.razonSocial} onChange={e => field('razonSocial', e.target.value)} /></label><label>Dirección fiscal<input required maxLength={255} value={fiscal.direccionFiscal} onChange={e => field('direccionFiscal', e.target.value)} /></label></div>}
              {receipt === 'BOLETA' && <div className="fiscal-fields"><label>DNI {cents(sale.total) > 70000 ? '(obligatorio)' : '(opcional)'}<input required={identified} inputMode="numeric" pattern="[0-9]{8}" maxLength={8} value={fiscal.dni} onChange={e => field('dni', e.target.value.replace(/\D/g, ''))} placeholder="8 dígitos" /></label><label>Nombre del cliente<input required={identified} maxLength={150} value={fiscal.nombreCliente} onChange={e => field('nombreCliente', e.target.value)} /></label><p className="payment-hint">Para boletas mayores a S/ 700, completa DNI y nombre.</p></div>}
              {receipt === 'NOTA_VENTA' && <p className="payment-hint">Ticket de consumo para el cliente, sin datos fiscales obligatorios.</p>}
              <div className="payment-confirmation"><span>{saldo > 0 ? `${method.toUpperCase()} · A cobrar` : 'Cuenta abonada íntegramente'}<strong>S/ {money(payable)}</strong></span>{saldo > 0 && method === 'efectivo' && <span>Vuelto<strong>S/ {money(Math.max(0, cents(received) - cents(payable)) / 100)}</strong></span>}<small>{sale.mesa ? 'La mesa quedará libre al confirmar.' : 'El pedido seguirá en cocina hasta su entrega.'}</small></div>
            </>}
          </fieldset>
          {totalMode && !complete && <p className="muted">La mesa tiene platos pendientes de entrega. Marca “Entregado” antes de emitir el comprobante.</p>}
          <div className="payment-actions">{step === 2 && <button type="button" className="secondary-button" disabled={busy || uncertain} onClick={() => setStep(1)}><ArrowLeft size={16} />Volver</button>}<button className="primary-button" disabled={busy || (!uncertain && totalMode && !complete)}>{busy ? 'Procesando…' : uncertain ? 'Comprobar / reintentar operación' : !totalMode ? 'Registrar abono' : step === 1 ? <>Continuar a comprobante<ArrowRight size={17} /></> : sale.mesa ? 'Emitir y cerrar mesa' : 'Emitir y finalizar cobro'}</button></div>
        </form>}
        {sale.comprobante && <p className="sale-success" role="status">{sale.comprobante.tipoComprobante} {sale.comprobante.numero} emitida.</p>}
        <h3>Historial de pagos</h3><ul className="payment-history">{sale.pagos.map(payment => <li key={payment.id}><div><strong>S/ {money(payment.monto)} · {payment.metodoPago.replace('_', ' / ')}</strong><small>{new Date(payment.fechaPago).toLocaleString('es-PE')} · {payment.registradoPor}</small>{payment.referencia && <small>{payment.referencia}</small>}</div>{Number(payment.vuelto) > 0 && <span>Vuelto S/ {money(payment.vuelto)}</span>}</li>)}</ul>
        {!sale.pagos.length && <p className="muted">Todavía no hay abonos.</p>}
      </>}
    </section>
  </div>
}
