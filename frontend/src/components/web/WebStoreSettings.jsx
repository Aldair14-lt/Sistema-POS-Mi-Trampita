import { useEffect, useState } from 'react'
import { Globe, ExternalLink, Copy } from 'lucide-react'
import { api } from '../../api'

export default function WebStoreSettings() {
  const [stores, setStores] = useState([]); const [id, setId] = useState(''); const [form, setForm] = useState(null)
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [message, setMessage] = useState('')
  const select = (id, rows = stores) => { const row = rows.find(s => String(s.empresaId) === String(id)); setId(String(id)); setForm(row ? { ...row, slug: row.slug || (rows.length === 1 ? 'mi-trampita' : `mi-trampita-${id}`) } : null); setMessage('') }
  useEffect(() => { let active = true; api.list('/api/web/configuracion').then(rows => { if (active) { setStores(rows); if (rows.length) select(rows[0].empresaId, rows) } }).catch(e => active && setError(e.message)); return () => { active = false } }, [])
  const published = stores.find(s => String(s.empresaId) === id)?.slug
  const link = `${window.location.origin}/pedir/${form?.slug || 'mi-trampita'}`
  const update = (field, value) => setForm(current => ({ ...current, [field]: value }))
  const save = async e => {
    e.preventDefault(); if (busy) return; setBusy(true); setError(''); setMessage('')
    try { const saved = await api.update(`/api/web/configuracion/${id}`, { slug: form.slug, activa: form.activa, recojo: form.recojo, delivery: form.delivery, mensaje: form.mensaje }); setStores(rows => rows.map(s => s.empresaId === saved.empresaId ? saved : s)); setForm(saved); setMessage(saved.activa ? 'Menú habilitado. Publica tus productos y comparte el enlace.' : 'Menú pausado.') }
    catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  const copy = async () => { try { await navigator.clipboard.writeText(link); setMessage('Enlace copiado.') } catch { setMessage('Copia el enlace mostrado.') } }
  return <details className="web-store-settings"><summary><Globe size={16} />Configurar y compartir menú público</summary>{error && <p className="form-error" role="alert">{error}</p>}{message && <p role="status">{message}</p>}
    {form && <form onSubmit={save}><fieldset disabled={busy}><label>Empresa<select value={id} onChange={e => select(e.target.value)}>{stores.map(s => <option key={s.empresaId} value={s.empresaId}>{s.empresa}</option>)}</select></label>
      <label>Identificador del enlace<input required readOnly={Boolean(published)} pattern="[a-z0-9]+(-[a-z0-9]+)*" minLength={3} maxLength={60} value={form.slug} onChange={e => update('slug', e.target.value)} /><small>Minúsculas, números y guiones. El enlace se conserva una vez publicado.</small></label>
      <div className="web-settings-checks">{[['activa','Recibir pedidos web'],['recojo','Recojo en local'],['delivery','Delivery']].map(([field,label]) => <label key={field}><input type="checkbox" checked={form[field]} onChange={e => update(field, e.target.checked)} />{label}</label>)}</div>
      <label>Mensaje para tus clientes<textarea maxLength={300} value={form.mensaje} onChange={e => update('mensaje', e.target.value)} placeholder="Horario de atención, cobertura de delivery…" /></label>
      <button className="primary-button" disabled={busy || (!form.recojo && !form.delivery)}>{busy ? 'Guardando…' : 'Guardar configuración'}</button></fieldset></form>}
    {published && <div className="web-share-link"><a href={link} target="_blank" rel="noopener noreferrer">{link}<ExternalLink size={15} /></a><button type="button" className="secondary-button" onClick={copy}><Copy size={15} />Copiar enlace</button></div>}
    <p className="muted">En Productos, activa “Mostrar en el menú público” para lo que deseas ofrecer. Comparte el enlace cuando el sistema esté publicado.</p>
  </details>
}
