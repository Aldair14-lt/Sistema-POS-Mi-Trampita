import { useEffect, useState } from 'react'
import { Clock3, Users, Utensils, CheckCheck, Armchair } from 'lucide-react'
import { money } from './OrderStatus'
import '../styles/tables.css'

export function tableStatus(mesa, sale) {
  if (sale?.cuentaSolicitada) return 'billing'
  if (sale?.detalles.some(item => !['SERVIDO', 'CANCELADO'].includes(item.estadoPreparacion))) return 'waiting'
  return mesa.estado === 'LIBRE' && !sale ? 'free' : 'occupied'
}
const states = { free: ['Libre', Armchair], occupied: ['Ocupada', Utensils], billing: ['Por cobrar', CheckCheck], waiting: ['Esperando comida', Clock3] }
const elapsed = (date, now) => {
  const minutes = Math.max(0, Math.floor((now - new Date(date)) / 60000))
  return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${minutes % 60} min`
}

export default function TablesGrid({ areas, mesas, openSales, selectedMesaId, onSelect, disabled }) {
  const [areaId, setAreaId] = useState('')
  const [now, setNow] = useState(Date.now())
  const activeAreas = areas.filter(area => area.estado === 'ACTIVA')
  useEffect(() => {
    if (!areas.some(area => area.estado === 'ACTIVA' && String(area.id) === areaId))
      setAreaId(String(areas.find(area => area.estado === 'ACTIVA')?.id || ''))
  }, [areas, areaId])
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 30000); return () => clearInterval(timer) }, [])
  const tables = mesas.filter(mesa => String(mesa.area?.id) === areaId)
  return <section className="tables-map" aria-label="Mapa de mesas">
    <header className="tables-map-header"><div><span className="eyebrow">MI TRAMPITA · SALÓN</span><h2>Una mesa, un buen momento.</h2></div><span className="tables-availability">{tables.filter(mesa => mesa.estado === 'LIBRE').length} mesas libres</span></header>
    <div className="tables-areas" aria-label="Seleccionar área">{activeAreas.map(area => <button type="button" key={area.id} aria-pressed={String(area.id) === areaId} className={String(area.id) === areaId ? 'active' : ''} onClick={() => setAreaId(String(area.id))}>{area.nombre}</button>)}</div>
    <div className="tables-legend">{Object.entries(states).map(([key, [label]]) => <span key={key}><i className={`table-dot ${key}`} />{label}</span>)}</div>
    <div className="tables-grid">{tables.map(mesa => {
      const sale = openSales.find(item => item.mesa?.id === mesa.id)
      const state = tableStatus(mesa, sale), [label, Icon] = states[state]
      const ready = sale?.detalles.filter(item => item.estadoPreparacion === 'LISTO').reduce((sum, item) => sum + item.cantidad, 0) || 0
      return <button type="button" disabled={disabled} onClick={() => onSelect(mesa)} key={mesa.id} aria-pressed={String(selectedMesaId) === String(mesa.id)} className={`table-tile ${state} ${String(selectedMesaId) === String(mesa.id) ? 'selected' : ''}`}>
        <div className="table-tile-top"><span className="table-state"><i />{label}</span><span className="table-capacity"><Users size={13} />{mesa.capacidad}</span></div>
        <div className="table-tile-center"><span className="table-furniture"><Icon size={23} /></span><strong><small>MESA</small>{String(mesa.numero).padStart(2, '0')}</strong></div>
        <div className="table-tile-footer">{sale ? <><span><Clock3 size={13} />{elapsed(sale.fechaVenta, now)}</span><b>Saldo S/ {money(sale.saldoPendiente)}</b></> : <span>{state === 'free' ? 'Abrir mesa →' : 'Tomar pedido →'}</span>}</div>
        {ready > 0 && <span className="table-ready" role="status"><CheckCheck size={14} />{ready} listo{ready !== 1 ? 's' : ''} para recoger</span>}
      </button>
    })}</div>
    {!tables.length && <p className="empty">No hay mesas en esta área.</p>}
  </section>
}
