import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Archive, ArrowRight, BadgeDollarSign, BarChart3, Boxes, BriefcaseBusiness, ChevronRight,
  CircleUserRound, ClipboardList, Command, LayoutDashboard, LogOut, Menu, Package,
  Plus, ReceiptText, Search, Settings2, ShieldCheck, Store, Tags, Truck, UserRound,
  UsersRound, X, Zap, ChefHat, Globe
} from 'lucide-react'
import { api } from './api'
import SalesPos from './SalesPos'
import CashierDashboard from './pages/CashierDashboard'
import MarketingDashboard from './MarketingDashboard'
import { permissions, canView, defaultView } from './permissions'
import KitchenBoard from './components/KitchenBoard'
import BarBoard from './components/BarBoard'
import OnlineOrdersBoard from './components/OnlineOrdersBoard'
import Login from './pages/Login'
import ResourceForm from './components/ResourceForm'
import ResourceTable from './components/ResourceTable'

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
  const requestVersion = useRef(0)
  const load = useCallback(() => {
    const version = ++requestVersion.current
    setLoading(true); setError('')
    api.list(resource.endpoint)
      .then(data => { if (version === requestVersion.current) setItems(data) })
      .catch(err => { if (version === requestVersion.current) setError(err.message) })
      .finally(() => { if (version === requestVersion.current) setLoading(false) })
  }, [resource.endpoint])
  useEffect(() => { load(); return () => { requestVersion.current++ } }, [load])
  const filtered = useMemo(() => items.filter((item) => JSON.stringify(item).toLowerCase().includes(query.toLowerCase())), [items, query])
  const openNew = () => { setEditing(null); setShowForm(true) }
  const openEdit = (item) => { setEditing(item); setShowForm(true) }
  return <><div className="page-heading compact"><div><span className="eyebrow">Directorio / {resource.label}</span><h1>{resource.label}</h1><p className="muted">Gestiona la información conectada a tu base de datos.</p></div>{!resource.readOnly && <button className="primary-button" onClick={openNew}><Plus size={17} /> Nuevo {resource.singular}</button>}</div><section className="panel table-panel"><div className="table-toolbar"><div className="search-box"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={`Buscar ${resource.label.toLowerCase()}...`} /></div><span className="result-count">{filtered.length} registros</span></div>{error && <div className="api-error">{error}</div>}{loading ? <div className="loading">Cargando datos...</div> : <ResourceTable resource={resource} items={filtered} onRefresh={load} onEdit={openEdit} onError={setError} />}</section>{showForm && <ResourceForm key={`${resource.endpoint}-${editing?.id || 'new'}`} resource={resource} item={editing} onClose={() => setShowForm(false)} onSaved={() => { setShowForm(false); setEditing(null); load() }} />}</>
}

function EmptyState({ text }) { return <div className="empty"><ClipboardList size={22} /><span>{text}</span></div> }


function Sales({ session }) { return <SalesPos session={session} /> }

export default App
