export function storageRead(storage, key, fallback) {
  try { return JSON.parse(globalThis[storage].getItem(key)) ?? fallback } catch { return fallback }
}
export function storageWrite(storage, key, value) {
  try { value == null ? globalThis[storage].removeItem(key) : globalThis[storage].setItem(key, JSON.stringify(value)) }
  catch { /* Navegación privada: conservar la operación en memoria. */ }
}
