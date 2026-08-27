import { useEffect, useMemo, useState } from 'react'
import {
  AlertCircle, CheckCircle2, FileText, Minus, Package, Plus, Printer, Search,
  ShoppingCart, UserRound, X
} from 'lucide-react'
import { api } from './api'

const blankCustomer = {
  numeroDocumento: '',
  nombresRazonSocial: '',
  direccion: '',
  telefono: '',
  correo: ''
}

function formatMoney(value) {
  return Number(value || 0).toFixed(2)
}

function receiptLabel(name) {
  const value = (name || '').toUpperCase()
  if (value.includes('FACTURA')) return 'Factura'
  if (value.includes('BOLETA')) return 'Boleta'
  return 'Otro'
}

export default function SalesPos({ session }) {
  const [products, setProducts] = useState([])
  const [categories, setCategories] = useState([])
  const [brands, setBrands] = useState([])
  const [clients, setClients] = useState([])
  const [empresas, setEmpresas] = useState([])
  const [comprobantes, setComprobantes] = useState([])
  const [cart, setCart] = useState([])
  const [search, setSearch] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [brandFilter, setBrandFilter] = useState('')
  const [comprobanteId, setComprobanteId] = useState('')
  const [metodoPago, setMetodoPago] = useState('efectivo')
  const [customer, setCustomer] = useState(blankCustomer)
  const [amountReceived, setAmountReceived] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [lastSale, setLastSale] = useState(null)

  useEffect(() => {
    setLoading(true)
    Promise.all([
      api.list('/api/productos'),
      api.list('/api/categorias'),
      api.list('/api/marcas'),
      api.list('/api/clientes'),
      api.list('/api/empresas'),
      api.list('/api/tipos-comprobante')
    ]).then(([productData, categoryData, brandData, clientData, companyData, receiptData]) => {
      setProducts(productData)
      setCategories(categoryData)
      setBrands(brandData)
      setClients(clientData)
      setEmpresas(companyData)
      setComprobantes(receiptData)
      if (!comprobanteId && receiptData.length) {
        const preferred = [...receiptData].sort((a, b) => {
          const order = { BOLETA: 0, FACTURA: 1 }
          return (order[receiptLabel(a.nombre).toUpperCase()] ?? 2) - (order[receiptLabel(b.nombre).toUpperCase()] ?? 2)
        })
        setComprobanteId(String(preferred[0].id))
      }
    }).catch((err) => setError(err.message)).finally(() => setLoading(false))
  }, [])

  const selectedReceipt = useMemo(
    () => comprobantes.find((item) => String(item.id) === String(comprobanteId)),
    [comprobantes, comprobanteId]
  )
  const isInvoice = selectedReceipt?.nombre?.toUpperCase().includes('FACTURA')
  const filteredProducts = useMemo(() => {
    const term = search.trim().toLowerCase()
    return products.filter((product) => {
      const matchesSearch = !term || product.nombre?.toLowerCase().includes(term) || product.codigoBarras?.toLowerCase().includes(term)
      const matchesCategory = !categoryFilter || String(product.categoria?.id) === String(categoryFilter)
      const matchesBrand = !brandFilter || String(product.marca?.id) === String(brandFilter)
      return matchesSearch && matchesCategory && matchesBrand
    })
  }, [products, search, categoryFilter, brandFilter])
  const subtotal = cart.reduce((sum, item) => sum + Number(item.precioVenta || 0) * item.cantidad, 0)
  const igv = subtotal * 0.18
  const total = subtotal + igv
  const change = Math.max(0, Number(amountReceived || 0) - total)

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
        correo: existing.correo || ''
      })
    }
  }, [customer.numeroDocumento, clients])

  const updateCustomer = (field, value) => {
    setError('')
    setCustomer((current) => ({ ...current, [field]: value }))
  }

  const addToCart = (product) => {
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

  const removeFromCart = (id) => setCart((current) => current.filter((item) => item.id !== id))

  const submitSale = async () => {
    setError('')
    const document = customer.numeroDocumento.trim()
    const name = customer.nombresRazonSocial.trim()
    if (!empresas.length) return setError('Registra primero los datos de tu empresa en Administración > Empresas.')
    if (!selectedReceipt) return setError('Selecciona el tipo de comprobante.')
    if (!cart.length) return setError('Agrega al menos un producto al carrito.')
    if (!/^\d{6,20}$/.test(document)) return setError('El documento debe contener entre 6 y 20 dígitos.')
    if (!name) return setError('Ingresa el nombre o razón social del cliente.')
    if (isInvoice && !/^\d{11}$/.test(document)) return setError('Para una factura debes ingresar un RUC de 11 dígitos.')
    if (isInvoice && !customer.direccion.trim()) return setError('La dirección es obligatoria para una factura.')
    if (customer.correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customer.correo)) return setError('El correo del cliente no es válido.')
    if (metodoPago === 'efectivo' && Number(amountReceived || 0) < total) return setError('El monto recibido no puede ser menor que el total.')

    setSaving(true)
    try {
      const sale = await api.create('/api/ventas', {
        empresaId: empresas[0].id,
        usuarioId: session.id,
        cliente: {
          ...customer,
          numeroDocumento: document,
          nombresRazonSocial: name,
          direccion: customer.direccion.trim(),
          telefono: customer.telefono.trim(),
          correo: customer.correo.trim()
        },
        tipoComprobanteId: Number(selectedReceipt.id),
        numeroComprobante: `${selectedReceipt.serie}-${Date.now().toString().slice(-8)}`,
        metodoPago,
        items: cart.map((item) => ({ productoId: item.id, cantidad: item.cantidad }))
      })
      setLastSale({ ...sale, receiptName: selectedReceipt.nombre, receiptSeries: selectedReceipt.serie, customerName: name, customer, company: empresas[0], change })
      setCart([])
      setAmountReceived('')
      setProducts(await api.list('/api/productos'))
      setClients(await api.list('/api/clientes'))
    } catch (err) {
      setError(err.message || 'No se pudo registrar la venta.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="loading">Cargando productos y configuración del POS...</div>

  return <>
    <div className="page-heading compact">
      <div><span className="eyebrow">Operación / POS</span><h1>Punto de Venta</h1><p className="muted">Agrega productos, completa los datos del cliente y emite su comprobante.</p></div>
      <div className="pos-company-badge"><span className="workspace-dot" /> {empresas[0]?.nombreComercial || empresas[0]?.razonSocial || 'Empresa pendiente'}</div>
    </div>
    <div className="pos-layout">
      <section className="panel pos-products">
        <div className="table-toolbar">
          <div className="search-box"><Search size={16} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar por nombre o código..." /></div>
          <span className="result-count">{filteredProducts.length} productos</span>
        </div>
        <div className="catalog-filters">
          <div className="catalog-filter-row"><strong>Comidas por categoría</strong><button type="button" className={!categoryFilter ? 'filter-chip active' : 'filter-chip'} onClick={() => setCategoryFilter('')}>Todas</button>{categories.map((category) => <button type="button" className={String(category.id) === String(categoryFilter) ? 'filter-chip active' : 'filter-chip'} key={category.id} onClick={() => setCategoryFilter(String(category.id))}>{category.nombre}</button>)}</div>
          <div className="catalog-filter-row"><strong>Bebidas por marca</strong><button type="button" className={!brandFilter ? 'filter-chip active' : 'filter-chip'} onClick={() => setBrandFilter('')}>Todas</button>{brands.filter((brand) => brand.nombre?.toLowerCase() !== 'sin marca').map((brand) => <button type="button" className={String(brand.id) === String(brandFilter) ? 'filter-chip active' : 'filter-chip'} key={brand.id} onClick={() => setBrandFilter(String(brand.id))}>{brand.nombre}</button>)}</div>
        </div>
        <div className="product-grid">
          {filteredProducts.map((product) => <button type="button" className="product-card" key={product.id} onClick={() => addToCart(product)} disabled={product.stockActual <= 0}>
            <div className="product-card-top"><Package size={19} /><strong>S/ {formatMoney(product.precioVenta)}</strong></div>
            <strong>{product.nombre}</strong><small>{product.codigoBarras}</small>
            <span className={product.stockActual <= product.stockMinimo ? 'stock-warning' : 'stock-ok'}>Stock: {product.stockActual}</span>
          </button>)}
          {!filteredProducts.length && <EmptyPos text="No se encontraron productos." />}
        </div>
      </section>

      <section className="panel pos-checkout">
        <div className="panel-head"><div><span className="eyebrow">Cobro</span><h3>Carrito ({cart.length})</h3></div><ShoppingCart size={20} className="accent-icon" /></div>
        <div className="cart-list">
          {!cart.length ? <EmptyPos text="Agrega productos para comenzar." /> : cart.map((item) => <div className="cart-item" key={item.id}>
            <div className="cart-item-info"><strong>{item.nombre}</strong><small>S/ {formatMoney(item.precioVenta)} c/u · stock {item.stockActual}</small></div>
            <div className="quantity-control"><button type="button" onClick={() => updateQuantity(item.id, -1)}><Minus size={13} /></button><b>{item.cantidad}</b><button type="button" onClick={() => updateQuantity(item.id, 1)}><Plus size={13} /></button></div>
            <button type="button" className="icon-button" onClick={() => removeFromCart(item.id)} title="Quitar producto"><X size={15} /></button>
          </div>)}
        </div>

        <div className="checkout-form">
          <div className="checkout-section-title"><UserRound size={15} /> Datos del cliente</div>
          <div className="inline-fields">
            <label><span>{isInvoice ? 'RUC' : 'DNI / documento'} *</span><input inputMode="numeric" maxLength={20} value={customer.numeroDocumento} onChange={(event) => updateCustomer('numeroDocumento', event.target.value.replace(/\D/g, ''))} placeholder={isInvoice ? '11 dígitos' : 'DNI del cliente'} /></label>
            <label><span>{isInvoice ? 'Razón social' : 'Nombre completo'} *</span><input maxLength={150} value={customer.nombresRazonSocial} onChange={(event) => updateCustomer('nombresRazonSocial', event.target.value)} placeholder="Nombre del cliente" /></label>
          </div>
          <label><span>Dirección {isInvoice ? '*' : '(opcional)'}</span><input maxLength={255} value={customer.direccion} onChange={(event) => updateCustomer('direccion', event.target.value)} placeholder="Dirección fiscal o domicilio" /></label>
          <div className="inline-fields">
            <label><span>Teléfono</span><input maxLength={20} value={customer.telefono} onChange={(event) => updateCustomer('telefono', event.target.value)} /></label>
            <label><span>Correo</span><input type="email" maxLength={100} value={customer.correo} onChange={(event) => updateCustomer('correo', event.target.value)} /></label>
          </div>

          <div className="checkout-section-title"><FileText size={15} /> Tipo de comprobante</div>
          <div className="receipt-options" role="group" aria-label="Tipo de comprobante">
            {comprobantes.map((item) => <button type="button" key={item.id} className={`receipt-option ${String(item.id) === String(comprobanteId) ? 'selected' : ''}`} onClick={() => { setComprobanteId(String(item.id)); setError('') }}>
              <FileText size={17} /><span>{receiptLabel(item.nombre)}</span><small>{item.serie}</small>
            </button>)}
          </div>
          {!comprobantes.length && <div className="form-error">No hay comprobantes configurados.</div>}
          <div className="inline-fields">
            <label><span>Método de pago *</span><select value={metodoPago} onChange={(event) => setMetodoPago(event.target.value)}><option value="efectivo">Efectivo</option><option value="tarjeta">Tarjeta</option><option value="transferencia">Transferencia</option><option value="yape_plin">Yape / Plin</option></select></label>
          </div>
          {metodoPago === 'efectivo' && <label><span>Monto recibido</span><input type="number" min="0" step="0.01" value={amountReceived} onChange={(event) => setAmountReceived(event.target.value)} placeholder={`Mínimo S/ ${formatMoney(total)}`} /></label>}

          <div className="totals"><div><span>Subtotal</span><b>S/ {formatMoney(subtotal)}</b></div><div><span>IGV (18%)</span><b>S/ {formatMoney(igv)}</b></div><div className="total-line"><strong>Total</strong><strong>S/ {formatMoney(total)}</strong></div>{metodoPago === 'efectivo' && <div className="change-line"><span>Vuelto</span><b>S/ {formatMoney(change)}</b></div>}</div>
          {error && <div className="form-error"><AlertCircle size={15} /> {error}</div>}
          {lastSale && <div className="sale-success"><CheckCircle2 size={16} /><span>Venta {lastSale.receiptName} registrada para {lastSale.customerName}.</span><button type="button" className="print-sale-button" onClick={() => window.print()} title={`Imprimir ${receiptLabel(lastSale.receiptName).toLowerCase()}`}><Printer size={15} /> Imprimir comprobante</button></div>}
          <button type="button" className="primary-button full" onClick={submitSale} disabled={saving || !cart.length}>{saving ? 'Registrando venta...' : `Cobrar S/ ${formatMoney(total)}`}</button>
        </div>
      </section>
    </div>
    {lastSale && <section className="print-only receipt-paper">
      <div className="receipt-header"><strong>{lastSale.company?.razonSocial || 'MI TRAMPITA'}</strong><span>RUC: {lastSale.company?.ruc || '—'}</span><span>{lastSale.company?.direccion || ''}</span><span>{lastSale.company?.telefono || ''}</span></div>
      <div className="receipt-title"><strong>{receiptLabel(lastSale.receiptName).toUpperCase()}</strong><strong>{lastSale.numeroComprobante || `${lastSale.receiptSeries}-${lastSale.id}`}</strong></div>
      <div className="receipt-customer"><span>Cliente: {lastSale.customerName}</span><span>Documento: {lastSale.customer?.numeroDocumento || lastSale.cliente?.numeroDocumento || '—'}</span><span>Fecha: {lastSale.fechaVenta ? new Date(lastSale.fechaVenta).toLocaleString('es-PE') : new Date().toLocaleString('es-PE')}</span></div>
      <table><thead><tr><th>Descripción</th><th>Cant.</th><th>P. unit.</th><th>Total</th></tr></thead><tbody>{(lastSale.detalles || []).map((detail) => <tr key={detail.id || detail.producto?.id}><td>{detail.producto?.nombre || 'Producto'}</td><td>{detail.cantidad}</td><td>S/ {formatMoney(detail.precioUnitario)}</td><td>S/ {formatMoney(detail.subtotal)}</td></tr>)}</tbody></table>
      <div className="receipt-totals"><span>Subtotal: S/ {formatMoney(lastSale.subtotal)}</span><span>IGV: S/ {formatMoney(lastSale.igv)}</span><strong>Total: S/ {formatMoney(lastSale.total)}</strong></div>
      <p>Medio de pago: {lastSale.metodoPago || 'efectivo'}</p><p>Gracias por su compra.</p>
    </section>}
  </>
}

function EmptyPos({ text }) {
  return <div className="empty"><Package size={22} /><span>{text}</span></div>
}
