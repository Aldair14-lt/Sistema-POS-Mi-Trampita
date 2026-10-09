import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { UtensilsCrossed, ShoppingBag, Search, Plus, MapPin, Phone, ArrowUpRight, Check, RefreshCw, GlassWater } from 'lucide-react'
import { publicApi, soles, cartTotal, storageRead, storageWrite, operationId } from './publicApi'
import CartPanel from './CartPanel'
import CheckoutDialog from './CheckoutDialog'
import './public-menu.css'

const states = { PENDIENTE: ['Pedido recibido', 'El equipo del local está revisando tu pedido. Te contactará si necesita confirmar algún detalle.'], ACEPTADO: ['Pedido confirmado', 'Tu pedido ya está en Cocina y Bar.'], PREPARANDO: ['Estamos preparando tu pedido', 'Ya falta menos para disfrutarlo.'], LISTO: ['¡Tu pedido está listo!', 'Coordina el recojo o la entrega con el local.'], ENTREGADO: ['Pedido entregado', 'Gracias por elegirnos. ¡Te esperamos pronto!'], RECHAZADO: ['No pudimos confirmar el pedido', 'Puedes comunicarte con el local o realizar un nuevo pedido.'], EXPIRADO: ['Este pedido expiró', 'Han pasado 24 horas sin confirmación. Realiza un nuevo pedido.'], CANCELADO: ['Pedido cancelado', 'Comunícate con el local para conocer el detalle.'] }
const terminal = new Set(['ENTREGADO', 'RECHAZADO', 'EXPIRADO', 'CANCELADO'])

export default function PublicMenu({ slug }) {
  const key = `trampita-cart-${slug}`; const pendingKey = `trampita-pending-${slug}`
  const [menu, setMenu] = useState(null); const [error, setError] = useState(''); const [loading, setLoading] = useState(true)
  const [cart, setCart] = useState(() => { const saved = storageRead('sessionStorage', pendingKey, null)?.items || storageRead('localStorage', key, []); return Array.isArray(saved) ? saved.filter(i => Number.isInteger(i?.productoId) && Number.isInteger(i?.cantidad) && i.cantidad > 0 && i.cantidad <= 20).slice(0, 30) : [] })
  const [query, setQuery] = useState(''); const [category, setCategory] = useState('Todos'); const [checkout, setCheckout] = useState(false)
  const pending = useRef(storageRead('sessionStorage', pendingKey, null)); const submitting = useRef(false)
  const [busy, setBusy] = useState(false); const [uncertain, setUncertain] = useState(Boolean(pending.current)); const [sendError, setSendError] = useState('')
  const [code, setCode] = useState(() => new URLSearchParams(window.location.search).get('pedido'))
  const [status, setStatus] = useState(null); const [statusError, setStatusError] = useState('')
  const loadMenu = useCallback(async () => {
    setLoading(true); setError('')
    try { setMenu(await publicApi.menu(slug)); setSendError('') } catch (e) { setError(e.message) } finally { setLoading(false) }
  }, [slug])
  useEffect(() => { loadMenu() }, [loadMenu])
  useEffect(() => { storageWrite('localStorage', key, cart) }, [cart, key])
  useEffect(() => { document.title = menu ? `${menu.nombre} · Pide a tu gusto` : 'Recreo Mi Trampita · Pedidos' }, [menu])
  useEffect(() => {
    if (!menu) return
    if (pending.current) { setCheckout(true); return }
    setCart(current => current.flatMap(i => { const p = menu.productos.find(p => p.id === i.productoId); return p?.limiteCantidad > 0 ? [{ ...i, cantidad: Math.min(i.cantidad, p.limiteCantidad), observaciones: String(i.observaciones || '').slice(0, 255) }] : [] }))
  }, [menu])
  useEffect(() => {
    if (!code) return
    let active = true; let timer
    const poll = async () => {
      try { const result = await publicApi.status(slug, code); if (active) { setStatus(result); setStatusError(''); if (terminal.has(result.estado)) return } }
      catch (e) { if (active) { setStatusError(e.message); if (e.status === 404 || e.status === 400) return } }
      if (active) timer = setTimeout(poll, 10000)
    }
    poll(); return () => { active = false; clearTimeout(timer) }
  }, [slug, code])
  const categories = useMemo(() => ['Todos', ...new Set(menu?.productos.map(p => p.categoria) || [])], [menu])
  const products = menu?.productos.filter(p => (category === 'Todos' || category === p.categoria) && `${p.nombre} ${p.descripcion || ''}`.toLowerCase().includes(query.toLowerCase().trim())) || []
  const count = cart.reduce((sum, i) => sum + i.cantidad, 0)
  const update = (id, field, value) => { if (!busy && !uncertain) setCart(current => current.map(i => i.productoId === id ? { ...i, [field]: field === 'cantidad' ? Math.max(1, Math.min(value, menu.productos.find(p => p.id === id)?.limiteCantidad || 1, 60 - count + i.cantidad)) : value } : i)) }
  const remove = id => { if (!busy && !uncertain) setCart(current => current.filter(i => i.productoId !== id)) }
  const add = product => {
    if (busy || uncertain) { setCheckout(true); return }
    if (count >= 60) { setSendError('Máximo 60 unidades por pedido.'); setCheckout(true); return }
    setCart(current => { const item = current.find(i => i.productoId === product.id); return item ? current.map(i => i === item ? { ...i, cantidad: Math.min(i.cantidad + 1, product.limiteCantidad) } : i) : [...current, { productoId: product.id, cantidad: 1, observaciones: '' }] })
  }
  const submit = async customer => {
    if (submitting.current || (!pending.current && !cart.length)) return
    submitting.current = true; setBusy(true); setSendError('')
    try {
    if (!pending.current) {
      pending.current = { claveOperacion: operationId(), nombre: customer.nombre.trim(), dni: customer.dni, telefono: customer.telefono.trim(), tipoEntrega: customer.tipoEntrega,
        direccion: customer.tipoEntrega === 'DELIVERY' ? customer.direccion.trim() : '', observaciones: customer.observaciones.trim(), items: cart.map(i => ({ ...i })), totalEsperado: cartTotal(cart, menu.productos) }
      storageWrite('sessionStorage', pendingKey, pending.current)
    }
      const result = await publicApi.send(slug, pending.current)
      pending.current = null; storageWrite('sessionStorage', pendingKey, null); setCart([]); setUncertain(false); setCheckout(false); setStatus(result); setCode(result.codigoSeguimiento)
      window.history.replaceState(null, '', `${window.location.pathname}?pedido=${result.codigoSeguimiento}`); window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch (e) {
      setSendError(e.message)
      if (e.status === 0 || e.status >= 500) { setUncertain(true); setSendError(`${e.message} Tu envío está pendiente de comprobar; reintenta el mismo pedido.`) }
      else { pending.current = null; storageWrite('sessionStorage', pendingKey, null); setUncertain(false) }
    } finally { submitting.current = false; setBusy(false) }
  }
  const newOrder = () => { setCode(null); setStatus(null); setStatusError(''); window.history.replaceState(null, '', window.location.pathname); loadMenu() }
  const phone = menu?.telefono?.replace(/[^+0-9]/g, '')
  return <div className="public-menu">
    <header className="web-header"><a href={`/pedir/${encodeURIComponent(slug)}`} className="web-brand"><span><UtensilsCrossed size={22} /></span><div>{menu?.nombre || 'Recreo Mi Trampita'}<small>EL SABOR DE COMPARTIR</small></div></a><span className="web-header-note">Hecho con cariño, servido a tu gusto.</span><button className="web-header-cart" disabled={!menu || Boolean(code)} onClick={() => setCheckout(true)}><ShoppingBag size={18} />Tu pedido <b>{count}</b></button></header>
    {code ? <main className="web-status"><span className="web-status-icon"><Check size={34} /></span><span className="web-eyebrow">{status?.referencia || 'TU PEDIDO'}</span><h1>{states[status?.estado]?.[0] || 'Consultando tu pedido…'}</h1><p>{states[status?.estado]?.[1]}</p>{status?.motivo && <p className="web-error">{status.motivo}</p>}{statusError && <p role="alert" className="web-error">{statusError}</p>}{status && <div className="web-status-total"><span>Total del pedido</span><strong>{soles(status.total)}</strong><small>Pago coordinado con el local.</small></div>}<p className="web-cart-help">Guarda este enlace para consultar el estado de tu pedido. No lo compartas con terceros.</p><div className="web-status-actions"><button className="web-button" onClick={newOrder}>Volver al menú</button>{phone && <a className="web-button web-button-light" href={`tel:${phone}`}><Phone size={17} />Llamar al local</a>}</div></main> : <>
      <section className="web-hero"><div><span className="web-eyebrow">RECREO MI TRAMPITA · A TU MESA</span><h1>Un buen plato.<br /><em>Un gran momento.</em></h1><p>Elige tus favoritos, reúne a los tuyos y disfruta. Nosotros nos encargamos del sabor.</p><div className="web-hero-tags">{menu?.recojo && <span><MapPin size={16} />Recojo en local</span>}{menu?.delivery && <span><ShoppingBag size={16} />Delivery</span>}<span>Precios con IGV</span></div></div><div className="web-hero-art" aria-hidden="true"><div className="web-plate"><UtensilsCrossed size={75} strokeWidth={1.2} /></div><span className="web-hero-seal">BUEN SABOR<br />BUENA COMPAÑÍA</span></div></section>
      <main className="web-menu-layout"><section className="web-catalog"><div className="web-catalog-heading"><div><span className="web-eyebrow">ESCOGE LO QUE TE PROVOCA</span><h2>Nuestra carta</h2></div><label className="web-search"><Search size={18} /><input type="search" aria-label="Buscar en la carta" placeholder="Buscar tu favorito…" value={query} onChange={e => setQuery(e.target.value)} /></label></div>
        {menu?.mensaje && <p className="web-store-message">{menu.mensaje}</p>}
        <nav className="web-categories" aria-label="Categorías">{categories.map(c => <button key={c} aria-pressed={category === c} onClick={() => setCategory(c)}>{c}</button>)}</nav>
        {loading ? <div className="web-empty" role="status">Preparando nuestra carta…</div> : error ? <div className="web-empty"><h3>La carta no está disponible</h3><p role="alert">{error}</p><button className="web-button" onClick={loadMenu}><RefreshCw size={17} />Reintentar</button></div> : <div className="web-products">{products.map((p, index) => {
          const selected = cart.find(i => i.productoId === p.id)?.cantidad || 0
          return <article className="web-product" key={p.id}><div className={`web-product-art art-${index % 3}`} aria-hidden="true">{/bebida|bar/i.test(p.categoria) ? <GlassWater size={38} strokeWidth={1.2} /> : <UtensilsCrossed size={38} strokeWidth={1.2} />}<span>{p.categoria}</span></div><div className="web-product-content"><small>{p.limiteCantidad > 0 ? 'Preparado al momento' : 'Por ahora agotado'}</small><h3>{p.nombre}</h3><p>{p.descripcion || 'Un favorito de nuestra carta, preparado para disfrutar.'}</p><div><strong>{soles(p.precio)}</strong><button aria-label={`Agregar ${p.nombre}`} disabled={p.limiteCantidad === 0 || selected >= p.limiteCantidad || count >= 60} onClick={() => add(p)}><Plus size={18} />{selected ? `Añadir (${selected})` : 'Añadir'}</button></div></div></article>
        })}{!products.length && <div className="web-empty"><h3>{menu?.productos.length ? 'No encontramos ese plato' : 'La carta estará disponible pronto'}</h3><p>{menu?.productos.length ? 'Prueba con otro nombre o categoría.' : 'Comunícate con el local para conocer los platos del día.'}</p></div>}</div>}
      </section><aside className="web-desktop-cart"><CartPanel cart={cart} products={menu?.productos || []} update={update} remove={remove} disabled={busy || uncertain} onCheckout={() => setCheckout(true)} /></aside></main>
      {menu && <button className="web-mobile-cart" onClick={() => setCheckout(true)}><ShoppingBag size={20} /><span>Ver mi pedido <small>{count} producto{count !== 1 ? 's' : ''}</small></span><strong>{soles(cartTotal(cart, menu.productos))}</strong><ArrowUpRight size={18} /></button>}
    </>}
    <footer className="web-footer"><div><strong>{menu?.nombre || 'Recreo Mi Trampita'}</strong>{menu?.direccion && <span><MapPin size={15} />{menu.direccion}</span>}</div>{phone && <a href={`tel:${phone}`}><Phone size={16} />{menu.telefono}</a>}<p>Gracias por apoyar el sabor de nuestro recreo.</p></footer>
    {checkout && menu && <CheckoutDialog menu={menu} cart={cart} update={update} remove={remove} pending={pending.current} busy={busy} uncertain={uncertain} error={sendError} submit={submit} refresh={loadMenu} close={() => { setCheckout(false); setSendError('') }} />}
  </div>
}
