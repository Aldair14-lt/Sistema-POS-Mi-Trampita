import { useEffect, useState } from 'react'

const states = { LIBRE: 'Libre', OCUPADA: 'Ocupada', ATENDIENDO: 'Atendiendo' }

export default function TablesView({ areas, mesas, openSales, selectedMesaId, onSelect, disabled }) {
  const [areaId, setAreaId] = useState('')
  const visibleAreas = areas.filter((area) => area.estado === 'ACTIVA')
  useEffect(() => {
    if (!visibleAreas.some((area) => String(area.id) === areaId)) setAreaId(String(visibleAreas[0]?.id || ''))
  }, [areas, areaId])
  const tables = mesas.filter((mesa) => String(mesa.area?.id) === areaId)
  return <section className="mesa-board panel" aria-label="Áreas y mesas">
    <div className="panel-head"><div><span className="eyebrow">Salón</span><h3>Áreas y mesas</h3></div>
      <span className="result-count">{tables.filter((mesa) => mesa.estado === 'LIBRE').length} libres · {tables.length} mesas</span></div>
    <div className="area-tabs" role="tablist" aria-label="Áreas del recreo">
      {visibleAreas.map((area) => <button key={area.id} id={`area-tab-${area.id}`} type="button" role="tab"
        aria-selected={String(area.id) === areaId} aria-controls="area-tables" className={String(area.id) === areaId ? 'filter-chip active' : 'filter-chip'}
        onClick={() => setAreaId(String(area.id))}>{area.nombre}</button>)}
    </div>
    <div className="mesa-grid" id="area-tables" role="tabpanel" aria-labelledby={areaId ? `area-tab-${areaId}` : undefined}>
      {tables.map((mesa) => {
        const sale = openSales.find((item) => item.mesa?.id === mesa.id)
        const ready = sale?.detalles.filter(item => item.estadoPreparacion === 'LISTO').reduce((sum, item) => sum + item.cantidad, 0) || 0
        return <button type="button" key={mesa.id} disabled={disabled} onClick={() => onSelect(mesa)}
          aria-pressed={String(selectedMesaId) === String(mesa.id)}
          className={`mesa-card mesa-${mesa.estado.toLowerCase()} ${String(selectedMesaId) === String(mesa.id) ? 'selected' : ''}`}>
          <strong>Mesa {mesa.numero}</strong>{ready > 0 && <span className="ready-alert" role="status">{ready} listo(s) para recoger</span>}<span>{states[mesa.estado] || mesa.estado}</span>
          <small>{mesa.capacidad} personas{sale ? ` · S/ ${Number(sale.total).toFixed(2)}` : ''}</small>
        </button>
      })}
      {!tables.length && <p className="empty">No hay mesas en esta área.</p>}
    </div>
    <div className="table-legend"><span>● Libre</span><span>● Ocupada: esperando pedido</span><span>● Atendiendo: comanda abierta</span></div>
  </section>
}