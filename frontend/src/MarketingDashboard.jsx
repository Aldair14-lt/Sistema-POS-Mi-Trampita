import { useEffect, useState } from 'react'
import { BarChart3, Cake, RefreshCw, UsersRound, Download } from 'lucide-react'
import { api } from './api'

const money = (value) => 'S/ ' + Number(value || 0).toFixed(2)
export default function MarketingDashboard() {
  const [data, setData] = useState(null)
  const [clients, setClients] = useState([])
  const [clientId, setClientId] = useState('')
  const [consumption, setConsumption] = useState([])
  const [segment, setSegment] = useState('todos')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadingConsumption, setLoadingConsumption] = useState(false)
  const [strategic, setStrategic] = useState({ birthdays: [], inactive: [], best: [] })
  const [audience, setAudience] = useState('todos')
  const [exporting, setExporting] = useState(false)
  const exportCsv = async () => {
    setExporting(true); setError('')
    try {
      const blob = await api.download(`/api/marketing/exportar.csv?segmento=${audience}`)
      const url = URL.createObjectURL(blob), link = document.createElement('a')
      link.href = url; link.download = `mi-trampita-meta-${audience}.csv`; document.body.appendChild(link); link.click(); link.remove()
      setTimeout(() => URL.revokeObjectURL(url), 10000)
    } catch (err) { setError(err.message) } finally { setExporting(false) }
  }
  const load = async () => {
    setLoading(true); setError('')
    try {
      const [dashboard, customerList, birthdays, inactive, best] = await Promise.all([api.list('/api/marketing'), api.list('/api/clientes'), api.list('/api/marketing/proximos-cumpleaneros'), api.list('/api/marketing/inactivos'), api.list('/api/marketing/mejores')])
      setData(dashboard); setClients(customerList)
      setStrategic({ birthdays, inactive, best })
    } catch (err) { setError(err.message) } finally { setLoading(false) }
  }
  useEffect(() => { load() }, [])
  useEffect(() => {
    let active = true
    setConsumption([])
    if (!clientId) { setLoadingConsumption(false); return }
    setLoadingConsumption(true); setError('')
    api.list(`/api/marketing/clientes/${clientId}/consumo`)
      .then((items) => { if (active) setConsumption(items) })
      .catch((err) => { if (active) setError(err.message) })
      .finally(() => { if (active) setLoadingConsumption(false) })
    return () => { active = false }
  }, [clientId])
  const filtered = clients.filter((client) => segment === 'todos'
    || (segment === 'frecuentes' && client.frecuenciaVisitas >= 5)
    || (segment === 'alto-consumo' && Number(client.totalGastado) >= 500)
    || (segment === 'nuevos' && client.frecuenciaVisitas <= 1))
  const maxVisits = Math.max(1, ...(data?.frecuentes || []).map((client) => client.frecuenciaVisitas))
  return <>
    <div className="page-heading compact"><div><span className="eyebrow">Administración / Fidelización</span><h1>Marketing</h1>
      <p className="muted">Conoce a tus clientes y prepara promociones según sus visitas, cumpleaños y consumo.</p></div>
      <button className="secondary-button" disabled={loading} onClick={load}><RefreshCw size={16} /> Actualizar</button></div>
    {error && <div className="form-error" role="alert">{error}</div>}
    {loading ? <div className="loading">Cargando indicadores…</div> : data && <>
      <div className="metric-grid marketing-metrics">
        <div className="metric"><UsersRound /><div><span>Clientes registrados</span><strong>{data.totalClientes}</strong></div></div>
        <div className="metric"><Cake /><div><span>Cumpleaños del mes</span><strong>{data.cumpleaneros.length}</strong></div></div>
        <div className="metric"><BarChart3 /><div><span>Clientes con 5 o más visitas</span><strong>{clients.filter(c => c.frecuenciaVisitas >= 5).length}</strong></div></div>
      </div>
      <p className="muted">Una visita equivale a una venta cobrada. Los totales incluyen IGV; los pedidos abiertos no cuentan.</p>
      <section className="panel recent-panel"><div className="panel-head"><h3>Públicos personalizados</h3><select aria-label="Público para exportar" value={audience} onChange={e => setAudience(e.target.value)}><option value="todos">Todos los clientes con contacto</option><option value="cumpleaneros">Próximos cumpleaños · 30 días</option><option value="inactivos">Inactivos · más de 60 días</option><option value="mejores">Mejores clientes · top 10</option></select></div><button className="primary-button" disabled={exporting} onClick={exportCsv}><Download size={17} />{exporting ? 'Preparando CSV…' : 'Exportar CSV para Facebook Ads'}</button><p className="muted">El archivo incluye correo y teléfono normalizados de clientes con contacto válido.</p></section>
      <div className="marketing-grid">{[['birthdays', 'Próximos cumpleañeros · 30 días'], ['inactive', 'Clientes inactivos · más de 60 días'], ['best', 'Mejores clientes por consumo']].map(([key, title]) => <section className="panel" key={key}><div className="panel-head"><h3>{title}</h3></div><div className="table-wrap"><table><thead><tr><th>Cliente</th><th>Contacto</th><th>Consumo</th></tr></thead><tbody>{strategic[key].map(c => <tr key={c.id}><td>{c.nombre}{key === 'birthdays' && <small> · {c.fechaNacimiento?.slice(5)}</small>}</td><td>{c.telefono || c.correo || 'Sin contacto'}</td><td>{money(c.totalGastado)}</td></tr>)}</tbody></table>{!strategic[key].length && <p className="empty">Sin clientes en este segmento.</p>}</div></section>)}</div>
      <div className="marketing-grid">
        <section className="panel"><div className="panel-head"><h3>Top 10 clientes frecuentes</h3></div>
          <div className="marketing-ranking">{data.frecuentes.map((client, index) => <button type="button" className="ranking-row" key={client.id} onClick={() => setClientId(String(client.id))}>
            <span>{index + 1}. {client.nombre}</span><b>{client.frecuenciaVisitas} visitas · {money(client.totalGastado)}</b>
            <span className="ranking-bar" style={{ width: `${100 * client.frecuenciaVisitas / maxVisits}%` }} /></button>)}
            {!data.frecuentes.length && <p className="empty">Aún no hay ventas cobradas.</p>}</div>
        </section>
        <section className="panel"><div className="panel-head"><h3>Cumpleaños · {new Intl.DateTimeFormat('es-PE', { month: 'long', timeZone: 'UTC' }).format(new Date(Date.UTC(2000, data.mes - 1, 1)))}</h3></div>
          <div className="table-wrap"><table><thead><tr><th>Cliente</th><th>Día</th><th>Contacto</th></tr></thead><tbody>
            {data.cumpleaneros.map(client => <tr key={client.id}><td>{client.nombre}</td><td>{client.fechaNacimiento?.slice(-2)}</td><td>{client.telefono || client.correo || 'Sin contacto'}</td></tr>)}
          </tbody></table>{!data.cumpleaneros.length && <p className="empty">No hay cumpleaños registrados este mes.</p>}</div>
        </section>
      </div>
      <section className="panel recent-panel"><div className="panel-head"><h3>Segmentos de clientes</h3>
        <select aria-label="Segmento" value={segment} onChange={event => setSegment(event.target.value)}>
          <option value="todos">Todos</option><option value="frecuentes">Frecuentes: 5+ visitas</option>
          <option value="alto-consumo">Consumo acumulado: S/ 500+</option><option value="nuevos">Nuevos: 0–1 visitas</option>
        </select></div>
        <div className="table-wrap"><table><thead><tr><th>Cliente</th><th>Visitas</th><th>Gasto total</th><th>Hábitos</th></tr></thead>
          <tbody>{filtered.map(client => <tr key={client.id}><td>{client.nombresRazonSocial}</td><td>{client.frecuenciaVisitas}</td><td>{money(client.totalGastado)}</td>
            <td><button className="text-button" onClick={() => setClientId(String(client.id))}>Ver consumo</button></td></tr>)}</tbody></table>
          {!filtered.length && <p className="empty">No hay clientes en este segmento.</p>}</div>
      </section>
      <section className="panel recent-panel"><div className="panel-head"><div><h3>Platos y bebidas más consumidos</h3><small>Top 10 por unidades · importes sin IGV</small></div>
        <select aria-label="Cliente para consultar consumo" value={clientId} onChange={event => setClientId(event.target.value)}>
          <option value="">Seleccionar cliente</option>{clients.map(client => <option key={client.id} value={client.id}>{client.nombresRazonSocial}</option>)}</select></div>
        {loadingConsumption ? <div className="loading">Consultando consumo…</div> : <div className="table-wrap"><table><thead><tr><th>Producto</th><th>Categoría</th><th>Unidades</th><th>Importe</th></tr></thead>
          <tbody>{consumption.map(item => <tr key={item.productoId}><td>{item.nombre}</td><td>{item.categoria}</td><td>{item.unidades}</td><td>{money(item.importe)}</td></tr>)}</tbody></table>
          {!consumption.length && <p className="empty">{clientId ? 'Este cliente todavía no tiene consumo cobrado.' : 'Selecciona un cliente para consultar sus hábitos.'}</p>}</div>}
      </section>
    </>}
  </>
}
