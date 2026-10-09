import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { createPortal, flushSync } from 'react-dom'
import CommandTemplate from './print/CommandTemplate'
import ReceiptTemplate from './print/ReceiptTemplate'
import CashCloseTemplate from './print/CashCloseTemplate'
import '../styles/print.css'

const PrintContext = createContext(null)

/** Una sola salida de impresión para toda la aplicación; datos React escapados. */
export default function PrintManager({ children }) {
  const [job, setJob] = useState(null)
  const [error, setError] = useState('')
  const printing = useRef(false)
  useEffect(() => {
    const finish = () => { printing.current = false; setJob(null) }
    window.addEventListener('afterprint', finish)
    return () => window.removeEventListener('afterprint', finish)
  }, [])
  const print = useCallback(async request => {
    if (printing.current) return
    if (!['RECEIPT', 'COMMAND', 'CASH_CLOSE'].includes(request.type) || !['80mm', 'A4'].includes(request.format)) {
      setError('Selecciona una plantilla y un formato de impresión válidos.'); return
    }
    if (request.type === 'RECEIPT' && !request.sale?.comprobante) {
      setError('Emite el comprobante antes de imprimirlo.'); return
    }
    printing.current = true; setError('')
    try {
      flushSync(() => setJob({ ...request, format: request.type === 'CASH_CLOSE' ? 'A4' : request.type === 'COMMAND' ? '80mm' : request.format }))
      await document.fonts?.ready
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
      window.print()
    } catch {
      printing.current = false; setJob(null); setError('No se pudo abrir la impresión. Vuelve a intentarlo.')
    }
  }, [])
  return <PrintContext.Provider value={print}>
    {children}
    {error && <div className="print-error" role="alert">{error}<button onClick={() => setError('')}>Cerrar</button></div>}
    {job && <PrintableDocument job={job} />}
  </PrintContext.Provider>
}

export function PrintableDocument({ job }) {
  return createPortal(<>
      <style>{`@page { size: ${job.format === 'A4' ? 'A4' : '80mm 297mm'}; margin: ${job.format === 'A4' ? '12mm' : '4mm'}; }`}</style>
      <section className={`print-only pos-print-document ${job.format === 'A4' ? 'print-a4' : 'print-thermal'}`} aria-label="Documento para imprimir">
        {job.type === 'COMMAND' ? <CommandTemplate sale={job.sale} area={job.area} /> : job.type === 'CASH_CLOSE' ? <CashCloseTemplate session={job.session} /> : <ReceiptTemplate sale={job.sale} />}
      </section>
    </>, document.body)
}

export function usePrint() {
  const print = useContext(PrintContext)
  if (!print) throw new Error('usePrint requiere PrintManager')
  return print
}
