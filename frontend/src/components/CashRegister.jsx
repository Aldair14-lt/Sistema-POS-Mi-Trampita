import { useEffect, useRef, useState } from 'react'
import { Banknote, Printer } from 'lucide-react'
import { api } from '../api'
import usePolling from '../hooks/usePolling'
import { usePrint } from './PrintManager'
import { money } from './OrderStatus'
import '../styles/cash-register.css'

export default function CashRegister() {
  const [companies, setCompanies] = useState([])
  const [company, setCompany] = useState('')
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    api.list('/api/pos/configuracion').then(list => { if (active) { setCompanies(list); setCompany(String(list[0]?.id || '')) } }).catch(e => active && setError(e.message))
    return () => { active = false }
  }, [])
  return <section className="cash-register panel" aria-label="Turno de caja"><header><h2><Banknote size={21} /> Apertura y arqueo de caja</h2>{companies.length > 1 && <label>Empresa<select value={company} onChange={e => setCompany(e.target.value)}>{companies.map(c => <option key={c.id} value={c.id}>{c.razonSocial}</option>)}</select></label>}</header>
    {error && <p role="alert">{error}</p>}{company ? <CashShift key={company} company={company} /> : <p className="muted">Configura una empresa para abrir caja.</p>}
  </section>
}

function CashShift({ company }) {
  const { data: shift, loading, error: loadError, refresh } = usePolling(`/api/caja/sesiones/actual?empresaId=${company}`)
  const [history, setHistory] = useState([])
  const [initial, setInitial] = useState('')
  const [counted, setCounted] = useState('')
  const [notes, setNotes] = useState('')
  const [closing, setClosing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [error, setError] = useState('')
  const pending = useRef(null), submitting = useRef(false)
  const print = usePrint()
  const loadHistory = async () => { try { setHistory(await api.list(`/api/caja/sesiones?empresaId=${company}`)) } catch (e) { setError(e.message) } }
  useEffect(() => { loadHistory() }, [company, shift?.id, shift?.fechaCierre])
  const submit = async event => {
    event.preventDefault()
    if (submitting.current) return
    if (!pending.current) pending.current = shift ? {
      path: `/api/caja/sesiones/${shift.id}/cerrar`, body: { efectivoDeclarado: Number(counted), observaciones: notes.trim() }
    } : { path: '/api/caja/sesiones/abrir', body: { empresaId: Number(company), montoInicial: Number(initial), claveOperacion: crypto.randomUUID() } }
    submitting.current = true; setBusy(true); setError('')
    try {
      await api.create(pending.current.path, pending.current.body)
      pending.current = null; setUncertain(false); setInitial(''); setCounted(''); setNotes(''); setClosing(false)
      await refresh(); await loadHistory()
    } catch (e) {
      if (!e.status || e.status >= 500) { setUncertain(true); setError('No se confirmó el turno. Reintenta la misma operación para verificarla sin duplicarla.') }
      else { pending.current = null; setUncertain(false); setError(e.message); await refresh() }
    } finally { submitting.current = false; setBusy(false) }
  }
  if (loading) return <p>Cargando turno…</p>
  return <>{(error || loadError) && <div className="form-error" role="alert">{error || loadError}</div>}
    {shift && <><p className="cash-shift-status">Turno #{shift.id} abierto · {shift.abiertoPor}</p><div className="cash-shift-metrics"><span>Sencillo inicial<strong>S/ {money(shift.montoInicial)}</strong></span><span>Cobros del turno<strong>S/ {money(shift.totalCobrado)}</strong></span><span>Efectivo esperado<strong>S/ {money(shift.efectivoEsperado)}</strong></span></div><div className="cash-method-totals">{Object.entries(shift.montosPorMetodo).map(([method, value]) => <span key={method}>{method}<b>S/ {money(value)}</b></span>)}</div>
      {!closing && !uncertain && <button className="secondary-button" onClick={() => setClosing(true)}>Cerrar turno y realizar arqueo</button>}</>}
    {(!shift || closing || uncertain) && <form onSubmit={submit}><fieldset disabled={busy || uncertain}>
      {shift ? <><label>Efectivo contado en caja (S/)<input type="number" required min="0" step="0.01" max="9999999999.99" value={counted} onChange={e => setCounted(e.target.value)} /></label><label>Observaciones del arqueo<textarea maxLength={500} value={notes} onChange={e => setNotes(e.target.value)} /></label><p>La diferencia quedará registrada. Las cuentas abiertas continúan en el siguiente turno.</p></> : <label>Sencillo inicial (S/)<input type="number" required min="0" max="9999999999.99" step="0.01" value={initial} onChange={e => setInitial(e.target.value)} /></label>}
    </fieldset><div className="cash-register-actions"><button className="primary-button" disabled={busy}>{busy ? 'Procesando…' : uncertain ? 'Comprobar / reintentar turno' : shift ? 'Confirmar cierre y arqueo' : 'Abrir turno'}</button>{closing && !uncertain && <button type="button" className="secondary-button" disabled={busy} onClick={() => setClosing(false)}>Cancelar</button>}</div></form>}
    {history.some(s => s.fechaCierre) && <details><summary>Últimos cierres de caja</summary>{history.filter(s => s.fechaCierre).map(s => <div className="cash-history-row" key={s.id}><span>Turno #{s.id} · {new Date(s.fechaCierre).toLocaleString('es-PE', { timeZone: 'America/Lima' })}<small>Diferencia S/ {money(s.diferencia)}</small></span><button className="secondary-button" onClick={() => print({ type: 'CASH_CLOSE', format: 'A4', session: s })}><Printer size={15} />Reporte A4</button></div>)}</details>}
  </>
}
