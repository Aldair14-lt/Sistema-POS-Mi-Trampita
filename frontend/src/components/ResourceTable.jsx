import { useRef, useState } from 'react'
import { ClipboardList, Pencil, Trash2 } from 'lucide-react'
import { api } from '../api'

function cellValue(key, value) {
  if (key.includes('precio')) return `S/ ${Number(value || 0).toFixed(2)}`
  if (Array.isArray(value)) return value.join(', ')
  if (value && typeof value === 'object') return value.nombre || value.razonSocial || value.nombreCategoria || value.nombreMarca || 'Objeto'
  return value ?? '—'
}

/** Tabla común del catálogo: confirma y serializa las eliminaciones del usuario. */
export default function ResourceTable({ resource, items, onRefresh, onEdit, onError }) {
  const [removingId, setRemovingId] = useState(null)
  const removing = useRef(false)

  async function remove(item) {
    if (removing.current || !window.confirm(`¿Eliminar este ${resource.singular}? Esta acción no se puede deshacer.`)) return
    removing.current = true
    setRemovingId(item.id)
    onError('')
    try {
      await api.remove(`${resource.endpoint}/${item.id}`)
      onRefresh()
    } catch (err) {
      onError(err.message || 'No se pudo eliminar el registro.')
    } finally {
      removing.current = false
      setRemovingId(null)
    }
  }

  if (!items.length) return <div className="empty"><ClipboardList size={22} aria-hidden="true" /><span>No hay registros para mostrar.</span></div>
  return <div className="table-wrap"><table aria-label={resource.label} aria-busy={removingId !== null}>
    <thead><tr>
      {resource.columns.map(([key, label]) => <th key={key} scope="col">{label}</th>)}
      {!resource.readOnly && <th scope="col">Acciones</th>}
    </tr></thead>
    <tbody>{items.map(item => <tr key={item.id}>
      {resource.columns.map(([key]) => <td key={key}>{cellValue(key, item[key])}</td>)}
      {!resource.readOnly && <td className="row-actions">
        <button type="button" className="row-action" disabled={removingId !== null} onClick={() => onEdit(item)}
          title={`Modificar ${resource.singular}`} aria-label={`Modificar ${resource.singular}`}><Pencil size={15} aria-hidden="true" /></button>
        <button type="button" className="row-action danger" disabled={removingId !== null} onClick={() => remove(item)}
          title={`Eliminar ${resource.singular}`} aria-label={`Eliminar ${resource.singular}`}><Trash2 size={15} aria-hidden="true" /></button>
      </td>}
    </tr>)}</tbody>
  </table></div>
}
