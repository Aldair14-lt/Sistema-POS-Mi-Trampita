import { useState } from 'react'
import { api } from '../api'

export default function CancelItemButton({ item, onUpdated }) {
  const [open, setOpen] = useState(false), [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  if (item.estadoPreparacion !== 'PENDIENTE') return null
  const cancel = async () => {
    if (busy || !reason.trim()) return
    setBusy(true); setError('')
    try {
      const updated = await api.patch(`/api/ventas/items/${item.id}/cancelar`, { motivo: reason.trim() })
      setOpen(false); onUpdated(updated)
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return <div className="cancel-item">
    {!open ? <button type="button" className="cancel-item-trigger" onClick={() => setOpen(true)}>Cancelar plato</button> : <div className="cancel-item-form">
      <label>Motivo de cancelación<input autoFocus maxLength={255} value={reason} onChange={e => setReason(e.target.value)} disabled={busy} /></label>
      <div><button type="button" className="secondary-button" disabled={busy} onClick={() => setOpen(false)}>Volver</button><button type="button" className="secondary-button" disabled={busy || !reason.trim()} onClick={cancel}>{busy ? 'Cancelando…' : 'Cancelar y devolver stock'}</button></div>
      {error && <p role="alert" className="form-error">{error}</p>}
    </div>}
  </div>
}
