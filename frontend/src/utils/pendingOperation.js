import { storageRead, storageWrite } from './storage.js'

const key = saleId => `pos-pending-payment-${saleId}`
export function restorePayment(saleId) {
  const pending = storageRead('sessionStorage', key(saleId), null)
  return pending && [`/api/ventas/${saleId}/pagos`, `/api/ventas/${saleId}/cobrar`].includes(pending.path)
    && pending.body && (pending.key == null || typeof pending.key === 'string') ? pending : null
}
export function persistPayment(saleId, pending) { storageWrite('sessionStorage', key(saleId), pending) }
