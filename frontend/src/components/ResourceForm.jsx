import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { ArrowRight, X } from 'lucide-react'
import { api } from '../api'
import PasswordField from './PasswordField'
import useDialog from '../hooks/useDialog'
import '../styles/resource-form.css'

export default function ResourceForm({ resource, item, onClose, onSaved }) {
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
  const titleId = useId(), errorId = useId()
  const { dialog, onKeyDown } = useDialog(onClose, saving)

  useEffect(() => {
    let active = true
    const showError = err => { if (active) setError(err.message) }
    if (resource.label === 'Productos') {
      Promise.all([
        api.list('/api/categorias'),
        api.list('/api/marcas'),
        api.list('/api/proveedores')
      ]).then(([c, m, p]) => { if (active) setRelations(current => ({ ...current, categorias: c, marcas: m, proveedores: p })) }).catch(showError)
    }
    if (resource.label === 'Mesas') api.list('/api/areas').then(areas => { if (active) setRelations(current => ({ ...current, areas })) }).catch(showError)
    if (resource.label === 'Usuarios') api.list('/api/roles').then(roles => { if (active) setRelations(current => ({ ...current, roles: roles.filter(role => ['ADMIN', 'MOZO', 'CAJA', 'COCINERO', 'BARTENDER'].includes(role.nombre)) })) }).catch(showError)
    return () => { active = false }
  }, [resource.label])

  const update = (key, value) => { setForm(current => ({ ...current, [key]: value })); setError('') }

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
    if ('contrasena' in form && new TextEncoder().encode(form.contrasena).length > 72) return setError('La contraseña no debe superar 72 bytes UTF-8.')
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
  return <div className="modal-backdrop"><section className="modal resource-modal" role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1} ref={dialog} onKeyDown={onKeyDown}><div className="modal-head"><div><span className="eyebrow">{item ? 'Modificar registro' : 'Nuevo registro'}</span><h2 id={titleId}>{resource.singular}</h2></div><button type="button" className="icon-button" disabled={saving} onClick={onClose} aria-label="Cerrar"><X size={19} /></button></div><form onSubmit={submit} className="form-grid" aria-busy={saving}>
    {fields.map((field) => {
      const isSelect = (resource.label === 'Productos' && ['categoriaId', 'marcaId', 'proveedorId'].includes(field)) || field === 'areaId';
      if (field === 'rolIds') return <fieldset key={field} className="roles-field" disabled={saving}><legend>Permisos del usuario</legend>{relations.roles.map(role => <label key={role.id}><input type="checkbox" checked={form.rolIds.includes(role.id)} onChange={event => update('rolIds', event.target.checked ? ['COCINERO', 'BARTENDER'].includes(role.nombre) ? [role.id] : [...form.rolIds.filter(id => !['COCINERO', 'BARTENDER'].includes(relations.roles.find(r => r.id === id)?.nombre)), role.id] : form.rolIds.filter(id => id !== role.id))} />{role.nombre}</label>)}</fieldset>
      if (field === 'visibleWeb') return <label key={field} className="resource-checkbox"><input type="checkbox" name={field} disabled={saving} checked={Boolean(form.visibleWeb)} onChange={e => update(field, e.target.checked)} />Mostrar en el menú público</label>
      if (field === 'areaDestino') return <label key={field}>Estación de preparación<select name={field} disabled={saving} value={form[field]} onChange={e => update(field, e.target.value)}><option value="COCINA">Cocina</option><option value="BAR">Bar</option></select></label>
      const labelText = field.replace(/[A-Z]/g, (letter) => ` ${letter}`).replace(/^./, (letter) => letter.toUpperCase());
      const isRequired = (field === 'direccion' && resource.label === 'Configuración') || ['areaId','nombre', 'codigoBarras', 'nombresRazonSocial', 'numeroDocumento', 'rucDni', 'razonSocial', 'ruc', 'categoriaId', 'proveedorId', 'usuario', 'nombreCompleto', 'serie', 'numero', 'capacidad'].includes(field) || (field === 'contrasena' && !item);
      const isNumber = field.toLowerCase().includes('precio') || field.toLowerCase().includes('stock') || ['numero', 'capacidad'].includes(field);

      if (field === 'contrasena') return <PasswordField key={field} required={!item} minLength={8} maxLength={72}
        autoComplete="new-password" disabled={saving} value={form[field]} onChange={event => update(field, event.target.value)}
        aria-describedby={error ? errorId : undefined} hint={item ? 'Deja en blanco para conservar la contraseña actual.' : 'Usa al menos 8 caracteres; máximo 72 bytes UTF-8.'} />

      if (isSelect) {
        const options = field === 'areaId' ? relations.areas.filter(area => area.estado === 'ACTIVA') : field === 'categoriaId' ? relations.categorias : field === 'marcaId' ? relations.marcas : relations.proveedores;
        const nameField = field === 'areaId' ? 'nombre' : field === 'categoriaId' ? 'nombre' : field === 'marcaId' ? 'nombre' : 'razonSocial';
        return <label key={field}>{labelText}{field === 'marcaId' && <small className="form-hint">Solo necesario para bebidas; las comidas usan “Sin marca”.</small>}
          <select name={field} disabled={saving} required={isRequired} value={form[field]} onChange={(e) => update(field, e.target.value)} className="resource-select">
            <option value="">Seleccione una opción</option>
            {options.map(opt => <option key={opt.id} value={opt.id}>{opt[nameField]}</option>)}
          </select>
        </label>
      }

      if (field === 'estado') {
        return <label key={field}>Estado
          <select name={field} disabled={saving} value={form[field]} onChange={(e) => update(field, e.target.value)} className="resource-select">
            {resource.label === 'Áreas' ? <><option value="ACTIVA">Activa</option><option value="INACTIVA">Inactiva</option></> : <><option value="activo">Activo</option><option value="inactivo">Inactivo</option><option value="bloqueado">Bloqueado</option></>}
          </select>
        </label>
      }

      return <label key={field}>{labelText}
        <input name={field} required={isRequired} disabled={saving} aria-describedby={error ? errorId : undefined}
          autoComplete={field === 'correo' || field === 'correoElectronico' ? 'email' : field === 'telefono' ? 'tel' : field === 'usuario' ? 'off' : undefined}
          step={field.toLowerCase().includes('precio') ? '0.01' : isNumber ? '1' : undefined} min={['numero', 'capacidad'].includes(field) ? 1 : isNumber ? 0 : undefined}
          type={field === 'fechaNacimiento' ? 'date' : isNumber ? 'number' : field === 'correo' || field === 'correoElectronico' ? 'email' : field === 'telefono' ? 'tel' : 'text'} value={form[field]} onChange={(event) => update(field, event.target.value)} />
      </label>
    })}
    {error && <div id={errorId} className="form-error" role="alert">{error}</div>}<div className="modal-actions"><button type="button" className="secondary-button" disabled={saving} onClick={onClose}>Cancelar</button><button className="primary-button" type="submit" disabled={saving}>{saving ? 'Guardando…' : 'Guardar'} <ArrowRight size={16} /></button></div></form></section></div>
}
