export const preparationLabels = {
  PENDIENTE: 'Pendiente', EN_PREPARACION: 'Preparando', LISTO: 'Listo para entregar', SERVIDO: 'Entregado'
}
export const money = (amount) => Number(amount || 0).toFixed(2)
export function PaymentBadge({ sale }) {
  const paid = Number(sale.totalPagado || 0)
  const label = sale.estadoCuenta === 'CERRADA' ? 'Pagado' : paid > 0 ? 'Adelanto' : 'Contra entrega'
  return <span className={`order-badge payment-${sale.estadoCuenta.toLowerCase()}`}>{label}</span>
}
export default function OrderStatus({ state }) {
  return <span className={`order-badge preparation-${state?.toLowerCase()}`}>{preparationLabels[state] || state}</span>
}
