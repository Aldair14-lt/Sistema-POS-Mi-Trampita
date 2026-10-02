import { useEffect, useRef, useState } from 'react'
import { api } from '../api'
import { money } from './OrderStatus'

export default function WhatsAppOrderForm({ onCreated, onCancel }) {
  const [catalog, setCatalog] = useState(null)
  const [customer, setCustomer] = useState({ numeroDocumento: '', nombresRazonSocial: '', telefono: '', direccion: '' })
  const [clientId, setClientId] = useState('')
  const [origin, setOrigin] = useState('WHATSAPP')
  const [delivery, setDelivery] = useState('RECOJO')
  const [address, setAddress] = useState('')
  const [receipt, setReceipt] = useState('')
  const [items, setItems] = useState([{ productoId: '', cantidad: 1 }])
  const [mode, setMode] = useState('CONTRA_ENTREGA')
  const [advance, setAdvance] = useState('')
  const [method, setMethod] = useState('yape')
  const [reference, setReference] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [uncertain, setUncertain] = useState(false)
  const pending = useRef(null)
  useEffect(() => {
    let active = true
    Promise.all([api.list('/api/productos'), api.list('/api/pos/configuracion'), api.list('/api/tipos-comprobante'), api.list('/api/pos/clientes')])
      .then(([products, companies, receipts, clients]) => {
        if (!active) return
        setCatalog({ products, companies, receipts, clients })
        setReceipt(String(receipts.find(r => r.nombre.toUpperCase().includes('BOLETA'))?.id || receipts[0]?.id || ''))
      }).catch(err => active && setError(err.message))
    return () => { active = false }
  }, [])
  const subtotal = items.reduce((sum, item) => sum + Number(catalog?.products.find(p => String(p.id) === item.productoId)?.precioVenta || 0) * Number(item.cantidad), 0)
  const total = Math.round((subtotal + Math.round(subtotal * 18) / 100) * 100) / 100
  const updateItem = (index, field, value) => setItems(current => current.map((item, i) => i === index ? { ...item, [field]: value } : item))
  const submit = async event => {
    event.preventDefault()
    if (busy || !catalog) return
    setError('')
    if (!pending.current) {
      if (!catalog.companies.length) return setError('Completa la configuración fiscal antes de recibir pedidos.')
      const selected = catalog.receipts.find(r => String(r.id) === receipt)
      if (!selected) return setError('Selecciona un comprobante.')
      if (selected.nombre.toUpperCase().includes('FACTURA') && (!/^\d{11}$/.test(customer.numeroDocumento) || !customer.direccion.trim()))
        return setError('La factura requiere RUC de 11 dígitos y dirección fiscal.')
      const initial = mode === 'TOTAL' ? total : Number(advance)
      if (mode !== 'CONTRA_ENTREGA' && (!Number.isFinite(initial) || initial <= 0 || initial > total))
        return setError('El pago inicial debe ser mayor que cero y no superar el total.')
      pending.current = {
        empresaId: catalog.companies[0].id, clienteId: clientId ? Number(clientId) : null, cliente: clientId ? null : { ...customer, nombresRazonSocial: customer.nombresRazonSocial.trim() },
        tipoComprobanteId: Number(receipt), numeroComprobante: `${selected.serie}-ON-${crypto.randomUUID().slice(0, 18)}`,
        origenPedido: origin, tipoEntrega: delivery, direccion: delivery === 'DELIVERY' ? address.trim() : null, telefono: customer.telefono.trim(),
        items: items.map(item => ({ productoId: Number(item.productoId), cantidad: Number(item.cantidad) })),
        pagoTotal: mode === 'TOTAL',
        pagoInicial: mode === 'CONTRA_ENTREGA' ? null : { monto: initial, metodoPago: method,
          montoRecibido: method === 'efectivo' ? initial : null, referencia: reference.trim(), claveOperacion: crypto.randomUUID() }
      }
    }
    setBusy(true)
    try { const sale = await api.create(pending.current.origenPedido === 'WHATSAPP' ? '/api/ventas/whatsapp' : '/api/ventas', pending.current); onCreated(sale) }
    catch (err) {
      if (err.status === 0 || err.status >= 500 || err.status === 409) {
        // Confirmar por la referencia única de la comanda antes de permitir un segundo envío.
        const orders = await api.list('/api/pedidos-online').catch(() => [])
        const saved = orders.find(sale => sale.numeroComprobante === pending.current.numeroComprobante)
        if (saved) { onCreated(saved); return }
        if (err.status === 409) { pending.current = null; setUncertain(false); setError(err.message) }
        else { setUncertain(true); setError('No se pudo confirmar el pedido. Reintenta para comprobar si fue recibido.') }
      } else { pending.current = null; setUncertain(false); setError(err.message) }
    } finally { setBusy(false) }
  }
  return <section className="panel online-order-form"><div className="panel-head"><h3>Ingreso rápido · WhatsApp / Web</h3>
    <button className="secondary-button" disabled={busy || uncertain} onClick={onCancel}>Cancelar</button></div>
    {error && <div className="form-error" role="alert">{error}</div>}
    {!catalog ? <p className="loading">Cargando catálogo…</p> : <form onSubmit={submit}>
      <fieldset disabled={busy || uncertain}><legend>Cliente y entrega</legend>
      <div className="inline-fields"><label>Canal<select value={origin} onChange={event => setOrigin(event.target.value)}><option value="WHATSAPP">WhatsApp / Redes</option><option value="WEB">Web</option></select></label>
        <label>Cliente<select value={clientId} onChange={event => {
          const id = event.target.value; setClientId(id)
          const selected = catalog.clients.find(client => String(client.id) === id)
          setCustomer(selected ? { numeroDocumento: selected.numeroDocumento, nombresRazonSocial: selected.nombresRazonSocial, telefono: selected.telefono || '', direccion: selected.direccion || '' } : { numeroDocumento: '', nombresRazonSocial: '', telefono: '', direccion: '' })
        }}><option value="">Nuevo cliente</option>{catalog.clients.map(client => <option key={client.id} value={client.id}>{client.nombresRazonSocial} · {client.numeroDocumento}</option>)}</select></label></div><div className="inline-fields">
        <label>DNI / RUC<input readOnly={Boolean(clientId)} required pattern="[0-9]{6,20}" maxLength={20} value={customer.numeroDocumento} onChange={e => setCustomer({ ...customer, numeroDocumento: e.target.value })} /></label>
        <label>Nombre / razón social<input readOnly={Boolean(clientId)} required maxLength={150} value={customer.nombresRazonSocial} onChange={e => setCustomer({ ...customer, nombresRazonSocial: e.target.value })} /></label></div>
      <div className="inline-fields"><label>Teléfono de contacto<input required type="tel" minLength={6} maxLength={20} value={customer.telefono} onChange={e => setCustomer({ ...customer, telefono: e.target.value })} /></label>
        <label>Dirección fiscal<input readOnly={Boolean(clientId)} maxLength={255} value={customer.direccion} onChange={e => setCustomer({ ...customer, direccion: e.target.value })} /></label></div>
      <div className="inline-fields"><label>Entrega<select value={delivery} onChange={e => setDelivery(e.target.value)}><option value="RECOJO">Recojo en local</option><option value="DELIVERY">Delivery</option></select></label>
        <label>Comprobante<select required value={receipt} onChange={e => setReceipt(e.target.value)}>{catalog.receipts.map(r => <option key={r.id} value={r.id}>{r.nombre} · {r.serie}</option>)}</select></label></div>
      {delivery === 'DELIVERY' && <label>Dirección de envío<input required maxLength={255} value={address} onChange={e => setAddress(e.target.value)} /></label>}
      <legend>Productos</legend>{items.map((item, index) => <div className="online-item-input" key={index}>
        <label>Producto<select required value={item.productoId} onChange={e => updateItem(index, 'productoId', e.target.value)}><option value="">Selecciona</option>
          {catalog.products.map(p => <option key={p.id} value={p.id} disabled={p.stockActual <= 0}>{p.nombre} · S/ {money(p.precioVenta)} · stock {p.stockActual}</option>)}</select></label>
        <label>Cantidad<input type="number" min="1" max="100000" required step="1" value={item.cantidad} onChange={e => updateItem(index, 'cantidad', e.target.value)} /></label>
        <button type="button" className="secondary-button" disabled={items.length === 1} onClick={() => setItems(items.filter((_, i) => i !== index))}>Quitar</button></div>)}
      <button type="button" className="secondary-button" disabled={items.length >= 200} onClick={() => setItems([...items, { productoId: '', cantidad: 1 }])}>Agregar producto</button>
      <legend>Pago inicial confirmado</legend><div className="inline-fields"><label>Modalidad<select value={mode} onChange={e => setMode(e.target.value)}><option value="CONTRA_ENTREGA">Presencial / contra entrega</option><option value="ADELANTO">Adelanto</option><option value="TOTAL">Pago 100%</option></select></label>
        {mode === 'ADELANTO' && <label>Adelanto (S/)<input required type="number" step="0.01" min="0.01" max={total} value={advance} onChange={e => setAdvance(e.target.value)} /></label>}</div>
      {mode !== 'CONTRA_ENTREGA' && <div className="inline-fields"><label>Método<select value={method} onChange={e => setMethod(e.target.value)}><option value="efectivo">Efectivo recibido exacto</option><option value="tarjeta">Tarjeta</option><option value="transferencia">Transferencia</option><option value="yape">Yape</option><option value="plin">Plin</option></select></label>
        <label>Referencia<input maxLength={100} value={reference} onChange={e => setReference(e.target.value)} /></label></div>}
      <p className="muted">Registra únicamente pagos que recepción haya confirmado.</p>
      </fieldset><div className="totals"><div><span>Subtotal</span><b>S/ {money(subtotal)}</b></div><div><span>IGV (18%)</span><b>S/ {money(total - subtotal)}</b></div><div className="total-line"><strong>Total</strong><strong>S/ {money(total)}</strong></div></div>
      <button className="primary-button" disabled={busy}>{busy ? 'Enviando…' : uncertain ? 'Comprobar / reintentar pedido' : 'Enviar pedido a cocina'}</button>
    </form>}
  </section>
}
