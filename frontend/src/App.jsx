import { useEffect, useMemo, useState } from 'react'
import {
  Archive, ArrowRight, BadgeDollarSign, BarChart3, Boxes, BriefcaseBusiness, ChevronRight,
  CircleUserRound, ClipboardList, Command, LayoutDashboard, LogOut, Menu, Package,
  Plus, ReceiptText, Search, Settings2, ShieldCheck, Store, Tags, Truck, UserRound,
  UsersRound, X, Zap, Minus, ShoppingCart, CheckCircle2, Pencil, Trash2
} from 'lucide-react'
import { api } from './api'
import SalesPos from './SalesPos'

const resources = {
  productos: { label: 'Productos', singular: 'producto', endpoint: '/api/productos', icon: Package, columns: [['nombre', 'Producto'], ['codigoBarras', 'Código'], ['precioVenta', 'Precio'], ['stockActual', 'Stock']] },
  clientes: { label: 'Clientes', singular: 'cliente', endpoint: '/api/clientes', icon: UsersRound, columns: [['numeroDocumento', 'Documento'], ['nombresRazonSocial', 'Nombre'], ['telefono', 'Teléfono'], ['correo', 'Correo']] },
  proveedores: { label: 'Proveedores', singular: 'proveedor', endpoint: '/api/proveedores', icon: Truck, columns: [['rucDni', 'RUC / DNI'], ['razonSocial', 'Razón social'], ['telefono', 'Teléfono'], ['correo', 'Correo']] },
  categorias: { label: 'Categorías', singular: 'categoría', endpoint: '/api/categorias', icon: Tags, columns: [['nombre', 'Nombre'], ['descripcion', 'Descripción']] },
  marcas: { label: 'Marcas', singular: 'marca', endpoint: '/api/marcas', icon: Archive, columns: [['nombre', 'Nombre']] },
  usuarios: { label: 'Usuarios', singular: 'usuario', endpoint: '/api/usuarios', icon: UserRound, columns: [['usuario', 'Usuario'], ['nombreCompleto', 'Nombre'], ['correoElectronico', 'Correo'], ['estado', 'Estado']] },
  roles: { label: 'Roles', singular: 'rol', endpoint: '/api/roles', icon: ShieldCheck, columns: [['nombre', 'Rol'], ['descripcion', 'Descripción']] },
  empresas: { label: 'Empresas', singular: 'empresa', endpoint: '/api/empresas', icon: BriefcaseBusiness, columns: [['ruc', 'RUC'], ['razonSocial', 'Razón social'], ['telefono', 'Teléfono']] },
  'tipos-comprobante': { label: 'Comprobantes', singular: 'tipo de comprobante', endpoint: '/api/tipos-comprobante', icon: ReceiptText, columns: [['nombre', 'Tipo'], ['serie', 'Serie']] }
}

const navGroups = [
  { title: 'Operación', items: [['dashboard', 'Inicio', LayoutDashboard], ['ventas', 'Ventas', ReceiptText], ['productos', 'Productos', Package]] },
  { title: 'Directorio', items: [['clientes', 'Clientes', UsersRound], ['proveedores', 'Proveedores', Truck], ['categorias', 'Categorías', Tags], ['marcas', 'Marcas', Archive]] },
  { title: 'Administración', reqAdmin: true, items: [['usuarios', 'Usuarios', UserRound], ['roles', 'Roles', ShieldCheck], ['empresas', 'Empresas', BriefcaseBusiness], ['tipos-comprobante', 'Comprobantes', ReceiptText]] },
]

function App() {
  const [session, setSession] = useState(() => {
    try {
      const s = localStorage.getItem('pos-session')
      return s ? JSON.parse(s) : null
    } catch(e) { return null }
  })
  const [view, setView] = useState('dashboard')
  const [mobileNav, setMobileNav] = useState(false)
  
  if (!session) return <Login onLogin={(data) => { localStorage.setItem('pos-session', JSON.stringify(data)); setSession(data) }} />
  return <Shell session={session} view={view} setView={(next) => { setView(next); setMobileNav(false) }} mobileNav={mobileNav} setMobileNav={setMobileNav} onLogout={() => { localStorage.removeItem('pos-session'); setSession(null) }} />
}

function Login({ onLogin }) {
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  
  async function submit(event) {
    event.preventDefault()
    if (!name.trim() || !password.trim()) return setError('Ingresa tu usuario y contraseña.')
    setLoading(true)
    setError('')
    try {
      const res = await api.create('/api/auth/login', { usuario: name.trim(), contrasena: password.trim() })
      onLogin(res)
    } catch(e) {
      setError(e.message || 'Credenciales inválidas o error de conexión.')
    } finally {
      setLoading(false)
    }
  }
  
  return <main className="login-page">
    <section className="login-art"><div className="brand-mark"><Store size={20} /> MI TRAMPITA</div><div className="login-quote"><span>01 / POS</span><h1>MI TRAMPITA<br /><em>Trabajo con ritmo.</em></h1><p>Todo para que tu tienda avance.</p></div><div className="art-grid" /></section>
    <section className="login-panel"><div className="login-form"><div className="mobile-brand"><Store size={20} /> MI TRAMPITA</div><span className="eyebrow">Punto de venta</span><h2>Bienvenido de vuelta</h2><p className="muted">Accede a tu espacio de trabajo.</p><form onSubmit={submit}><label>Usuario<input autoFocus value={name} onChange={(event) => setName(event.target.value)} placeholder="Tu nombre de usuario" /></label><label style={{marginTop: '15px'}}>Contraseña<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Tu contraseña" /></label>{error && <div className="form-error">{error}</div>}<button className="primary-button full" type="submit" disabled={loading}>{loading ? 'Iniciando sesión...' : 'Entrar'} <ArrowRight size={18} /></button></form><p className="login-note"><Zap size={14} /> Autenticación en vivo contra la base de datos PostgreSQL.</p></div><span className="login-footer">Mi Trampita · 2026</span></section>
  </main>
}

function Shell({ session, view, setView, mobileNav, setMobileNav, onLogout }) {
  const isAdmin = session.roles?.some(r => r.toLowerCase().includes('admin')) || false;

  return <div className="app-shell"><aside className={`sidebar ${mobileNav ? 'open' : ''}`}><div className="sidebar-top"><div className="brand-mark dark"><Store size={19} /> MI TRAMPITA</div><button className="icon-button close-nav" onClick={() => setMobileNav(false)} aria-label="Cerrar menú"><X size={20} /></button></div><div className="workspace"><span className="workspace-dot" /><span>Mi tienda</span><ChevronRight size={14} /></div><nav>{navGroups.map((group) => {
    if (group.reqAdmin && !isAdmin) return null;
    return <div className="nav-group" key={group.title}><span className="nav-label">{group.title}</span>{group.items.map(([key, label, Icon]) => <button className={`nav-item ${view === key ? 'active' : ''}`} key={key} onClick={() => setView(key)}><Icon size={17} /><span>{label}</span>{key === 'ventas' && <span className="nav-badge">POS</span>}</button>)}</div>
  })}</nav><div className="sidebar-bottom">
  {isAdmin && <button className="nav-item" onClick={() => setView('empresas')}><Settings2 size={17} /><span>Configuración</span></button>}
  <div className="user-card"><div className="avatar">{session.nombreCompleto?.charAt(0).toUpperCase() || 'U'}</div><div><strong title={session.nombreCompleto}>{session.usuario}</strong><small>{isAdmin ? 'Administrador' : 'Vendedor'}</small></div><button className="icon-button" onClick={onLogout} title="Cerrar sesión"><LogOut size={16} /></button></div></div></aside>{mobileNav && <button className="scrim" onClick={() => setMobileNav(false)} aria-label="Cerrar menú" />}<main className="main-content"><header className="topbar"><button className="icon-button menu-button" onClick={() => setMobileNav(true)} aria-label="Abrir menú"><Menu size={21} /></button><div className="crumb"><Command size={16} /><span>/</span><strong>{view === 'dashboard' ? 'Inicio' : view === 'ventas' ? 'Ventas' : resources[view]?.label}</strong></div><div className="top-actions"><span className="connection"><span /> API conectada</span><button className="icon-button" title="Perfil" onClick={() => { if(isAdmin) setView('usuarios') }}><CircleUserRound size={19} /></button></div></header><div className="content">{view === 'dashboard' ? <Dashboard setView={setView} /> : view === 'ventas' ? <Sales session={session} /> : <ResourceView resource={resources[view]} />}</div></main></div>
}

function Dashboard({ setView }) { return <><DashboardLegacy setView={setView} /><RecentSales /></> }

function RecentSales() {
  const [sales, setSales] = useState([])
  const [error, setError] = useState('')
  useEffect(() => { api.list('/api/ventas').then(setSales).catch((err) => setError(err.message)) }, [])
  const recent = [...sales].sort((a, b) => new Date(b.fechaVenta || 0) - new Date(a.fechaVenta || 0)).slice(0, 5)
  return <section className="panel recent-panel"><div className="panel-head"><div><span className="eyebrow">Actividad</span><h3>Últimas ventas</h3></div><ReceiptText size={20} className="accent-icon" /></div>{error ? <div className="api-error">No se pudo cargar la actividad.</div> : recent.length ? <div className="recent-sales">{recent.map((sale) => <div className="recent-sale" key={sale.id}><span className="recent-sale-icon"><ReceiptText size={15} /></span><div><strong>{sale.tipoComprobante?.nombre || 'Comprobante'} · {sale.numeroComprobante}</strong><small>{sale.cliente?.nombresRazonSocial || 'Cliente'} · {sale.fechaVenta ? new Date(sale.fechaVenta).toLocaleString('es-PE') : 'Fecha pendiente'}</small></div><b>S/ {Number(sale.total || 0).toFixed(2)}</b></div>)}</div> : <EmptyState text="Todavía no hay ventas registradas." />}</section>
}

function DashboardLegacy({ setView }) {
  const [products, setProducts] = useState([])
  const [sales, setSales] = useState([])
  useEffect(() => { api.list('/api/productos').then(setProducts).catch(() => setProducts([])); api.list('/api/ventas').then(setSales).catch(() => setSales([])) }, [])
  const lowStock = products.filter((item) => item.stockActual <= item.stockMinimo).length
  return <><div className="page-heading"><div><span className="eyebrow">Hoy</span><h1>Buen día, <em>equipo.</em></h1><p className="muted">Una mirada rápida a tu operación.</p></div><button className="primary-button" onClick={() => setView('ventas')}><Plus size={17} /> Nueva venta</button></div><div className="metric-grid"><Metric label="Ventas registradas" value={sales.length} detail="Desde el backend" icon={BadgeDollarSign} tone="green" /><Metric label="Productos" value={products.length} detail="Catálogo activo" icon={Package} tone="yellow" /><Metric label="Stock por revisar" value={lowStock} detail="Bajo mínimo" icon={Boxes} tone="orange" /><Metric label="Operación" value="Activa" detail="localhost:9090" icon={BarChart3} tone="blue" /></div><div className="dashboard-grid"><section className="panel spotlight"><div className="panel-head"><div><span className="eyebrow">Acciones rápidas</span><h3>¿Qué quieres hacer?</h3></div><Zap size={20} className="accent-icon" /></div><div className="quick-grid"><QuickAction icon={ReceiptText} title="Registrar venta" detail="Cobrar y actualizar stock" onClick={() => setView('ventas')} /><QuickAction icon={Package} title="Nuevo producto" detail="Añadir al catálogo" onClick={() => setView('productos')} /><QuickAction icon={UsersRound} title="Nuevo cliente" detail="Crear ficha de cliente" onClick={() => setView('clientes')} /><QuickAction icon={Truck} title="Ver proveedores" detail="Consultar directorio" onClick={() => setView('proveedores')} /></div></section><section className="panel stock-panel"><div className="panel-head"><div><span className="eyebrow">Inventario</span><h3>Stock bajo</h3></div><button className="text-button" onClick={() => setView('productos')}>Ver todo <ArrowRight size={14} /></button></div>{products.filter((item) => item.stockActual <= item.stockMinimo).slice(0, 4).map((item) => <div className="stock-row" key={item.id}><div className="product-symbol"><Package size={15} /></div><div><strong>{item.nombre}</strong><small>{item.codigoBarras}</small></div><span className="stock-value">{item.stockActual} <small>/ {item.stockMinimo}</small></span></div>)}{lowStock === 0 && <EmptyState text="Todo el inventario está en orden." />}</section></div></>
}

function Metric({ label, value, detail, icon: Icon, tone }) { return <div className="metric"><div className={`metric-icon ${tone}`}><Icon size={19} /></div><div><span>{label}</span><strong>{value}</strong><small>{detail}</small></div></div> }
function QuickAction({ icon: Icon, title, detail, onClick }) { return <button className="quick-action" onClick={onClick}><span className="quick-icon"><Icon size={18} /></span><span><strong>{title}</strong><small>{detail}</small></span><ArrowRight size={16} /></button> }

function ResourceView({ resource }) {
  const [items, setItems] = useState([])
  const [query, setQuery] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = () => { setLoading(true); api.list(resource.endpoint).then(setItems).catch((err) => setError(err.message)).finally(() => setLoading(false)) }
  useEffect(load, [resource.endpoint])
  const filtered = useMemo(() => items.filter((item) => JSON.stringify(item).toLowerCase().includes(query.toLowerCase())), [items, query])
  const openNew = () => { setEditing(null); setShowForm(true) }
  const openEdit = (item) => { setEditing(item); setShowForm(true) }
  return <><div className="page-heading compact"><div><span className="eyebrow">Directorio / {resource.label}</span><h1>{resource.label}</h1><p className="muted">Gestiona la información conectada a tu base de datos.</p></div><button className="primary-button" onClick={openNew}><Plus size={17} /> Nuevo {resource.singular}</button></div><section className="panel table-panel"><div className="table-toolbar"><div className="search-box"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={`Buscar ${resource.label.toLowerCase()}...`} /></div><span className="result-count">{filtered.length} registros</span></div>{error && <div className="api-error">{error}</div>}{loading ? <div className="loading">Cargando datos...</div> : <DataTable resource={resource} items={filtered} onRefresh={load} onEdit={openEdit} onError={setError} />}</section>{showForm && <ResourceForm key={`${resource.endpoint}-${editing?.id || 'new'}`} resource={resource} item={editing} onClose={() => setShowForm(false)} onSaved={() => { setShowForm(false); setEditing(null); load() }} />}</>
}

function DataTable({ resource, items, onRefresh, onEdit, onError }) { return items.length ? <div className="table-wrap"><table><thead><tr>{resource.columns.map(([, label]) => <th key={label}>{label}</th>)}<th>Acciones</th></tr></thead><tbody>{items.map((item) => <tr key={item.id}><>{resource.columns.map(([key]) => <td key={key}>{key.includes('precio') ? 'S/ ' + Number(item[key] || 0).toFixed(2) : (typeof item[key] === 'object' && item[key] !== null ? item[key].nombre || item[key].razonSocial || item[key].nombreCategoria || item[key].nombreMarca || 'Objeto' : item[key] ?? '—')}</td>)}</><td className="row-actions"><button className="row-action" onClick={() => onEdit(item)} title={`Modificar ${resource.singular}`}><Pencil size={15} /></button><button className="row-action danger" onClick={() => { if (confirm(`¿Eliminar este ${resource.singular}? Esta acción no se puede deshacer.`)) api.remove(`${resource.endpoint}/${item.id}`).then(onRefresh).catch((err) => onError(err.message || 'No se pudo eliminar el registro.')) }} title={`Eliminar ${resource.singular}`}><Trash2 size={15} /></button></td></tr>)}</tbody></table></div> : <EmptyState text="No hay registros para mostrar." /> }
function EmptyState({ text }) { return <div className="empty"><ClipboardList size={22} /><span>{text}</span></div> }

function ResourceForm({ resource, item, onClose, onSaved }) {
  const initialForm = useMemo(() => {
    let base
    switch (resource.label) {
      case 'Categorías': base = { nombre: '', descripcion: '' }; break
      case 'Marcas': base = { nombre: '' }; break
      case 'Clientes': base = { numeroDocumento: '', nombresRazonSocial: '', direccion: '', telefono: '', correo: '' }; break
      case 'Proveedores': base = { rucDni: '', razonSocial: '', telefono: '', correo: '' }; break
      case 'Usuarios': base = { usuario: '', contrasena: '', nombreCompleto: '', correoElectronico: '', pinCaja: '', estado: 'activo' }; break
      case 'Roles': base = { nombre: '', descripcion: '' }; break
      case 'Empresas': base = { ruc: '', razonSocial: '', nombreComercial: '', direccion: '', telefono: '', correo: '' }; break
      case 'Comprobantes': base = { nombre: '', serie: '', descripcion: '' }; break
      case 'Productos': base = { categoriaId: '', marcaId: '', proveedorId: '', codigoBarras: '', nombre: '', descripcion: '', precioCompra: 0, precioVenta: 0, stockActual: 0, stockMinimo: 5 }; break
      default: base = {}
    }
    if (!item) return base
    const values = { ...base }
    Object.keys(base).forEach((field) => { if (field !== 'contrasena') values[field] = item[field] ?? '' })
    if (resource.label === 'Productos') {
      values.categoriaId = item.categoria?.id ?? ''
      values.marcaId = item.marca?.nombre?.toLowerCase() === 'sin marca' ? '' : (item.marca?.id ?? '')
      values.proveedorId = item.proveedor?.id ?? ''
    }
    return values
  }, [resource.label, item]);

  const [form, setForm] = useState(initialForm)
  const [error, setError] = useState('')
  const [relations, setRelations] = useState({ categorias: [], marcas: [], proveedores: [] })

  useEffect(() => {
    if (resource.label === 'Productos') {
      Promise.all([
        api.list('/api/categorias'),
        api.list('/api/marcas'),
        api.list('/api/proveedores')
      ]).then(([c, m, p]) => setRelations({ categorias: c, marcas: m, proveedores: p }))
    }
  }, [resource.label])

  const update = (key, value) => setForm((current) => ({ ...current, [key]: value }))
  
  async function submit(event) {
    event.preventDefault()
    setError('')
    const requiredText = ['nombre', 'codigoBarras', 'nombresRazonSocial', 'numeroDocumento', 'rucDni', 'razonSocial', 'ruc', 'direccion', 'usuario', 'nombreCompleto', 'serie']
    if (!item && 'contrasena' in form) requiredText.push('contrasena')
    const missing = requiredText.find((field) => field in form && !String(form[field]).trim())
    if (missing) return setError('Completa todos los campos obligatorios.')
    if ('contrasena' in form && form.contrasena && form.contrasena.length < 4) return setError('La contraseña debe tener al menos 4 caracteres.')
    if ('correo' in form && form.correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.correo)) return setError('Ingresa un correo válido.')
    if ('correoElectronico' in form && form.correoElectronico && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.correoElectronico)) return setError('Ingresa un correo válido.')
    const numericFields = ['precioCompra', 'precioVenta', 'stockActual', 'stockMinimo']
    if (numericFields.some((field) => field in form && (!Number.isFinite(Number(form[field])) || Number(form[field]) < 0))) return setError('Los precios y el stock deben ser números mayores o iguales a cero.')
    if (resource.label === 'Productos' && ['categoriaId', 'proveedorId'].some((field) => !Number.isInteger(Number(form[field])) || Number(form[field]) <= 0)) return setError('Selecciona categoría y proveedor.')
    try {
      const cleanForm = Object.fromEntries(Object.entries(form).map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value]))
      const payload = resource.label === 'Productos' ? {
        categoria: { id: Number(form.categoriaId) }, 
        marca: form.marcaId ? { id: Number(form.marcaId) } : null, 
        proveedor: { id: Number(form.proveedorId) }, 
        codigoBarras: form.codigoBarras, 
        nombre: form.nombre, 
        descripcion: form.descripcion, 
        precioCompra: Number(form.precioCompra), 
        precioVenta: Number(form.precioVenta), 
        stockActual: Number(form.stockActual), 
        stockMinimo: Number(form.stockMinimo) 
      } : cleanForm;
      
      if (item) await api.update(`${resource.endpoint}/${item.id}`, payload)
      else await api.create(resource.endpoint, payload)
      onSaved();
    } catch (err) { 
      setError(err.message || 'No se pudo guardar el registro.')
    } 
  }
  
  const fields = Object.keys(form)
  return <div className="modal-backdrop"><section className="modal" style={{maxHeight: '90vh', overflowY: 'auto'}}><div className="modal-head"><div><span className="eyebrow">{item ? 'Modificar registro' : 'Nuevo registro'}</span><h2>{resource.singular}</h2></div><button className="icon-button" onClick={onClose} aria-label="Cerrar"><X size={19} /></button></div><form onSubmit={submit} className="form-grid">
    {fields.map((field) => {
      const isSelect = resource.label === 'Productos' && ['categoriaId', 'marcaId', 'proveedorId'].includes(field);
      const labelText = field.replace(/[A-Z]/g, (letter) => ` ${letter}`).replace(/^./, (letter) => letter.toUpperCase());
      const isRequired = ['nombre', 'codigoBarras', 'nombresRazonSocial', 'numeroDocumento', 'rucDni', 'razonSocial', 'ruc', 'direccion', 'categoriaId', 'proveedorId', 'usuario', 'nombreCompleto', 'serie'].includes(field) || (field === 'contrasena' && !item);
      const isNumber = field.toLowerCase().includes('precio') || field.toLowerCase().includes('stock');

      if (isSelect) {
        const options = field === 'categoriaId' ? relations.categorias : field === 'marcaId' ? relations.marcas : relations.proveedores;
        const nameField = field === 'categoriaId' ? 'nombre' : field === 'marcaId' ? 'nombre' : 'razonSocial';
        return <label key={field}>{labelText}{field === 'marcaId' && <small className="form-hint">Solo necesario para bebidas; las comidas usan “Sin marca”.</small>}
          <select required={isRequired} value={form[field]} onChange={(e) => update(field, e.target.value)} style={{border: '1px solid var(--line)', padding: '14px 15px', borderRadius: '4px', background: 'var(--paper)'}}>
            <option value="">Seleccione una opción</option>
            {options.map(opt => <option key={opt.id} value={opt.id}>{opt[nameField]}</option>)}
          </select>
        </label>
      }

      if (field === 'estado') {
        return <label key={field}>Estado
          <select value={form[field]} onChange={(e) => update(field, e.target.value)} style={{border: '1px solid var(--line)', padding: '14px 15px', borderRadius: '4px', background: 'var(--paper)'}}>
            <option value="activo">Activo</option>
            <option value="inactivo">Inactivo</option>
            <option value="bloqueado">Bloqueado</option>
          </select>
        </label>
      }

      return <label key={field}>{labelText}
        <input required={isRequired} min={isNumber ? 0 : undefined} minLength={field === 'contrasena' ? 4 : undefined} maxLength={field === 'contrasena' ? 255 : undefined} type={field.toLowerCase().includes('contrase') ? 'password' : isNumber ? 'number' : field === 'correo' || field === 'correoElectronico' ? 'email' : 'text'} value={form[field]} onChange={(event) => update(field, event.target.value)} />
      </label>
    })}
    {error && <div className="form-error">{error}</div>}<div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>Cancelar</button><button className="primary-button" type="submit">Guardar <ArrowRight size={16} /></button></div></form></section></div>
}

function Sales({ session }) { return <SalesPos session={session} /> }

function SalesLegacy({ session }) { 
  const [products, setProducts] = useState([])
  const [clients, setClients] = useState([])
  const [empresas, setEmpresas] = useState([])
  const [comprobantes, setComprobantes] = useState([])
  
  const [cart, setCart] = useState([])
  const [search, setSearch] = useState('')
  const [clientId, setClientId] = useState('')
  const [comprobanteId, setComprobanteId] = useState('')
  const [metodoPago, setMetodoPago] = useState('efectivo')
  const [loading, setLoading] = useState(false)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    api.list('/api/productos').then(setProducts)
    api.list('/api/clientes').then(setClients)
    api.list('/api/empresas').then(setEmpresas)
    api.list('/api/tipos-comprobante').then(setComprobantes)
  }, [])

  const filteredProducts = useMemo(() => products.filter(p => p.nombre.toLowerCase().includes(search.toLowerCase()) || p.codigoBarras.includes(search)), [products, search])
  const subtotal = cart.reduce((acc, item) => acc + (item.precioVenta * item.cantidad), 0)
  const total = subtotal * 1.18 
  
  const addToCart = (product) => {
    if (product.stockActual <= 0) return setError('Producto sin stock');
    setError('');
    setCart(prev => {
      const exists = prev.find(i => i.id === product.id)
      if (exists) {
        if (exists.cantidad >= product.stockActual) { setError('Stock insuficiente'); return prev; }
        return prev.map(i => i.id === product.id ? {...i, cantidad: i.cantidad + 1} : i)
      }
      return [...prev, {...product, cantidad: 1}]
    })
  }

  const updateQty = (id, delta, product) => {
    setError('');
    setCart(prev => prev.map(i => {
      if (i.id === id) {
        const newQ = i.cantidad + delta;
        if (newQ > i.stockActual) { setError('No puedes agregar más del stock disponible'); return i; }
        return newQ > 0 ? {...i, cantidad: newQ} : i;
      }
      return i;
    }).filter(i => i.cantidad > 0))
  }

  const submitSale = async () => {
    if (!clientId || !comprobanteId || cart.length === 0) return setError('Complete todos los campos de la venta (cliente, comprobante, y al menos un producto)')
    if (empresas.length === 0) return setError('No hay empresas registradas para asociar a la venta. Regístrelo en Directorio.')
    
    setLoading(true)
    setError('')
    try {
      await api.create('/api/ventas', {
        empresaId: empresas[0].id,
        usuarioId: session.id, // Using the correctly authenticated session user
        clienteId: Number(clientId),
        tipoComprobanteId: Number(comprobanteId),
        numeroComprobante: 'FAC-' + Date.now().toString().slice(-6),
        metodoPago: metodoPago,
        items: cart.map(item => ({ productoId: item.id, cantidad: item.cantidad }))
      })
      setSuccess(true)
      setCart([])
      api.list('/api/productos').then(setProducts) // refresh stock
      setTimeout(() => setSuccess(false), 3000)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return <><div className="page-heading compact"><div><span className="eyebrow">Operación / POS</span><h1>Punto de Venta</h1><p className="muted">Registra ventas interactivamente.</p></div></div>
  <div style={{display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: '20px'}}>
    <section className="panel" style={{display: 'flex', flexDirection: 'column'}}>
      <div className="table-toolbar">
        <div className="search-box"><Search size={16} /><input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Buscar producto por nombre o código..." /></div>
      </div>
      <div style={{padding: '20px', display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '15px', maxHeight: '600px', overflowY: 'auto'}}>
        {filteredProducts.map(p => (
          <div key={p.id} onClick={() => addToCart(p)} style={{border: '1px solid var(--line)', padding: '15px', borderRadius: '5px', cursor: 'pointer', background: 'var(--paper)', opacity: p.stockActual <= 0 ? 0.5 : 1, transition: 'transform 0.1s'}}>
            <div style={{display: 'flex', justifyContent: 'space-between', marginBottom: '10px'}}>
              <Package size={20} color={p.stockActual > 0 ? "var(--green)" : "var(--muted)"} />
              <strong style={{color: p.stockActual > 0 ? 'var(--green)' : 'var(--muted)'}}>S/ {p.precioVenta}</strong>
            </div>
            <strong style={{display: 'block', fontSize: '13px'}}>{p.nombre}</strong>
            <small style={{color: 'var(--muted)', fontSize: '11px'}}>{p.codigoBarras}</small>
            <div style={{marginTop: '10px', fontSize: '11px', color: p.stockActual > p.stockMinimo ? 'var(--muted)' : 'var(--orange)'}}>
              Stock: {p.stockActual}
            </div>
          </div>
        ))}
      </div>
    </section>

    <section className="panel" style={{display: 'flex', flexDirection: 'column', maxHeight: 'calc(100vh - 150px)'}}>
      <div className="panel-head" style={{borderBottom: '1px solid var(--line)'}}>
        <h3>Carrito de Compras</h3>
        <ShoppingCart size={20} className="accent-icon" />
      </div>
      <div style={{flex: 1, overflowY: 'auto', padding: '15px'}}>
        {cart.length === 0 ? <EmptyState text="El carrito está vacío" /> : 
          cart.map(item => (
            <div key={item.id} style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '15px', borderBottom: '1px solid var(--line)', paddingBottom: '10px'}}>
              <div>
                <strong style={{fontSize: '13px', display: 'block'}}>{item.nombre}</strong>
                <small style={{color: 'var(--muted)'}}>S/ {item.precioVenta} c/u</small>
              </div>
              <div style={{display: 'flex', alignItems: 'center', gap: '10px'}}>
                <button type="button" onClick={() => updateQty(item.id, -1, item)} style={{padding: '5px', background: '#f0f0f0', borderRadius: '4px'}}><Minus size={14}/></button>
                <span style={{fontSize: '13px', fontWeight: 'bold'}}>{item.cantidad}</span>
                <button type="button" onClick={() => updateQty(item.id, 1, item)} style={{padding: '5px', background: '#f0f0f0', borderRadius: '4px'}}><Plus size={14}/></button>
              </div>
            </div>
          ))
        }
      </div>
      <div style={{padding: '20px', borderTop: '1px solid var(--line)', background: '#fafcf9'}}>
        <div style={{display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '20px'}}>
          <select value={clientId} onChange={e => setClientId(e.target.value)} style={{padding: '10px', borderRadius: '4px', border: '1px solid var(--line)'}}>
            <option value="">Seleccionar Cliente...</option>
            {clients.map(c => <option key={c.id} value={c.id}>{c.nombresRazonSocial}</option>)}
          </select>
          <select value={comprobanteId} onChange={e => setComprobanteId(e.target.value)} style={{padding: '10px', borderRadius: '4px', border: '1px solid var(--line)'}}>
            <option value="">Seleccionar Comprobante...</option>
            {comprobantes.map(c => <option key={c.id} value={c.id}>{c.nombreTipo} - {c.serie}</option>)}
          </select>
          <select value={metodoPago} onChange={e => setMetodoPago(e.target.value)} style={{padding: '10px', borderRadius: '4px', border: '1px solid var(--line)'}}>
            <option value="efectivo">Efectivo</option>
            <option value="tarjeta">Tarjeta</option>
            <option value="transferencia">Transferencia</option>
            <option value="yape_plin">Yape / Plin</option>
          </select>
        </div>
        
        <div style={{display: 'flex', justifyContent: 'space-between', marginBottom: '10px'}}>
          <span style={{color: 'var(--muted)', fontSize: '13px'}}>Subtotal</span>
          <strong>S/ {subtotal.toFixed(2)}</strong>
        </div>
        <div style={{display: 'flex', justifyContent: 'space-between', marginBottom: '20px', fontSize: '18px'}}>
          <strong>Total (inc. IGV)</strong>
          <strong style={{color: 'var(--green)'}}>S/ {total.toFixed(2)}</strong>
        </div>

        {error && <div className="form-error" style={{marginBottom: '15px'}}>{error}</div>}
        {success && <div style={{padding: '10px', background: '#d8ecd9', color: '#285d47', borderRadius: '4px', marginBottom: '15px', display: 'flex', alignItems: 'center', gap: '10px'}}><CheckCircle2 size={16}/> Venta registrada exitosamente!</div>}
        
        <button className="primary-button full" onClick={submitSale} disabled={loading || cart.length === 0} style={{marginTop: 0}}>
          {loading ? 'Procesando...' : `Cobrar S/ ${total.toFixed(2)}`}
        </button>
      </div>
    </section>
  </div></> 
}

export default App
