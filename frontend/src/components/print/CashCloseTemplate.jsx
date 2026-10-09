import PrintHeader, { money, date } from './PrintHeader'
export default function CashCloseTemplate({ session: s }) {
  return <><PrintHeader name={s.empresaNombre} ruc={s.empresaRuc} /><h1>CIERRE Y ARQUEO DE CAJA · TURNO #{s.id}</h1>
    <div className="print-customer"><span>Apertura: {date(s.fechaApertura)} · {s.abiertoPor}</span><span>Cierre: {date(s.fechaCierre)} · {s.cerradoPor || 'Turno abierto'}</span></div>
    <table><thead><tr><th>Concepto</th><th>Importe (S/)</th></tr></thead><tbody><tr><td>Sencillo inicial</td><td>{money(s.montoInicial)}</td></tr>{Object.entries(s.montosPorMetodo).map(([method, value]) => <tr key={method}><td>Cobrado · {method}</td><td>{money(value)}</td></tr>)}<tr><th>Total cobrado</th><td>{money(s.totalCobrado)}</td></tr><tr><th>Efectivo esperado (inicial + cobros netos)</th><td>{money(s.efectivoEsperado)}</td></tr><tr><th>Efectivo contado</th><td>{s.efectivoDeclarado == null ? '—' : money(s.efectivoDeclarado)}</td></tr><tr><th>Diferencia (contado − esperado)</th><td>{s.diferencia == null ? '—' : money(s.diferencia)}</td></tr></tbody></table>
    <p>Observaciones: {s.observaciones || 'Sin observaciones'}</p><div className="print-signatures"><span>Firma de cajero</span><span>Firma de supervisión / sello</span></div><p>Resumen del turno: incluye los abonos recibidos durante el turno, aun si sus cuentas siguen abiertas.</p>
  </>
}
