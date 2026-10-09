import { Store } from 'lucide-react'
export const money = value => Number(value || 0).toFixed(2)
export const date = value => value ? new Date(value).toLocaleString('es-PE', { timeZone: 'America/Lima' }) : '—'
export default function PrintHeader({ name, ruc, address }) {
  return <header className="print-header"><Store className="print-logo" size={32} aria-hidden="true" /><strong>{name || 'RECREO MI TRAMPITA'}</strong>{ruc && <span>RUC: {ruc}</span>}{address && <span>{address}</span>}</header>
}
