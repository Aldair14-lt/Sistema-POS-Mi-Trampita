import { useCallback, useEffect, useRef, useState } from 'react'
import useOperationEvents from './useOperationEvents'
import { api } from '../api'

/** Consulta serial por intervalo. Una respuesta vieja nunca reemplaza una actualización nueva. */
export default function usePolling(path, interval = 5000) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const sequence = useRef(0)
  const refresh = useCallback(async () => {
    const current = ++sequence.current
    try {
      const result = await api.list(path)
      if (current === sequence.current) { setData(result); setError('') }
      return result
    } catch (err) {
      if (current === sequence.current) setError(err.message)
      return null
    } finally {
      if (current === sequence.current) setLoading(false)
    }
  }, [path])
  useEffect(() => {
    let stopped = false
    let timer
    setData(null); setLoading(true)
    const poll = async () => {
      await refresh()
      if (!stopped) timer = setTimeout(poll, interval)
    }
    poll()
    return () => { stopped = true; clearTimeout(timer); sequence.current++ }
  }, [refresh, interval])
  useOperationEvents(refresh, path.startsWith('/api/cocina/'))
  return { data, error, loading, refresh }
}
