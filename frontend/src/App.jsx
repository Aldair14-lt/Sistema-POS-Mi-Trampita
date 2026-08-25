import { useEffect, useMemo, useState } from 'react'
import {
  Archive, ArrowRight, BadgeDollarSign, BarChart3, Boxes, BriefcaseBusiness, ChevronRight,
  CircleUserRound, ClipboardList, Command, LayoutDashboard, LogOut, Menu, Package,
  Plus, ReceiptText, Search, Settings2, ShieldCheck, Store, Tags, Truck, UserRound,
  UsersRound, X, Zap,
} from 'lucide-react'
import { api } from './api'

const resources = {
  productos: { label: 'Productos', singular: 'producto', endpoint: '/api/productos', icon: Package, columns: [['nombre', 'Producto'], ['codigoBarras', 'Código'], ['precioVenta', 'Precio'], ['stockActual', 'Stock']] },
  clientes: { label: 'Clientes', singular: 'cliente', endpoint: '/api/clientes', icon: UsersRound, columns: [['numeroDocumento', 'Documento'], ['nombresRazonSocial', 'Nombre'], ['telefono', 'Teléfono'], ['correo', 'Correo']] },
  proveedores: { label: 'Proveedores', singular: 'proveedor', endpoint: '/api/proveedores', icon: Truck, columns: [['rucDni', 'RUC / DNI'], ['razonSocial', 'Razón social'], ['telefono', 'Teléfono'], ['correo', 'Correo']] },
  categorias: { label: 'Categorías', singular: 'categoría', endpoint: '/api/categorias', icon: Tags, columns: [['nombre', 'Nombre'], ['descripcion', 'Descripción']] },
  marcas: { label: 'Marcas', singular: 'marca', endpoint: '/api/marcas', icon: Archive, columns: [['nombre', 'Nombre']] },
}

const navGroups = [
  { title: 'Operación', items: [['dashboard', 'Inicio', LayoutDashboard], ['ventas', 'Ventas', ReceiptText], ['productos', 'Productos', Package]] },
  { title: 'Directorio', items: [['clientes', 'Clientes', UsersRound], ['proveedores', 'Proveedores', Truck], ['categorias', 'Categorías', Tags], ['marcas', 'Marcas', Archive]] },
  { title: 'Administración', items: [['usuarios', 'Usuarios', UserRound], ['roles', 'Roles', ShieldCheck]] },
]

function App() {
  const [session, setSession] = useState(() => localStorage.getItem('pos-session'))
  const [view, setView] = useState('dashboard')
  const [mobileNav, setMobileNav] = useState(false)
  if (!session) return <Login onLogin={(name) => { localStorage.setItem('pos-session', name); setSession(name) }} />
  return <Shell session={session} view={view} setView={(next) => { setView(next); setMobileNav(false) }} mobileNav={mobileNav} setMobileNav={setMobileNav} onLogout={() => { localStorage.removeItem('pos-session'); setSession(null) }} />
}

function Login({ onLogin }) {
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  function submit(event) {
    event.preventDefault()
    if (!name.trim()) return setError('Ingresa tu usuario para continuar.')
    onLogin(name.trim())
  }
  return <main className="login-page">
    <section className="login-art"><div className="brand-mark"><Store size={20} /> MI TRAMPITA</div><div className="login-quote"><span>01 / POS</span><h1>Vende con<br /><em>ritmo.</em></h1><p>Todo lo que necesitas para que tu tienda avance.</p></div><div className="art-grid" /></section>
    <section className="login-panel"><div className="login-form"><div className="mobile-brand"><Store size={20} /> MI TRAMPITA</div><span className="eyebrow">Punto de venta</span><h2>Bienvenido de vuelta</h2><p className="muted">Accede a tu espacio de trabajo.</p><form onSubmit={submit}><label>Usuario<input autoFocus value={name} onChange={(event) => setName(event.target.value)} placeholder="Tu nombre de usuario" /></label>{error && <div className="form-error">{error}</div>}<button className="primary-button full" type="submit">Entrar <ArrowRight size={18} /></button></form><p className="login-note"><Zap size={14} /> Sesión local de interfaz. El backend aún no expone autenticación.</p></div><span className="login-footer">Mi Trampita · 2026</span></section>
  </main>
}

function Shell({ session, view, setView, mobileNav, setMobileNav, onLogout }) {
  return <div className="app-shell"><aside className={`sidebar ${mobileNav ? 'open' : ''}`}><div className="sidebar-top"><div className="brand-mark dark"><Store size={19} /> MI TRAMPITA</div><button className="icon-button close-nav" onClick={() => setMobileNav(false)} aria-label="Cerrar menú"><X size={20} /></button></div><div className="workspace"><span className="workspace-dot" /><span>Mi tienda</span><ChevronRight size={14} /></div><nav>{navGroups.map((group) => <div className="nav-group" key={group.title}><span className="nav-label">{group.title}</span>{group.items.map(([key, label, Icon]) => <button className={`nav-item ${view === key ? 'active' : ''}`} key={key} onClick={() => setView(key)}><Icon size={17} /><span>{label}</span>{key === 'ventas' && <span className="nav-badge">POS</span>}</button>)}</div>)}</nav><div className="sidebar-bottom"><button className="nav-item"><Settings2 size={17} /><span>Configuración</span></button><div className="user-card"><div className="avatar">{session.charAt(0).toUpperCase()}</div><div><strong>{session}</strong><small>Sesión local</small></div><button className="icon-button" onClick={onLogout} title="Cerrar sesión"><LogOut size={16} /></button></div></div></aside>{mobileNav && <button className="scrim" onClick={() => setMobileNav(false)} aria-label="Cerrar menú" />}<main className="main-content"><header className="topbar"><button className="icon-button menu-button" onClick={() => setMobileNav(true)} aria-label="Abrir menú"><Menu size={21} /></button><div className="crumb"><Command size={16} /><span>/</span><strong>{view === 'dashboard' ? 'Inicio' : view === 'ventas' ? 'Ventas' : resources[view]?.label || (view === 'usuarios' ? 'Usuarios' : 'Roles')}</strong></div><div className="top-actions"><span className="connection"><span /> API local</span><button className="icon-button" title="Perfil"><CircleUserRound size={19} /></button></div></header><div className="content">{view === 'dashboard' ? <Dashboard setView={setView} /> : view === 'ventas' ? <Sales /> : view === 'usuarios' || view === 'roles' ? <Unavailable title={view === 'usuarios' ? 'Usuarios' : 'Roles'} /> : <ResourceView resource={resources[view]} />}</div></main></div>
}

function Dashboard({ setView }) {
  const [products, setProducts] = useState([])
  const [sales, setSales] = useState([])
  useEffect(() => { api.list('/api/productos').then(setProducts).catch(() => setProducts([])); api.list('/api/ventas').then(setSales).catch(() => setSales([])) }, [])
  const lowStock = products.filter((item) => item.stockActual <= item.stockMinimo).length
  return <><div className="page-heading"><div><span className="eyebrow">Lunes, 24 de agosto de 2026</span><h1>Buen día, <em>equipo.</em></h1><p className="muted">Una mirada rápida a tu operación.</p></div><button className="primary-button" onClick={() => setView('ventas')}><Plus size={17} /> Nueva venta</button></div><div className="metric-grid"><Metric label="Ventas registradas" value={sales.length} detail="Desde el backend" icon={BadgeDollarSign} tone="green" /><Metric label="Productos" value={products.length} detail="Catálogo activo" icon={Package} tone="yellow" /><Metric label="Stock por revisar" value={lowStock} detail="Bajo mínimo" icon={Boxes} tone="orange" /><Metric label="Operación" value="API" detail="localhost:8080" icon={BarChart3} tone="blue" /></div><div className="dashboard-grid"><section className="panel spotlight"><div className="panel-head"><div><span className="eyebrow">Acciones rápidas</span><h3>¿Qué quieres hacer?</h3></div><Zap size={20} className="accent-icon" /></div><div className="quick-grid"><QuickAction icon={ReceiptText} title="Registrar venta" detail="Cobrar y actualizar stock" onClick={() => setView('ventas')} /><QuickAction icon={Package} title="Nuevo producto" detail="Añadir al catálogo" onClick={() => setView('productos')} /><QuickAction icon={UsersRound} title="Nuevo cliente" detail="Crear ficha de cliente" onClick={() => setView('clientes')} /><QuickAction icon={Truck} title="Ver proveedores" detail="Consultar directorio" onClick={() => setView('proveedores')} /></div></section><section className="panel stock-panel"><div className="panel-head"><div><span className="eyebrow">Inventario</span><h3>Stock bajo</h3></div><button className="text-button" onClick={() => setView('productos')}>Ver todo <ArrowRight size={14} /></button></div>{products.filter((item) => item.stockActual <= item.stockMinimo).slice(0, 4).map((item) => <div className="stock-row" key={item.id}><div className="product-symbol"><Package size={15} /></div><div><strong>{item.nombre}</strong><small>{item.codigoBarras}</small></div><span className="stock-value">{item.stockActual} <small>/ {item.stockMinimo}</small></span></div>)}{lowStock === 0 && <EmptyState text="Todo el inventario está en orden." />}</section></div></>
}

function Metric({ label, value, detail, icon: Icon, tone }) { return <div className="metric"><div className={`metric-icon ${tone}`}><Icon size={19} /></div><div><span>{label}</span><strong>{value}</strong><small>{detail}</small></div></div> }
function QuickAction({ icon: Icon, title, detail, onClick }) { return <button className="quick-action" onClick={onClick}><span className="quick-icon"><Icon size={18} /></span><span><strong>{title}</strong><small>{detail}</small></span><ArrowRight size={16} /></button> }

function ResourceView({ resource }) {
  const [items, setItems] = useState([])
  const [query, setQuery] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = () => { setLoading(true); api.list(resource.endpoint).then(setItems).catch((err) => setError(err.message)).finally(() => setLoading(false)) }
  useEffect(load, [resource.endpoint])
  const filtered = useMemo(() => items.filter((item) => JSON.stringify(item).toLowerCase().includes(query.toLowerCase())), [items, query])
  return <><div className="page-heading compact"><div><span className="eyebrow">Directorio / {resource.label}</span><h1>{resource.label}</h1><p className="muted">Gestiona la información conectada a tu base de datos.</p></div><button className="primary-button" onClick={() => setShowForm(true)}><Plus size={17} /> Nuevo {resource.singular}</button></div><section className="panel table-panel"><div className="table-toolbar"><div className="search-box"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={`Buscar ${resource.label.toLowerCase()}...`} /></div><span className="result-count">{filtered.length} registros</span></div>{error && <div className="api-error">No se pudo conectar con el backend: {error}</div>}{loading ? <div className="loading">Cargando datos...</div> : <DataTable resource={resource} items={filtered} onRefresh={load} />}</section>{showForm && <ResourceForm resource={resource} onClose={() => setShowForm(false)} onSaved={() => { setShowForm(false); load() }} />}</>
}

function DataTable({ resource, items, onRefresh }) { return items.length ? <div className="table-wrap"><table><thead><tr>{resource.columns.map(([, label]) => <th key={label}>{label}</th>)}<th /></tr></thead><tbody>{items.map((item) => <tr key={item.id}><>{resource.columns.map(([key]) => <td key={key}>{key.includes('precio') ? `S/ ${Number(item[key] || 0).toFixed(2)}` : item[key] ?? '—'}</td>)}</><td><button className="row-action" onClick={() => { if (confirm('¿Eliminar este registro?')) api.remove(`${resource.endpoint}/${item.id}`).then(onRefresh).catch(() => {}) }} title="Eliminar"><X size={15} /></button></td></tr>)}</tbody></table></div> : <EmptyState text="No hay registros para mostrar." /> }
function EmptyState({ text }) { return <div className="empty"><ClipboardList size={22} /><span>{text}</span></div> }

function ResourceForm({ resource, onClose, onSaved }) {
  const [form, setForm] = useState(resource.label === 'Categorías' ? { nombre: '', descripcion: '' } : resource.label === 'Marcas' ? { nombre: '' } : resource.label === 'Clientes' ? { numeroDocumento: '', nombresRazonSocial: '', direccion: '', telefono: '', correo: '' } : resource.label === 'Proveedores' ? { rucDni: '', razonSocial: '', telefono: '', correo: '' } : { categoriaId: '', marcaId: '', proveedorId: '', codigoBarras: '', nombre: '', descripcion: '', precioCompra: 0, precioVenta: 0, stockActual: 0, stockMinimo: 5 })
  const [error, setError] = useState('')
  const update = (key, value) => setForm((current) => ({ ...current, [key]: value }))
  async function submit(event) { event.preventDefault(); try { const payload = resource.label === 'Productos' ? { categoria: { id: Number(form.categoriaId) }, marca: { id: Number(form.marcaId) }, proveedor: { id: Number(form.proveedorId) }, codigoBarras: form.codigoBarras, nombre: form.nombre, descripcion: form.descripcion, precioCompra: Number(form.precioCompra), precioVenta: Number(form.precioVenta), stockActual: Number(form.stockActual), stockMinimo: Number(form.stockMinimo) } : form; await api.create(resource.endpoint, payload); onSaved() } catch (err) { setError(err.message) } }
  const fields = Object.keys(form)
  return <div className="modal-backdrop"><section className="modal"><div className="modal-head"><div><span className="eyebrow">Nuevo registro</span><h2>{resource.singular}</h2></div><button className="icon-button" onClick={onClose} aria-label="Cerrar"><X size={19} /></button></div><form onSubmit={submit} className="form-grid">{fields.map((field) => <label key={field}>{field.replace(/[A-Z]/g, (letter) => ` ${letter}`).replace(/^./, (letter) => letter.toUpperCase())}<input required={['nombre', 'codigoBarras', 'nombresRazonSocial', 'numeroDocumento', 'rucDni', 'razonSocial', 'categoriaId', 'marcaId', 'proveedorId'].includes(field)} type={field.toLowerCase().includes('id') || field.toLowerCase().includes('precio') || field.toLowerCase().includes('stock') ? 'number' : field === 'correo' ? 'email' : 'text'} value={form[field]} onChange={(event) => update(field, event.target.value)} /></label>)}{resource.label === 'Productos' && <div className="form-hint">Las relaciones de categoría, marca y proveedor usan los IDs existentes en PostgreSQL. El frontend no asigna valores automáticos.</div>}{error && <div className="form-error">{error}</div>}<div className="modal-actions"><button type="button" className="secondary-button" onClick={onClose}>Cancelar</button><button className="primary-button" type="submit">Guardar <ArrowRight size={16} /></button></div></form></section></div>
}

function Sales() { return <><div className="page-heading compact"><div><span className="eyebrow">Operación / POS</span><h1>Ventas</h1><p className="muted">Registra ventas y actualiza el stock en una sola operación.</p></div></div><section className="panel sales-placeholder"><div className="sales-icon"><ReceiptText size={25} /></div><h2>Lista para cobrar</h2><p>El backend requiere empresa, usuario, cliente, tipo de comprobante y productos para registrar una venta.</p><div className="sales-fields"><span>POST /api/ventas</span><span>IGV 18%</span><span>Stock transaccional</span></div><p className="form-hint">Completa primero los catálogos y usa el contrato real del endpoint para enviar una venta.</p></section></> }
function Unavailable({ title }) { return <><div className="page-heading compact"><div><span className="eyebrow">Administración</span><h1>{title}</h1><p className="muted">Módulo preparado para conectarse cuando exista su endpoint.</p></div></div><section className="panel unavailable"><ShieldCheck size={28} /><h2>Endpoint pendiente</h2><p>El backend contiene el modelo de {title.toLowerCase()}, pero actualmente no publica un controlador REST para consultarlo o administrarlo. No se realizan llamadas inventadas.</p><code>/api/{title.toLowerCase()}</code></section></> }

export default App
