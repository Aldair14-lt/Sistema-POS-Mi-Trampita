import { Minus, Plus, Trash2, ShoppingBag } from 'lucide-react'
import { cartTotal, soles } from './publicApi'

export default function CartPanel({ cart, products, update, remove, disabled, onCheckout }) {
  return <section className="web-cart" aria-label="Tu carrito">
    <div className="web-cart-heading"><ShoppingBag size={21} /><h2>Tu pedido</h2><span>{cart.reduce((sum, i) => sum + i.cantidad, 0)}</span></div>
    {!cart.length ? <div className="web-cart-empty"><ShoppingBag size={42} /><h3>Algo rico te espera</h3><p>Agrega tus favoritos y prepara tu próximo almuerzo.</p></div> : <ul className="web-cart-lines">{cart.map(item => {
      const product = products.find(p => p.id === item.productoId)
      if (!product) return null
      return <li key={item.productoId}><div className="web-cart-line-head"><strong>{product.nombre}</strong><button type="button" disabled={disabled} className="web-icon" onClick={() => remove(item.productoId)} aria-label={`Quitar ${product.nombre}`}><Trash2 size={16} /></button></div>
        <div className="web-cart-quantity"><button type="button" disabled={disabled || item.cantidad <= 1} onClick={() => update(item.productoId, 'cantidad', item.cantidad - 1)} aria-label={`Disminuir ${product.nombre}`}><Minus size={14} /></button><span>{item.cantidad}</span><button type="button" disabled={disabled || item.cantidad >= product.limiteCantidad} onClick={() => update(item.productoId, 'cantidad', item.cantidad + 1)} aria-label={`Aumentar ${product.nombre}`}><Plus size={14} /></button><b>{soles(product.precio)} c/u</b></div>
        <label className="web-item-note">¿Alguna indicación?<input disabled={disabled} maxLength={255} value={item.observaciones || ''} onChange={e => update(item.productoId, 'observaciones', e.target.value)} placeholder="Sin ají, sin hielo…" /></label></li>
    })}</ul>}
    <div className="web-cart-total"><span>Total con IGV</span><strong>{soles(cartTotal(cart, products))}</strong></div>
    <p className="web-cart-help">Disponibilidad confirmada por el local. Pago al recoger o recibir.</p>
    {onCheckout && <button type="button" className="web-button" disabled={!cart.length || disabled} onClick={onCheckout}>Continuar con mi pedido <ShoppingBag size={17} /></button>}
  </section>
}
