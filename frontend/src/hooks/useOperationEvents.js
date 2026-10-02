import { useEffect, useRef } from 'react'
import { API_URL } from '../api'

const streams = new Map()

/** Una conexión compartida por pantalla; el polling sigue disponible si se corta SSE. */
export default function useOperationEvents(onChange, kitchen = false) {
  const callback = useRef(onChange)
  callback.current = onChange
  useEffect(() => {
    const path = kitchen ? '/api/cocina/eventos' : '/api/operacion/eventos'
    let stream = streams.get(path)
    if (!stream) {
      const source = new EventSource(API_URL + path, { withCredentials: true })
      stream = { source, listeners: new Set() }
      source.addEventListener('actualizar', () => stream.listeners.forEach(listener => listener()))
      streams.set(path, stream)
    }
    let timer
    const listener = () => { clearTimeout(timer); timer = setTimeout(() => callback.current(), 120) }
    stream.listeners.add(listener)
    return () => {
      clearTimeout(timer)
      stream.listeners.delete(listener)
      if (!stream.listeners.size) { stream.source.close(); streams.delete(path) }
    }
  }, [kitchen])
}
