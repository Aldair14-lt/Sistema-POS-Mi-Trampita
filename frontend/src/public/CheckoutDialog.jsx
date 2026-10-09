import { useEffect, useRef, useState } from 'react'
import { X, MapPin, Truck } from 'lucide-react'
import CartPanel from './CartPanel'
import { cartTotal, soles } from './publicApi'

export default function CheckoutDialog({ menu, cart, update, remove, pending, busy, uncertain, error, submit, refresh, close }) {
  const dialog = useRef(null)
  const [customer, setCustomer] = useState(() => pending || { nombre: '', dni: '', telefono: '', direccion: '', observaciones: '', tipoEntrega: menu.recojo ? 'RECOJO' : 'DELIVERY' })
  useEffect(() => { const previous = document.activeElement; dialog.current.showModal(); return () => previous?.focus?.() }, [])
  const change = (field, value) => setCustomer(current => ({ ...current, [field]: value }))
  return <dialog className="web-checkout" ref={dialog} aria-labelledby="web-checkout-title" onCancel={e => { e.preventDefault(); if (!busy && !uncertain) close() }}>
    <header><div><span className="web-eyebrow">YA CASI ESTÁ</span><h2 id="web-checkout-title">¿Dónde lo disfrutarás?</h2></div><button type="button" className="web-icon" disabled={busy || uncertain} onClick={close} aria-label="Cerrar carrito"><X /></button></header>
    <form onSubmit={e => { e.preventDefault(); submit(customer) }}>
      <CartPanel cart={cart} products={menu.productos} update={update} remove={remove} disabled={busy || uncertain} />
      <fieldset disabled={busy || uncertain}><legend>Entrega</legend><div className="web-delivery-options">
        {menu.recojo && <label><input type="radio" name="tipoEntrega" value="RECOJO" checked={customer.tipoEntrega === 'RECOJO'} onChange={e => change('tipoEntrega', e.target.value)} /><MapPin size={18} />Recojo en local</label>}
        {menu.delivery && <label><input type="radio" name="tipoEntrega" value="DELIVERY" checked={customer.tipoEntrega === 'DELIVERY'} onChange={e => change('tipoEntrega', e.target.value)} /><Truck size={18} />Delivery</label>}
      </div>
        <label>Nombre completo<input required autoComplete="name" maxLength={150} value={customer.nombre} onChange={e => change('nombre', e.target.value)} /></label>
        <div className="web-form-row"><label>Teléfono de contacto<input required type="tel" autoComplete="tel" pattern="[+0-9 ()-]{6,20}" maxLength={20} value={customer.telefono} onChange={e => change('telefono', e.target.value)} /></label>
          <label>DNI para tu boleta<input required inputMode="numeric" pattern="[0-9]{8}" minLength={8} maxLength={8} value={customer.dni} onChange={e => change('dni', e.target.value)} /></label></div>
        {customer.tipoEntrega === 'DELIVERY' ? <label>Dirección y referencia de entrega<textarea required autoComplete="street-address" maxLength={255} value={customer.direccion} onChange={e => change('direccion', e.target.value)} /></label> : <p className="web-address"><MapPin size={16} />{menu.direccion}</p>}
        <label>Comentario para el local<textarea maxLength={255} value={customer.observaciones} onChange={e => change('observaciones', e.target.value)} placeholder="Hora de recojo, referencia u otra indicación…" /></label>
      </fieldset>
      <p className="web-privacy">Usaremos estos datos para atender tu pedido y emitir tu comprobante. El local confirmará disponibilidad y cobertura de delivery.</p>
      {error && <div className="web-error" role="alert">{error}</div>}
      {error && !busy && !uncertain && <button type="button" className="web-button web-button-light" style={{marginBottom:12}} onClick={refresh}>Actualizar carta</button>}
      <button className="web-button" type="submit" disabled={busy || !cart.length}>{busy ? 'Enviando tu pedido…' : uncertain ? 'Comprobar / reintentar envío' : `Enviar pedido · ${soles(cartTotal(cart, menu.productos))}`}</button>
    </form>
  </dialog>
}
