import { useEffect, useMemo, useRef, useState } from 'react'
import {
  AlertCircle, CheckCircle2, FileText, Minus, Package, Plus, Printer, Search,
  ShoppingCart, UserRound, X
} from 'lucide-react'
import { api } from './api'
import { storageRead, storageWrite } from './utils/storage'
import { receiptType, receiptLabel, requiresIdentification } from './utils/receipt'
import { usePrint } from './components/PrintManager'
import { permissions } from './permissions'
import TablesView from './TablesView'
import UniversalPaymentModal from './components/UniversalPaymentModal'
import CancelItemButton from './components/CancelItemButton'
import OrderStatus from './components/OrderStatus'
import useOperationEvents from './hooks/useOperationEvents'
import useOrderAlerts from './hooks/useOrderAlerts'
import { Volume2, VolumeX } from 'lucide-react'

const blankCustomer = {
  numeroDocumento: '',
  nombresRazonSocial: '',
  direccion: '',
  telefono: '',
  correo: '',
  fechaNacimiento: ''
}

function formatMoney(value) {
  return Number(value || 0).toFixed(2)
}

export default function SalesPos({ session }) {
  const { canOrder, canCharge } = permissions(session)
  const [areas, setAreas] = useState([])
  const [lastOrder, setLastOrder] = useState(null)
  const printDocument = usePrint()
  const printTicket = (sale, printType, area) => printDocument({ sale, area, type: printType === 'COMANDA' ? 'COMMAND' : 'RECEIPT', format: printType === 'COMANDA' ? '80mm' : sale.comprobante?.tipoComprobante === 'FACTURA' ? 'A4' : '80mm' })
  const [products, setProducts] = useState([])
  const [categories, setCategories] = useState([])
  const [brands, setBrands] = useState([])
  const [clients, setClients] = useState([])
  const [empresas, setEmpresas] = useState([])
  const [comprobantes, setComprobantes] = useState([])
  const [mesas, setMesas] = useState([])
  const [openSales, setOpenSales] = useState([])
  const [activeSaleId, setActiveSaleId] = useState('')
  const [selectedMesaId, setSelectedMesaId] = useState('')
  const [cart, setCart] = useState([])
  const [search, setSearch] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [brandFilter, setBrandFilter] = useState('')
  const [comprobanteId, setComprobanteId] = useState('')
  const [paymentId, setPaymentId] = useState(null)
  const [customer, setCustomer] = useState(blankCustomer)
  const [identifyCustomer, setIdentifyCustomer] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [lastSale, setLastSale] = useState(null)
  const [notice, setNotice] = useState('')
  const additionalKey = `pos-pending-additional-${session.id}`
  const pendingAdditional = useRef(storageRead('sessionStorage', additionalKey, null))
  const submitting = useRef(false)
  const [uncertainAdditional, setUncertainAdditional] = useState(Boolean(pendingAdditional.current))
  useEffect(() => {
    const pending = pendingAdditional.current
    if (loading || !pending) return
    const sale = openSales.find(s => s.id === pending.saleId)
    setActiveSaleId(String(pending.saleId)); setSelectedMesaId(String(sale?.mesa?.id || pending.mesaId || ''))
    setCart(pending.cart || [])
  }, [loading])

  useEffect(() => {
    setLoading(true)
    Promise.all([
      api.list('/api/productos'),
      api.list('/api/categorias'),
      api.list('/api/marcas'),
      api.list('/api/pos/clientes'),
      api.list('/api/pos/configuracion'),
      api.list('/api/tipos-comprobante'),
      api.list('/api/ventas/abiertas'),
      api.list('/api/mesas'),
      api.list('/api/areas')
    ]).then(([productData, categoryData, brandData, clientData, companyData, receiptData, salesData, mesaData, areaData]) => {
      setProducts(productData)
      setCategories(categoryData)
      setBrands(brandData)
      setClients(clientData)
      setEmpresas(companyData)
      setComprobantes(receiptData)
      setOpenSales(salesData.filter((sale) => sale.estado === 'ABIERTA' && sale.origenPedido === 'LOCAL'))
      setMesas(mesaData)
      setAreas(areaData)
      if (!comprobanteId && receiptData.length) {
        const preferred = [...receiptData].sort((a, b) => {
          const order = { NOTA_VENTA: 0, BOLETA: 1, FACTURA: 2 }
          return (order[receiptType(a.nombre)] ?? 3) - (order[receiptType(b.nombre)] ?? 3)
        })
        setComprobanteId(String(preferred[0].id))
      }
    }).catch((err) => setError(err.message)).finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    let mounted = true
    const refresh = async () => {
      try {
        const [tables, sales, stock] = await Promise.all([api.list('/api/mesas'), api.list('/api/ventas/abiertas'), api.list('/api/productos')])
        if (mounted) { setMesas(tables); setOpenSales(sales.filter(sale => sale.origenPedido === 'LOCAL')); setProducts(stock) }
      } catch (err) { if (mounted) setError(err.message) }
    }
    const timer = setInterval(refresh, 5000)
    return () => { mounted = false; clearInterval(timer) }
  }, [])

  useOperationEvents(async () => {
    try {
      const [tables, sales, stock] = await Promise.all([api.list('/api/mesas'), api.list('/api/ventas/abiertas'), api.list('/api/productos')])
      setMesas(tables); setOpenSales(sales.filter(sale => sale.origenPedido === 'LOCAL')); setProducts(stock)
    } catch (err) { setError(err.message) }
  })
  const readyKeys = useMemo(() => loading ? null : openSales.flatMap(sale =>
    sale.detalles.filter(item => item.estadoPreparacion === 'LISTO').map(item => item.id)), [openSales, loading])
  const alerts = useOrderAlerts(readyKeys)

  const selectedReceipt = useMemo(
    () => comprobantes.find((item) => String(item.id) === String(comprobanteId)),
    [comprobantes, comprobanteId]
  )
  const activeSale = useMemo(
    () => openSales.find((sale) => String(sale.id) === String(activeSaleId)),
    [openSales, activeSaleId]
  )
  const selectedType = receiptType(selectedReceipt?.nombre)
  const isInvoice = selectedType === 'FACTURA'
  const filteredProducts = useMemo(() => {
    const term = search.trim().toLowerCase()
    return products.filter((product) => {
      const matchesSearch = !term || product.nombre?.toLowerCase().includes(term) || product.codigoBarras?.toLowerCase().includes(term)
      const matchesCategory = !categoryFilter || String(product.categoria?.id) === String(categoryFilter)
      const matchesBrand = !brandFilter || String(product.marca?.id) === String(brandFilter)
      return matchesSearch && matchesCategory && matchesBrand
    })
  }, [products, search, categoryFilter, brandFilter])
  const additionalSubtotal = cart.reduce((sum, item) => sum + Number(item.precioVenta || 0) * item.cantidad, 0)
  const subtotal = additionalSubtotal + Number(activeSale?.subtotal || 0)
  const igv = Math.round((subtotal * 0.18 + Number.EPSILON) * 100) / 100
  const total = !cart.length && activeSale ? Number(activeSale.total) : Math.round((subtotal + igv + Number.EPSILON) * 100) / 100
  const additionalTotal = additionalSubtotal * 1.18
  const customerRequired = requiresIdentification(selectedType, total, identifyCustomer)

  useEffect(() => {
    const document = customer.numeroDocumento.trim()
    if (document.length < 6) return
    const existing = clients.find((client) => client.numeroDocumento === document)
    if (existing && existing.nombresRazonSocial !== customer.nombresRazonSocial) {
      setCustomer({
        numeroDocumento: existing.numeroDocumento || document,
        nombresRazonSocial: existing.nombresRazonSocial || '',
        direccion: existing.direccion || '',
        telefono: existing.telefono || '',
        correo: existing.correo || '',
        fechaNacimiento: existing.fechaNacimiento || ''
      })
    }
  }, [customer.numeroDocumento, clients])

  const updateCustomer = (field, value) => {
    setError('')
    setNotice('')
    setCustomer((current) => ({ ...current, [field]: value }))
  }

  const selectOpenSale = (value) => {
    if (saving || uncertainAdditional) return
    if (cart.length) return setError('Envía o quita los productos pendientes antes de cambiar de mesa.')
    setActiveSaleId(value)
    setCart([])
    setLastSale(null)
    setLastOrder(null)
    setError('')
    setNotice('')
    const sale = openSales.find((item) => String(item.id) === String(value))
    if (sale?.cliente) {
      setCustomer({
        numeroDocumento: sale.cliente.numeroDocumento || '',
        nombresRazonSocial: sale.cliente.nombresRazonSocial || '',
        direccion: sale.cliente.direccion || '',
        telefono: sale.cliente.telefono || '',
        correo: sale.cliente.correo || '',
        fechaNacimiento: sale.cliente.fechaNacimiento || ''
      })
    }
    if (sale) {
      setSelectedMesaId(String(sale.mesa?.id || ''))
    } else {
      setSelectedMesaId('')
    }
  }

  const selectMesa = async (mesa) => {
    if (saving || uncertainAdditional) return
    if (cart.length && String(selectedMesaId) !== String(mesa.id)) return setError('Envía o quita los productos pendientes antes de cambiar de mesa.')
    if (String(selectedMesaId) === String(mesa.id)) return
    const sale = openSales.find((item) => item.mesa?.id === mesa.id)
    if (sale) return selectOpenSale(String(sale.id))
    if (mesa.estado === 'ATENDIENDO') return setError('Actualiza el salón para cargar la comanda de esta mesa.')
    if (canOrder && mesa.estado === 'LIBRE') {
      setSaving(true); setError('')
      try {
        const opened = await api.patch(`/api/mesas/${mesa.id}/abrir`, {})
        setMesas(current => current.map(item => item.id === opened.id ? opened : item))
      } catch (err) { setError(err.message); return } finally { setSaving(false) }
    }
    setActiveSaleId('')
    setSelectedMesaId(String(mesa.id))
    setCart([])
    setLastSale(null)
    setLastOrder(null)
    setError('')
    setNotice('')
  }

  const addToCart = (product) => {
    if (!canOrder || saving || uncertainAdditional) return
    if (activeSale?.estadoCuenta === 'CERRADA') return setError('La cuenta ya está pagada. Finaliza la entrega y el cierre.')
    if (!selectedMesaId) return setError('Selecciona una mesa antes de tomar el pedido.')
    if (Number(product.stockActual) <= 0) return setError('Este producto no tiene stock disponible.')
    setError('')
    setCart((current) => {
      const found = current.find((item) => item.id === product.id)
      if (!found) return [...current, { ...product, cantidad: 1 }]
      if (found.cantidad >= product.stockActual) {
        setError(`Stock insuficiente para ${product.nombre}.`)
        return current
      }
      return current.map((item) => item.id === product.id ? { ...item, cantidad: item.cantidad + 1 } : item)
    })
  }

  const updateQuantity = (id, delta) => {
    if (saving || uncertainAdditional) return
    setError('')
    setCart((current) => current.flatMap((item) => {
      if (item.id !== id) return [item]
      const quantity = item.cantidad + delta
      if (quantity > item.stockActual) {
        setError(`No puedes superar el stock de ${item.nombre}.`)
        return [item]
      }
      return quantity > 0 ? [{ ...item, cantidad: quantity }] : []
    }))
  }

  const removeFromCart = (id) => !saving && !uncertainAdditional && setCart((current) => current.filter((item) => item.id !== id))

  const sendAdditional = async () => {
    if (submitting.current) return
    submitting.current = true; setSaving(true)
    const pending = pendingAdditional.current
    try {
      const updated = await api.patch(`/api/ventas/${pending.saleId}/items`, pending.body)
      setOpenSales(current => current.map(sale => sale.id === updated.id ? updated : sale).filter(sale => sale.estado === 'ABIERTA'))
      setCart([])
      setLastOrder({ ...updated, detalles: updated.detalles.filter(d => d.claveComanda === pending.body.claveOperacion) })
      pendingAdditional.current = null; storageWrite('sessionStorage', additionalKey, null); setUncertainAdditional(false)
      api.list('/api/productos').then(setProducts).catch(err => setError(err.message))
      setNotice(`Adicional registrado una sola vez. Comanda ${updated.numeroComprobante} actualizada.`)
    } catch (err) {
      if (err.status === 0 || err.status >= 500) {
        setUncertainAdditional(true); setError('No se confirmó la tanda. Reintenta la misma comanda para comprobarla sin duplicar stock.')
      } else { pendingAdditional.current = null; storageWrite('sessionStorage', additionalKey, null); setUncertainAdditional(false); setError(err.message) }
    } finally { submitting.current = false; setSaving(false) }
  }

  const submitSale = async () => {
    if (!canOrder || saving || submitting.current) return
    if (pendingAdditional.current) return sendAdditional()
    if (activeSale?.estadoCuenta === 'CERRADA') return setError('La cuenta ya está pagada. Finaliza la entrega y el cierre.')
    if (!selectedMesaId) return setError('Faltan datos de la mesa. Selecciona una mesa del salón.')
    if (activeSaleId && !activeSale) return setError('La comanda ya no está abierta. Vuelve a seleccionar la mesa.')
    setError('')
    setNotice('')
    if (activeSale) {
      if (!cart.length) return setError('Agrega al menos un producto adicional.')
      pendingAdditional.current = { saleId: activeSale.id, mesaId: activeSale.mesa?.id, cart,
        body: { claveOperacion: crypto.randomUUID(), items: cart.map(item => ({ productoId: item.id, cantidad: item.cantidad, observaciones: item.observaciones || '' })) } }
      storageWrite('sessionStorage', additionalKey, pendingAdditional.current)
      return sendAdditional()
    }
    const document = customer.numeroDocumento.trim()
    const name = customer.nombresRazonSocial.trim()
    if (!empresas.length) return setError('Solicita al administrador completar los datos fiscales en Configuración.')
    if (!selectedReceipt) return setError('Selecciona el tipo de comprobante.')
    if (!cart.length) return setError('Agrega al menos un producto al carrito.')
    if (customerRequired && !(isInvoice ? /^\d{11}$/ : /^\d{8}$/).test(document)) return setError(isInvoice ? 'Para una factura debes ingresar un RUC de 11 dígitos.' : 'La boleta identificada requiere DNI de 8 dígitos.')
    if (customerRequired && !name) return setError('Ingresa el nombre o razón social del cliente.')
    if (isInvoice && !customer.direccion.trim()) return setError('La dirección es obligatoria para una factura.')
    if (customerRequired && customer.correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customer.correo)) return setError('El correo del cliente no es válido.')
    submitting.current = true; setSaving(true)
    try {
      const sale = await api.create('/api/ventas', {
        empresaId: empresas[0].id,
        cliente: customerRequired ? {
          ...customer,
          numeroDocumento: document,
          nombresRazonSocial: name,
          direccion: customer.direccion.trim(),
          telefono: customer.telefono.trim(),
          correo: customer.correo.trim(),
          fechaNacimiento: customer.fechaNacimiento || null
        } : null,
        tipoComprobanteId: Number(selectedReceipt.id),
        numeroComprobante: `${selectedReceipt.serie}-${crypto.randomUUID().slice(0, 18)}`,
        mesaId: selectedMesaId ? Number(selectedMesaId) : null,
        items: cart.map((item) => ({ productoId: item.id, cantidad: item.cantidad, observaciones: item.observaciones || '' }))
      })
      setLastOrder(sale)
      api.list('/api/productos').then(setProducts).catch(err => setError(err.message))
      setNotice('Comanda registrada. Puedes imprimir el pedido para cocina.')
      setCart([])
      setActiveSaleId(String(sale.id))
      setSelectedMesaId(String(sale.mesa?.id || ''))
      setOpenSales((current) => [...current, sale].filter((item) => item.estado === 'ABIERTA'))
      if (sale.mesa?.id) setMesas((current) => current.map((mesa) => mesa.id === sale.mesa.id ? { ...mesa, estado: 'ATENDIENDO' } : mesa))
    } catch (err) {
      setError(err.message || 'No se pudo registrar la venta.')
    } finally {
      submitting.current = false
      setSaving(false)
    }
  }

  const paymentUpdated = (sale) => {
    if (sale.estado === 'CERRADA') {
      setLastSale(sale); setLastOrder(null)
      setOpenSales(current => current.filter(item => item.id !== sale.id))
      setMesas(current => current.map(mesa => mesa.id === sale.mesa?.id ? { ...mesa, estado: 'LIBRE' } : mesa))
      setActiveSaleId(''); setSelectedMesaId(''); setCart([])
      setNotice(`Cuenta ${sale.numeroComprobante} cerrada. La mesa quedó libre.`)
    } else setOpenSales(current => current.map(item => item.id === sale.id ? sale : item))
  }

  const serveItem = async (item) => {
    if (saving) return
    setSaving(true); setError('')
    try {
      await api.patch(`/api/ventas/items/${item.id}/servir`, { estado: 'SERVIDO', estadoActual: 'LISTO' })
      const updated = await api.list(`/api/ventas/${activeSale.id}`)
      setOpenSales(current => current.map(sale => sale.id === updated.id ? updated : sale))
      setNotice('Plato entregado a la mesa.')
    } catch (err) { setError(err.message) } finally { setSaving(false) }
  }

  const requestBill = async () => {
    if (saving || !activeSale) return
    setSaving(true); setError('')
    try {
      const updated = await api.patch(`/api/ventas/${activeSale.id}/solicitar-cuenta`, {})
      setOpenSales(current => current.map(sale => sale.id === updated.id ? updated : sale))
      setNotice('Cuenta solicitada. Caja ya recibió el aviso.')
    } catch (err) { setError(err.message) } finally { setSaving(false) }
  }

  const changeTableState = async (action) => {
    setSaving(true); setError('')
    try {
      const table = await api.patch(`/api/mesas/${selectedMesaId}/${action}`, {})
      setMesas(current => current.map(mesa => mesa.id === table.id ? table : mesa))
      setNotice(action === 'abrir' ? 'Mesa ocupada. Puedes tomar el pedido.' : 'Mesa liberada.')
    } catch (err) { setError(err.message) } finally { setSaving(false) }
  }

  if (loading) return <div className="loading">Cargando productos y configuración del POS...</div>

  return <>
    <div className="page-heading compact">
      <div><span className="eyebrow">Operación / POS</span><h1>Punto de Venta</h1><p className="muted">{canOrder ? 'Selecciona una mesa y registra su comanda.' : 'Selecciona una comanda para cobrar y liberar la mesa.'}</p></div>
      <div className="pos-company-badge"><span className="workspace-dot" /> {empresas[0]?.nombreComercial || empresas[0]?.razonSocial || 'Configuración fiscal pendiente'}</div>
    </div>
    <div className="cashier-toolbar"><button className="secondary-button sound-button" aria-pressed={alerts.sound} onClick={alerts.toggleSound}>{alerts.sound ? <Volume2 size={16} /> : <VolumeX size={16} />}{alerts.sound ? 'Sonido activo' : 'Activar sonido'}</button></div>
    {alerts.incoming > 0 && <div className="operation-alert" role="status"><span>{alerts.incoming} plato(s) listo(s) para recoger en cocina.</span><button onClick={alerts.dismiss}>Entendido</button></div>}
    <TablesView areas={areas} mesas={mesas} openSales={openSales} selectedMesaId={selectedMesaId} onSelect={selectMesa} disabled={saving || uncertainAdditional} />
    {selectedMesaId && !activeSale && <div className="table-actions">
      {canOrder && mesas.find(mesa => String(mesa.id) === String(selectedMesaId))?.estado === 'LIBRE' && <button className="secondary-button" disabled={saving} onClick={() => changeTableState('abrir')}>Ocupar mesa</button>}
      {canCharge && mesas.find(mesa => String(mesa.id) === String(selectedMesaId))?.estado === 'OCUPADA' && <button className="secondary-button" disabled={saving} onClick={() => changeTableState('liberar')}>Liberar mesa sin pedido</button>}
    </div>}
    <div className={`pos-layout ${!canOrder ? 'cashier-layout' : ''}`}>
      {canOrder && <section className="panel pos-products">
        <div className="table-toolbar">
          <div className="search-box"><Search size={16} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar por nombre o código..." /></div>
          <span className="result-count">{filteredProducts.length} productos</span>
        </div>
        <div className="catalog-filters">
          <div className="catalog-filter-row"><strong>Comidas por categoría</strong><button type="button" className={!categoryFilter ? 'filter-chip active' : 'filter-chip'} onClick={() => setCategoryFilter('')}>Todas</button>{categories.map((category) => <button type="button" className={String(category.id) === String(categoryFilter) ? 'filter-chip active' : 'filter-chip'} key={category.id} onClick={() => setCategoryFilter(String(category.id))}>{category.nombre}</button>)}</div>
          <div className="catalog-filter-row"><strong>Bebidas por marca</strong><button type="button" className={!brandFilter ? 'filter-chip active' : 'filter-chip'} onClick={() => setBrandFilter('')}>Todas</button>{brands.filter((brand) => brand.nombre?.toLowerCase() !== 'sin marca').map((brand) => <button type="button" className={String(brand.id) === String(brandFilter) ? 'filter-chip active' : 'filter-chip'} key={brand.id} onClick={() => setBrandFilter(String(brand.id))}>{brand.nombre}</button>)}</div>
        </div>
        <div className="product-grid">
          {filteredProducts.map((product) => <button type="button" className="product-card" key={product.id} onClick={() => addToCart(product)} disabled={saving || uncertainAdditional || product.stockActual <= 0 || activeSale?.estadoCuenta === 'CERRADA'}>
            <div className="product-card-top"><Package size={19} /><strong>S/ {formatMoney(product.precioVenta)}</strong></div>
            <strong>{product.nombre}</strong><small>{product.codigoBarras}</small>
            <span className={product.stockActual <= product.stockMinimo ? 'stock-warning' : 'stock-ok'}>Stock: {product.stockActual}</span>
          </button>)}
          {!filteredProducts.length && <EmptyPos text="No se encontraron productos." />}
        </div>
      </section>}

      <section className="panel pos-checkout">
        <div className="panel-head"><div><span className="eyebrow">{activeSale ? 'Comanda abierta' : canOrder ? 'Pedido' : 'Caja'}</span><h3>{activeSale ? `Mesa ${activeSale.mesa?.numero}` : canOrder ? `Carrito (${cart.length})` : 'Selecciona una comanda'}</h3></div><ShoppingCart size={20} className="accent-icon" /></div>
        <div className="cart-list">
          <label className="open-sale-selector"><span>Venta / mesa</span><select disabled={saving || uncertainAdditional} value={activeSaleId} onChange={(event) => selectOpenSale(event.target.value)}><option value="">Nueva venta {selectedMesaId ? `· Mesa ${mesas.find((mesa) => String(mesa.id) === String(selectedMesaId))?.numero || ''}` : '· Selecciona una mesa'}</option>{openSales.map((sale) => <option key={sale.id} value={sale.id}>Mesa {sale.mesa?.numero || 'sin mesa'} · {sale.numeroComprobante} · S/ {formatMoney(sale.total)}</option>)}</select></label>
          {activeSale && <div className="open-sale-summary"><strong>Pedido actual</strong>{(activeSale.detalles || []).map(detail => <div key={detail.id} className="sale-item-status">
            <span>{detail.cantidad} × {detail.producto?.nombre || 'Producto'}<OrderStatus state={detail.estadoPreparacion} /></span>
            <b>S/ {formatMoney(detail.subtotal)}</b>
            {canOrder && detail.estadoPreparacion === 'LISTO' && <button type="button" className="secondary-button" disabled={saving} onClick={() => serveItem(detail)}>Entregado</button>}
            {canCharge && <CancelItemButton item={detail} onUpdated={updated => {
              setOpenSales(current => updated.estado === 'ANULADA' ? current.filter(sale => sale.id !== updated.id) : current.map(sale => sale.id === updated.id ? updated : sale))
              if (updated.estado === 'ANULADA') { setActiveSaleId(''); setSelectedMesaId(''); setCart([]) }
              api.list('/api/productos').then(setProducts).catch(err => setError(err.message))
              api.list('/api/mesas').then(setMesas).catch(err => setError(err.message))
              setNotice('Plato cancelado. Stock y saldo actualizados.')
            }} />}
          </div>)}</div>}
          {!cart.length ? (!activeSale && <EmptyPos text={canOrder ? 'Agrega productos para comenzar.' : 'Selecciona una mesa con comanda abierta.'} />) : cart.map((item) => <div className="cart-item" key={item.id}>
            <div className="cart-item-info"><strong>{item.nombre}</strong><small>S/ {formatMoney(item.precioVenta)} c/u · stock {item.stockActual} · {item.areaDestino}</small><label>Observaciones<input maxLength={255} disabled={saving || uncertainAdditional} value={item.observaciones || ''} onChange={e => setCart(current => current.map(p => p.id === item.id ? { ...p, observaciones: e.target.value } : p))} placeholder="Sin ají, sin hielo…" /></label></div>
            <div className="quantity-control"><button type="button" disabled={saving || uncertainAdditional} onClick={() => updateQuantity(item.id, -1)}><Minus size={13} /></button><b>{item.cantidad}</b><button type="button" disabled={saving || uncertainAdditional} onClick={() => updateQuantity(item.id, 1)}><Plus size={13} /></button></div>
            <button type="button" className="icon-button" disabled={saving || uncertainAdditional} onClick={() => removeFromCart(item.id)} title="Quitar producto"><X size={15} /></button>
          </div>)}
        </div>

        <div className="checkout-form">
          {!activeSale && canOrder && <>
          <div className="checkout-section-title"><FileText size={15} /> Comprobante solicitado</div>
          <div className="receipt-options" role="group" aria-label="Tipo de comprobante">
            {comprobantes.map((item) => <button type="button" key={item.id} aria-pressed={String(item.id) === String(comprobanteId)} className={`receipt-option ${String(item.id) === String(comprobanteId) ? 'selected' : ''}`} disabled={saving} onClick={() => { setComprobanteId(String(item.id)); setError('') }}>
              <FileText size={17} /><span>{receiptLabel(item.nombre)}</span><small>{item.serie}</small>
            </button>)}
          </div>
          {selectedType === 'NOTA_VENTA' && <p className="muted">Ticket simple sin datos del cliente. Se imprimirá como consumidor final.</p>}
          {selectedType === 'BOLETA' && <>
            {total <= 700 && <label className="customer-identification"><input type="checkbox" checked={identifyCustomer} disabled={saving} onChange={event => setIdentifyCustomer(event.target.checked)} />Emitir Boleta a nombre del cliente</label>}
            <p className="muted">DNI y nombre obligatorios para una Boleta identificada o mayor a S/ 700.</p>
          </>}
          {customerRequired && <>
          <div className="checkout-section-title"><UserRound size={15} /> Datos del cliente</div>
          <div className="inline-fields">
            <label><span>{isInvoice ? 'RUC' : 'DNI'} *</span><input required inputMode="numeric" pattern={isInvoice ? '[0-9]{11}' : '[0-9]{8}'} maxLength={isInvoice ? 11 : 8} value={customer.numeroDocumento} onChange={(event) => updateCustomer('numeroDocumento', event.target.value.replace(/\D/g, ''))} placeholder={isInvoice ? '11 dígitos' : '8 dígitos'} /></label>
            <label><span>{isInvoice ? 'Razón social' : 'Nombre completo'} *</span><input maxLength={150} value={customer.nombresRazonSocial} onChange={(event) => updateCustomer('nombresRazonSocial', event.target.value)} placeholder="Nombre del cliente" /></label>
          </div>
          <label><span>Dirección {isInvoice ? '*' : '(opcional)'}</span><input maxLength={255} value={customer.direccion} onChange={(event) => updateCustomer('direccion', event.target.value)} placeholder="Dirección fiscal o domicilio" /></label>
          <div className="inline-fields">
            <label><span>Teléfono</span><input maxLength={20} value={customer.telefono} onChange={(event) => updateCustomer('telefono', event.target.value)} /></label>
            <label><span>Correo</span><input type="email" maxLength={100} value={customer.correo} onChange={(event) => updateCustomer('correo', event.target.value)} /></label>
          </div>

          <label><span>Fecha de nacimiento (opcional)</span><input type="date" value={customer.fechaNacimiento} onChange={event => updateCustomer('fechaNacimiento', event.target.value)} /></label>
          </>}
          {!comprobantes.length && <div className="form-error">No hay comprobantes configurados.</div>}
          </>} {/* Datos de cliente y comprobante */}
          <div className="totals"><div><span>Subtotal</span><b>S/ {formatMoney(subtotal)}</b></div><div><span>IGV (18%)</span><b>S/ {formatMoney(igv)}</b></div><div className="total-line"><strong>Total</strong><strong>S/ {formatMoney(total)}</strong></div>{activeSale && <div><span>Abonado / Saldo actual</span><b>S/ {formatMoney(activeSale.totalPagado)} / S/ {formatMoney(activeSale.saldoPendiente)}</b></div>}</div>
          {error && <div className="form-error" role="alert"><AlertCircle size={15} /> {error}</div>}
          {notice && <div className="sale-success" role="status"><CheckCircle2 size={16} /><span>{notice}</span></div>}
          {lastOrder && canOrder && <div className="command-print-actions">{['COCINA', 'BAR'].map(area => <button type="button" className="secondary-button" key={area} onClick={() => printTicket(lastOrder, 'COMANDA', area)}><Printer size={15} />Comanda {area}</button>)}</div>}
          {lastOrder && canOrder && <button type="button" className="secondary-button full" onClick={() => printTicket(lastOrder, 'COMANDA')}><Printer size={15} /> Imprimir comanda completa</button>}
          {lastSale && canCharge && <div className="sale-success"><CheckCircle2 size={16} /><span>Venta cobrada.</span><button type="button" className="print-sale-button" onClick={() => printTicket(lastSale, 'COMPROBANTE')}><Printer size={15} /> Imprimir comprobante</button></div>}
          {uncertainAdditional && <p role="status">Hay una tanda pendiente de comprobar. Se conservarán sus productos y su clave al reintentar.</p>}
          {canOrder && <button type="button" className="primary-button full" onClick={submitSale} disabled={saving || (!uncertainAdditional && (!cart.length || activeSale?.estadoCuenta === 'CERRADA'))}>{saving ? 'Guardando pedido...' : uncertainAdditional ? 'Comprobar / reintentar comanda' : activeSale ? `Enviar adicional S/ ${formatMoney(additionalTotal)}` : `Enviar comanda S/ ${formatMoney(total)}`}</button>}
          {canOrder && activeSale && <button type="button" className="secondary-button full" disabled={saving || cart.length > 0 || activeSale.cuentaSolicitada || activeSale.detalles.some(item => !['SERVIDO', 'CANCELADO'].includes(item.estadoPreparacion))} onClick={requestBill}>{activeSale.cuentaSolicitada ? 'Cuenta solicitada a Caja' : 'Solicitar cuenta a Caja'}</button>}
          {canCharge && activeSale && <button type="button" className="secondary-button full" onClick={() => setPaymentId(activeSale.id)} disabled={saving || cart.length > 0}>Pagos parciales / cerrar cuenta</button>}
        </div>
      </section>
    </div>
    {paymentId && <UniversalPaymentModal key={paymentId} saleId={paymentId} onClose={() => setPaymentId(null)} onUpdated={paymentUpdated} />}
  </>
}

function EmptyPos({ text }) {
  return <div className="empty"><Package size={22} /><span>{text}</span></div>
}
