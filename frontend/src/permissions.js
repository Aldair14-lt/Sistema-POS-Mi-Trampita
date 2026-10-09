export function permissions(session) {
  const roles = new Set(session?.roles || [])
  const isAdmin = roles.has('ADMIN')
  const canOrder = isAdmin || roles.has('MOZO')
  const canCharge = isAdmin || roles.has('CAJA')
  return { isAdmin, canOrder, canCharge, canKitchen: isAdmin || roles.has('COCINERO'), canBar: isAdmin || roles.has('BARTENDER'), canOnline: canCharge }
}
export function defaultView(session) {
  const rights = permissions(session)
  return rights.isAdmin ? 'dashboard' : rights.canKitchen ? 'cocina' : rights.canBar ? 'bar' : rights.canCharge ? 'caja' : 'ventas'
}
export function canView(session, view) {
  const rights = permissions(session)
  return rights.isAdmin || (view === 'caja' && rights.canCharge) || (view === 'ventas' && (rights.canOrder || rights.canCharge)) ||
    (view === 'cocina' && rights.canKitchen) || (view === 'bar' && rights.canBar) || (view === 'online' && rights.canOnline)
}
