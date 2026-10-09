import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Archive, ArrowRight, BadgeDollarSign, BarChart3, Boxes, BriefcaseBusiness, ChevronRight,
  CircleUserRound, ClipboardList, Command, LayoutDashboard, LogOut, Menu, Package,
  Plus, ReceiptText, Search, Settings2, ShieldCheck, Store, Tags, Truck, UserRound,
  UsersRound, X, Zap, Minus, ShoppingCart, CheckCircle2, Pencil, Trash2, ChefHat, Globe
} from 'lucide-react'
import { api } from './api'
import SalesPos from './SalesPos'
import CashierDashboard from './pages/CashierDashboard'
import MarketingDashboard from './MarketingDashboard'
import { permissions, canView, defaultView } from './permissions'
import KitchenBoard from './components/KitchenBoard'
import BarBoard from './components/BarBoard'
import OnlineOrdersBoard from './components/OnlineOrdersBoard'

const resources = {
  productos: { label: 'Productos', singular: 'producto', endpoint: '/api/productos', icon: Package, columns: [['nombre', 'Producto'], ['codigoBarras', 'Código'], ['precioVenta', 'Precio'], ['stockActual', 'Stock']] },
  clientes: { label: 'Clientes', singular: 'cliente', endpoint: '/api/clientes', icon: UsersRound, columns: [['numeroDocumento', 'Documento'], ['nombresRazonSocial', 'Nombre'], ['telefono', 'Teléfono'], ['correo', 'Correo']] },
  proveedores: { label: 'Proveedores', singular: 'proveedor', endpoint: '/api/proveedores', icon: Truck, columns: [['rucDni', 'RUC / DNI'], ['razonSocial', 'Razón social'], ['telefono', 'Teléfono'], ['correo', 'Correo']] },
  categorias: { label: 'Categorías', singular: 'categoría', endpoint: '/api/categorias', icon: Tags, columns: [['nombre', 'Nombre'], ['descripcion', 'Descripción']] },
  marcas: { label: 'Marcas', singular: 'marca', endpoint: '/api/marcas', icon: Archive, columns: [['nombre', 'Nombre']] },
  usuarios: { label: 'Usuarios', singular: 'usuario', endpoint: '/api/usuarios', icon: UserRound, columns: [['usuario', 'Usuario'], ['nombreCompleto', 'Nombre'], ['correoElectronico', 'Correo'], ['estado', 'Estado'], ['roles', 'Roles']] },
  roles: { readOnly: true, label: 'Roles', singular: 'rol', endpoint: '/api/roles', icon: ShieldCheck, columns: [['nombre', 'Rol'], ['descripcion', 'Descripción']] },
  configuracion: { label: 'Configuración', singular: 'configuración fiscal', endpoint: '/api/configuracion', icon: BriefcaseBusiness, columns: [['ruc', 'RUC'], ['razonSocial', 'Razón social'], ['telefono', 'Teléfono']] },
  'tipos-comprobante': { label: 'Comprobantes', singular: 'tipo de comprobante', endpoint: '/api/tipos-comprobante', icon: ReceiptText, columns: [['nombre', 'Tipo'], ['serie', 'Serie']] },
  areas: { label: 'Áreas', singular: 'área', endpoint: '/api/areas', icon: Store, columns: [['nombre', 'Nombre'], ['estado', 'Estado']] },
  mesas: { label: 'Mesas', singular: 'mesa', endpoint: '/api/mesas', icon: Store, columns: [['numero', 'Mesa'], ['area', 'Área'], ['capacidad', 'Capacidad'], ['estado', 'Estado']] }
}

const navGroups = [
  { title: 'Operación', items: [['caja', 'Caja y recepción', BadgeDollarSign], ['ventas', 'Mesas y ventas', ReceiptText], ['online', 'Pedidos online', Globe], ['cocina', 'Cocina', ChefHat], ['bar', 'Bar', Store]] },
  { title: 'Gestión', reqAdmin: true, items: [['dashboard', 'Inicio', LayoutDashboard], ['marketing', 'Marketing', BarChart3], ['areas', 'Áreas', Store], ['mesas', 'Mesas', Store], ['productos', 'Productos', Package]] },
  { title: 'Directorio', reqAdmin: true, items: [['clientes', 'Clientes', UsersRound], ['proveedores', 'Proveedores', Truck], ['categorias', 'Categorías', Tags], ['marcas', 'Marcas', Archive]] },
  { title: 'Administración', reqAdmin: true, items: [['usuarios', 'Usuarios', UserRound], ['roles', 'Roles', ShieldCheck], ['configuracion', 'Configuración', Settings2], ['tipos-comprobante', 'Comprobantes', ReceiptText]] },
]

function App() {
  const [session, setSession] = useState(null)
  const [checking, setChecking] = useState(true)
  const [view, setView] = useState('ventas')
  const [mobileNav, setMobileNav] = useState(false)
  useEffect(() => {
    try { localStorage.removeItem('pos-session') } catch { /* Almacenamiento bloqueado. */ }
    api.list('/api/auth/me').then(data => { setSession(data); setView(defaultView(data)) }).catch(() => setSession(null)).finally(() => setChecking(false))
    const expired = () => setSession(null)
    window.addEventListener('pos-session-expired', expired)
    return () => window.removeEventListener('pos-session-expired', expired)
  }, [])
  if (checking) return <div className="loading">Verificando sesión…</div>
  if (!session) return <Login onLogin={(data) => { setSession(data); setView(defaultView(data)) }} />
  const safeView = canView(session, view) ? view : defaultView(session)
  return <Shell session={session} view={safeView} setView={(next) => { if (canView(session, next)) setView(next); setMobileNav(false) }} mobileNav={mobileNav} setMobileNav={setMobileNav}
    onLogout={async () => { try { await api.create('/api/auth/logout', {}); setSession(null) } catch (err) { window.alert(err.message) } }} />
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
      const res = await api.create('/api/auth/login', { usuario: name.trim(), contrasena: password })
      onLogin(res)
    } catch(e) {
      setError(e.message || 'Credenciales inválidas o error de conexión.')
    } finally {
      setLoading(false)
    }
  }
  
  return <main className="login-page">
    <section className="login-art"><div className="brand-mark"><Store size={20} /> MI TRAMPITA</div><div className="login-quote"><span>01 / POS</span><h1>MI TRAMPITA<br /><em>Trabajo con ritmo.</em></h1><p>Mesas, cocina y caja al ritmo del recreo.</p></div><div className="art-grid" /></section>
    <section className="login-panel"><div className="login-form"><div className="mobile-brand"><Store size={20} /> MI TRAMPITA</div><span className="eyebrow">Punto de venta</span><h2>Bienvenido de vuelta</h2><p className="muted">Accede a tu espacio de trabajo.</p><form onSubmit={submit}><label>Usuario<input autoFocus value={name} onChange={(event) => setName(event.target.value)} placeholder="Tu nombre de usuario" /></label><label style={{marginTop: '15px'}}>Contraseña<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Tu contraseña" /></label>{error && <div className="form-error">{error}</div>}<button className="primary-button full" type="submit" disabled={loading}>{loading ? 'Iniciando sesión...' : 'Entrar'} <ArrowRight size={18} /></button></form><p className="login-note"><Zap size={14} /> Autenticación en vivo contra la base de datos PostgreSQL.</p></div><span className="login-footer">Mi Trampita · 2026</span></section>
  </main>
}

function Shell({ session, view, setView, mobileNav, setMobileNav, onLogout }) {
  const { isAdmin } = permissions(session);

  return <div className="app-shell"><aside className={`sidebar ${mobileNav ? 'open' : ''}`}><div className="sidebar-top"><div className="brand-mark dark"><Store size={19} /> MI TRAMPITA</div><button className="icon-button close-nav" onClick={() => setMobileNav(false)} aria-label="Cerrar menú"><X size={20} /></button></div><div className="workspace"><span className="workspace-dot" /><span>Recreo Mi Trampita</span><ChevronRight size={14} /></div><nav>{navGroups.map((group) => {
    if (group.reqAdmin && !isAdmin) return null;
    return <div className="nav-group" key={group.title}><span className="nav-label">{group.title}</span>{group.items.filter(([key]) => canView(session, key)).map(([key, label, Icon]) => <button className={`nav-item ${view === key ? 'active' : ''}`} key={key} onClick={() => setView(key)}><Icon size={17} /><span>{label}</span>{key === 'ventas' && <span className="nav-badge">POS</span>}</button>)}</div>
  })}</nav><div className="sidebar-bottom">
  {isAdmin && <button className="nav-item" onClick={() => setView('configuracion')}><Settings2 size={17} /><span>Configuración</span></button>}
  <div className="user-card"><div className="avatar">{session.nombreCompleto?.charAt(0).toUpperCase() || 'U'}</div><div><strong title={session.nombreCompleto}>{session.usuario}</strong><small>{isAdmin ? 'Administrador' : session.roles?.includes('COCINERO') ? 'Cocinero' : session.roles?.includes('BARTENDER') ? 'Bartender' : session.roles?.includes('CAJA') ? 'Caja' : 'Mozo / Ventas'}</small></div><button className="icon-button" onClick={onLogout} title="Cerrar sesión"><LogOut size={16} /></button></div></div></aside>{mobileNav && <button className="scrim" onClick={() => setMobileNav(false)} aria-label="Cerrar menú" />}<main className="main-content"><header className="topbar"><button className="icon-button menu-button" onClick={() => setMobileNav(true)} aria-label="Abrir menú"><Menu size={21} /></button><div className="crumb"><Command size={16} /><span>/</span><strong>{view === 'caja' ? 'Caja y recepción' : view === 'dashboard' ? 'Inicio' : view === 'ventas' ? 'Mesas y ventas' : view === 'marketing' ? 'Marketing' : view === 'bar' ? 'Bar' : view === 'cocina' ? 'Cocina' : view === 'online' ? 'Pedidos online' : resources[view]?.label}</strong></div><div className="top-actions"><span className="connection"><span /> API conectada</span><button className="icon-button" title="Perfil" onClick={() => { if(isAdmin) setView('usuarios') }}><CircleUserRound size={19} /></button></div></header><div className="content">{view === 'caja' ? <CashierDashboard session={session} /> : view === 'dashboard' ? <Dashboard setView={setView} /> : view === 'ventas' ? <Sales session={session} /> : view === 'bar' ? <BarBoard /> : view === 'cocina' ? <KitchenBoard /> : view === 'online' ? <OnlineOrdersBoard session={session} /> : view === 'marketing' && isAdmin ? <MarketingDashboard /> : isAdmin && resources[view] ? <ResourceView key={view} resource={resources[view]} /> : null}</div></main></div>
}

function Dashboard({ setView }) { return <><DashboardLegacy setView={setView} /><RecentSales /></> }

function RecentSales() {
  const [sales, setSales] = useState([])
  const [error, setError] = useState('')
  useEffect(() => { api.list('/api/ventas').then(setSales).catch((err) => setError(err.message)) }, [])
  const recent = sales.filter(sale => sale.estado === 'CERRADA').sort((a, b) => new Date(b.fechaVenta || 0) - new Date(a.fechaVenta || 0)).slice(0, 5)
  return <section className="panel recent-panel"><div className="panel-head"><div><span className="eyebrow">Actividad</span><h3>Últimas ventas</h3></div><ReceiptText size={20} className="accent-icon" /></div>{error ? <div className="api-error">No se pudo cargar la actividad.</div> : recent.length ? <div className="recent-sales">{recent.map((sale) => <div className="recent-sale" key={sale.id}><span className="recent-sale-icon"><ReceiptText size={15} /></span><div><strong>{sale.tipoComprobante?.nombre || 'Comprobante'} · {sale.comprobante?.numero || sale.numeroComprobante}</strong><small>{sale.cliente?.nombresRazonSocial || 'Cliente'} · {sale.fechaVenta ? new Date(sale.fechaVenta).toLocaleString('es-PE') : 'Fecha pendiente'}</small></div><b>S/ {Number(sale.total || 0).toFixed(2)}</b></div>)}</div> : <EmptyState text="Todavía no hay ventas registradas." />}</section>
}

function DashboardLegacy({ setView }) {
  const [products, setProducts] = useState([])
  const [sales, setSales] = useState([])
  useEffect(() => { api.list('/api/productos').then(setProducts).catch(() => setProducts([])); api.list('/api/ventas').then(setSales).catch(() => setSales([])) }, [])
  const lowStock = products.filter((item) => item.stockActual <= item.stockMinimo).length
  return <><div className="page-heading"><div><span className="eyebrow">Hoy</span><h1>Buen día, <em>equipo.</em></h1><p className="muted">Una mirada rápida a tu operación.</p></div><button className="primary-button" onClick={() => setView('ventas')}><Plus size={17} /> Nueva venta</button></div><div className="metric-grid"><Metric label="Ventas registradas" value={sales.filter(sale => sale.estado === 'CERRADA').length} detail="Desde el backend" icon={BadgeDollarSign} tone="green" /><Metric label="Productos" value={products.length} detail="Catálogo activo" icon={Package} tone="yellow" /><Metric label="Stock por revisar" value={lowStock} detail="Bajo mínimo" icon={Boxes} tone="orange" /><Metric label="Operación" value="Activa" detail="localhost:9090" icon={BarChart3} tone="blue" /></div><div className="dashboard-grid"><section className="panel spotlight"><div className="panel-head"><div><span className="eyebrow">Acciones rápidas</span><h3>¿Qué quieres hacer?</h3></div><Zap size={20} className="accent-icon" /></div><div className="quick-grid"><QuickAction icon={ReceiptText} title="Registrar venta" detail="Cobrar y actualizar stock" onClick={() => setView('ventas')} /><QuickAction icon={Package} title="Nuevo producto" detail="Añadir al catálogo" onClick={() => setView('productos')} /><QuickAction icon={UsersRound} title="Nuevo cliente" detail="Crear ficha de cliente" onClick={() => setView('clientes')} /><QuickAction icon={Truck} title="Ver proveedores" detail="Consultar directorio" onClick={() => setView('proveedores')} /></div></section><section className="panel stock-panel"><div className="panel-head"><div><span className="eyebrow">Inventario</span><h3>Stock bajo</h3></div><button className="text-button" onClick={() => setView('productos')}>Ver todo <ArrowRight size={14} /></button></div>{products.filter((item) => item.stockActual <= item.stockMinimo).slice(0, 4).map((item) => <div className="stock-row" key={item.id}><div className="product-symbol"><Package size={15} /></div><div><strong>{item.nombre}</strong><small>{item.codigoBarras}</small></div><span className="stock-value">{item.stockActual} <small>/ {item.stockMinimo}</small></span></div>)}{lowStock === 0 && <EmptyState text="Todo el inventario está en orden." />}</section></div></>
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
  const load = () => { setLoading(true); setError(''); api.list(resource.endpoint).then(setItems).catch((err) => setError(err.message)).finally(() => setLoading(false)) }
  useEffect(load, [resource.endpoint])
  const filtered = useMemo(() => items.filter((item) => JSON.stringify(item).toLowerCase().includes(query.toLowerCase())), [items, query])
  const openNew = () => { setEditing(null); setShowForm(true) }
  const openEdit = (item) => { setEditing(item); setShowForm(true) }
  return <><div className="page-heading compact"><div><span className="eyebrow">Directorio / {resource.label}</span><h1>{resource.label}</h1><p className="muted">Gestiona la información conectada a tu base de datos.</p></div>{!resource.readOnly && <button className="primary-button" onClick={openNew}><Plus size={17} /> Nuevo {resource.singular}</button>}</div><section className="panel table-panel"><div className="table-toolbar"><div className="search-box"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={`Buscar ${resource.label.toLowerCase()}...`} /></div><span className="result-count">{filtered.length} registros</span></div>{error && <div className="api-error">{error}</div>}{loading ? <div className="loading">Cargando datos...</div> : <DataTable resource={resource} items={filtered} onRefresh={load} onEdit={openEdit} onError={setError} />}</section>{showForm && <ResourceForm key={`${resource.endpoint}-${editing?.id || 'new'}`} resource={resource} item={editing} onClose={() => setShowForm(false)} onSaved={() => { setShowForm(false); setEditing(null); load() }} />}</>
}

function DataTable({ resource, items, onRefresh, onEdit, onError }) { return items.length ? <div className="table-wrap"><table><thead><tr>{resource.columns.map(([, label]) => <th key={label}>{label}</th>)}{!resource.readOnly && <th>Acciones</th>}</tr></thead><tbody>{items.map((item) => <tr key={item.id}><>{resource.columns.map(([key]) => <td key={key}>{key.includes('precio') ? 'S/ ' + Number(item[key] || 0).toFixed(2) : (Array.isArray(item[key]) ? item[key].join(', ') : typeof item[key] === 'object' && item[key] !== null ? item[key].nombre || item[key].razonSocial || item[key].nombreCategoria || item[key].nombreMarca || 'Objeto' : item[key] ?? '—')}</td>)}</>{!resource.readOnly && <td className="row-actions"><button className="row-action" onClick={() => onEdit(item)} title={`Modificar ${resource.singular}`}><Pencil size={15} /></button><button className="row-action danger" onClick={() => { if (confirm(`¿Eliminar este ${resource.singular}? Esta acción no se puede deshacer.`)) api.remove(`${resource.endpoint}/${item.id}`).then(onRefresh).catch((err) => onError(err.message || 'No se pudo eliminar el registro.')) }} title={`Eliminar ${resource.singular}`}><Trash2 size={15} /></button></td>}</tr>)}</tbody></table></div> : <EmptyState text="No hay registros para mostrar." /> }
function EmptyState({ text }) { return <div className="empty"><ClipboardList size={22} /><span>{text}</span></div> }

function ResourceForm({ resource, item, onClose, onSaved }) {
  const initialForm = useMemo(() => {
    let base
    switch (resource.label) {
      case 'Categorías': base = { nombre: '', descripcion: '' }; break
      case 'Marcas': base = { nombre: '' }; break
      case 'Clientes': base = { numeroDocumento: '', nombresRazonSocial: '', direccion: '', telefono: '', correo: '', fechaNacimiento: '' }; break
      case 'Proveedores': base = { rucDni: '', razonSocial: '', telefono: '', correo: '' }; break
      case 'Usuarios': base = { usuario: '', contrasena: '', nombreCompleto: '', correoElectronico: '', estado: 'activo', rolIds: [] }; break
      case 'Roles': base = { nombre: '', descripcion: '' }; break
      case 'Configuración': base = { ruc: '', razonSocial: '', nombreComercial: '', direccion: '', telefono: '', correo: '' }; break
      case 'Comprobantes': base = { nombre: '', serie: '', descripcion: '' }; break
      case 'Áreas': base = { nombre: '', estado: 'ACTIVA' }; break
      case 'Mesas': base = { numero: '', capacidad: 4, areaId: '' }; break
      case 'Productos': base = { categoriaId: '', marcaId: '', proveedorId: '', codigoBarras: '', nombre: '', descripcion: '', precioCompra: 0, precioVenta: 0, stockActual: 0, stockMinimo: 5, areaDestino: 'COCINA', visibleWeb: false }; break
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
    if (resource.label === 'Mesas') values.areaId = item.area?.id || ''
    return values
  }, [resource.label, item]);

  const [form, setForm] = useState(initialForm)
  const [saving, setSaving] = useState(false)
  const submitting = useRef(false)
  const [error, setError] = useState('')
  const [relations, setRelations] = useState({ categorias: [], marcas: [], proveedores: [], areas: [], roles: [] })

  useEffect(() => {
    if (resource.label === 'Productos') {
      Promise.all([
        api.list('/api/categorias'),
        api.list('/api/marcas'),
        api.list('/api/proveedores')
      ]).then(([c, m, p]) => setRelations(current => ({ ...current, categorias: c, marcas: m, proveedores: p }))).catch(err => setError(err.message))
    }
    if (resource.label === 'Mesas') api.list('/api/areas').then(areas => setRelations(current => ({ ...current, areas }))).catch(err => setError(err.message))
    if (resource.label === 'Usuarios') api.list('/api/roles').then(roles => setRelations(current => ({ ...current, roles: roles.filter(role => ['ADMIN', 'MOZO', 'CAJA', 'COCINERO', 'BARTENDER'].includes(role.nombre)) }))).catch(err => setError(err.message))
  }, [resource.label])

  const update = (key, value) => setForm((current) => ({ ...current, [key]: value }))
  
  async function submit(event) {
    event.preventDefault()
    if (submitting.current) return
    setError('')
    const requiredText = ['nombre', 'codigoBarras', 'nombresRazonSocial', 'numeroDocumento', 'rucDni', 'razonSocial', 'ruc', 'usuario', 'nombreCompleto', 'serie']
    if (resource.label === 'Configuración') requiredText.push('direccion')
    if (!item && 'contrasena' in form) requiredText.push('contrasena')
    const missing = requiredText.find((field) => field in form && !String(form[field]).trim())
    if (missing) return setError('Completa todos los campos obligatorios.')
    if ('contrasena' in form && form.contrasena && form.contrasena.length < 8) return setError('La contraseña debe tener al menos 8 caracteres.')
    if ('correo' in form && form.correo && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.correo)) return setError('Ingresa un correo válido.')
    if ('correoElectronico' in form && form.correoElectronico && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.correoElectronico)) return setError('Ingresa un correo válido.')
    if (resource.label === 'Usuarios' && !form.rolIds.length) return setError('Selecciona al menos un rol.')
    if (resource.label === 'Mesas' && !form.areaId) return setError('Selecciona el área de la mesa.')
    const numericFields = ['precioCompra', 'precioVenta', 'stockActual', 'stockMinimo', 'numero', 'capacidad']
    if (numericFields.some((field) => field in form && (!Number.isFinite(Number(form[field])) || Number(form[field]) < 0))) return setError('Los precios y el stock deben ser números mayores o iguales a cero.')
    if (resource.label === 'Productos' && ['categoriaId', 'proveedorId'].some((field) => !Number.isInteger(Number(form[field])) || Number(form[field]) <= 0)) return setError('Selecciona categoría y proveedor.')
    submitting.current = true; setSaving(true)
    try {
      const cleanForm = Object.fromEntries(Object.entries(form).map(([key, value]) => [key, typeof value === 'string' && key !== 'contrasena' ? value.trim() : value]))
      const payload = resource.label === 'Productos' ? {
        ...(item ? { version: item.version } : {}),
        categoria: { id: Number(form.categoriaId) }, 
        marca: form.marcaId ? { id: Number(form.marcaId) } : null, 
        proveedor: { id: Number(form.proveedorId) }, 
        codigoBarras: form.codigoBarras, 
        nombre: form.nombre, 
        descripcion: form.descripcion, 
        precioCompra: Number(form.precioCompra), 
        precioVenta: Number(form.precioVenta), 
        stockActual: Number(form.stockActual), 
        stockMinimo: Number(form.stockMinimo), areaDestino: form.areaDestino, visibleWeb: Boolean(form.visibleWeb)
      } : resource.label === 'Mesas' ? {
        numero: Number(form.numero),
        capacidad: Number(form.capacidad),
        areaId: Number(form.areaId)
      } : resource.label === 'Clientes' ? { ...cleanForm, fechaNacimiento: form.fechaNacimiento || null } : cleanForm;
      
      if (item) await api.update(`${resource.endpoint}/${item.id}`, payload)
      else await api.create(resource.endpoint, payload)
      onSaved();
    } catch (err) { 
      setError(err.message || 'No se pudo guardar el registro.')
    } finally { submitting.current = false; setSaving(false) }
  }
  
  const fields = Object.keys(form)
  return <div className="modal-backdrop"><section className="modal" style={{maxHeight: '90vh', overflowY: 'auto'}}><div className="modal-head"><div><span className="eyebrow">{item ? 'Modificar registro' : 'Nuevo registro'}</span><h2>{resource.singular}</h2></div><button className="icon-button" onClick={onClose} aria-label="Cerrar"><X size={19} /></button></div><form onSubmit={submit} className="form-grid">
    {fields.map((field) => {
      const isSelect = (resource.label === 'Productos' && ['categoriaId', 'marcaId', 'proveedorId'].includes(field)) || field === 'areaId';
      if (field === 'rolIds') return <fieldset key={field} className="roles-field"><legend>Permisos del usuario</legend>{relations.roles.map(role => <label key={role.id}><input type="checkbox" checked={form.rolIds.includes(role.id)} onChange={event => update('rolIds', event.target.checked ? ['COCINERO', 'BARTENDER'].includes(role.nombre) ? [role.id] : [...form.rolIds.filter(id => !['COCINERO', 'BARTENDER'].includes(relations.roles.find(r => r.id === id)?.nombre)), role.id] : form.rolIds.filter(id => id !== role.id))} />{role.nombre}</label>)}</fieldset>
      if (field === 'visibleWeb') return <label key={field} style={{display:'flex',alignItems:'center',gap:10}}><input type="checkbox" style={{width:18}} checked={Boolean(form.visibleWeb)} onChange={e => update(field, e.target.checked)} />Mostrar en el menú público</label>
      if (field === 'areaDestino') return <label key={field}>Estación de preparación<select value={form[field]} onChange={e => update(field, e.target.value)}><option value="COCINA">Cocina</option><option value="BAR">Bar</option></select></label>
      const labelText = field.replace(/[A-Z]/g, (letter) => ` ${letter}`).replace(/^./, (letter) => letter.toUpperCase());
      const isRequired = (field === 'direccion' && resource.label === 'Configuración') || ['areaId','nombre', 'codigoBarras', 'nombresRazonSocial', 'numeroDocumento', 'rucDni', 'razonSocial', 'ruc', 'categoriaId', 'proveedorId', 'usuario', 'nombreCompleto', 'serie', 'numero', 'capacidad'].includes(field) || (field === 'contrasena' && !item);
      const isNumber = field.toLowerCase().includes('precio') || field.toLowerCase().includes('stock') || ['numero', 'capacidad'].includes(field);

      if (isSelect) {
        const options = field === 'areaId' ? relations.areas.filter(area => area.estado === 'ACTIVA') : field === 'categoriaId' ? relations.categorias : field === 'marcaId' ? relations.marcas : relations.proveedores;
        const nameField = field === 'areaId' ? 'nombre' : field === 'categoriaId' ? 'nombre' : field === 'marcaId' ? 'nombre' : 'razonSocial';
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
            {resource.label === 'Áreas' ? <><option value="ACTIVA">Activa</option><option value="INACTIVA">Inactiva</option></> : <><option value="activo">Activo</option><option value="inactivo">Inactivo</option><option value="bloqueado">Bloqueado</option></>}
          </select>
        </label>
      }

      return <label key={field}>{labelText}
        <input required={isRequired} disabled={saving} step={field.toLowerCase().includes('precio') ? '0.01' : isNumber ? '1' : undefined} min={['numero', 'capacidad'].includes(field) ? 1 : isNumber ? 0 : undefined} minLength={field === 'contrasena' ? 8 : undefined} maxLength={field === 'contrasena' ? 72 : undefined} type={field === 'fechaNacimiento' ? 'date' : field.toLowerCase().includes('contrase') ? 'password' : isNumber ? 'number' : field === 'correo' || field === 'correoElectronico' ? 'email' : 'text'} value={form[field]} onChange={(event) => update(field, event.target.value)} />
      </label>
    })}
    {error && <div className="form-error">{error}</div>}<div className="modal-actions"><button type="button" className="secondary-button" disabled={saving} onClick={onClose}>Cancelar</button><button className="primary-button" type="submit" disabled={saving}>{saving ? 'Guardando…' : 'Guardar'} <ArrowRight size={16} /></button></div></form></section></div>
}

function Sales({ session }) { return <SalesPos session={session} /> }

export default App
