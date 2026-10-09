import { useEffect, useRef } from 'react'

/** Mantiene el foco dentro del diálogo y lo devuelve al control que lo abrió. */
export default function useDialog(onClose, busy = false) {
  const dialog = useRef(null)
  useEffect(() => {
    const previous = document.activeElement
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    dialog.current?.focus()
    return () => {
      document.body.style.overflow = overflow
      if (previous?.isConnected) previous.focus()
    }
  }, [])

  function onKeyDown(event) {
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      if (!busy) onClose()
      return
    }
    if (event.key !== 'Tab') return
    const controls = [...dialog.current.querySelectorAll(
      'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], [tabindex="0"]'
    )].filter(element => element.getClientRects().length > 0)
    const first = controls[0], last = controls.at(-1)
    if (!first) { event.preventDefault(); dialog.current.focus(); return }
    if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) {
      event.preventDefault(); last.focus()
    } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialog.current)) {
      event.preventDefault(); first.focus()
    }
  }

  return { dialog, onKeyDown }
}
