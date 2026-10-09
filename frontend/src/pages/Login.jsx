import { useId, useRef, useState } from 'react'
import { ArrowRight, Store, Zap } from 'lucide-react'
import { api } from '../api'
import PasswordField from '../components/PasswordField'

export default function Login({ onLogin }) {
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const submitting = useRef(false)
  const errorId = useId()

  async function submit(event) {
    event.preventDefault()
    if (submitting.current) return
    if (!name.trim() || !password.trim()) return setError('Ingresa tu usuario y contraseña.')
    if (new TextEncoder().encode(password).length > 72) return setError('La contraseña no debe superar 72 bytes UTF-8.')
    submitting.current = true
    setLoading(true)
    setError('')
    try {
      const session = await api.create('/api/auth/login', { usuario: name.trim(), contrasena: password })
      onLogin(session)
    } catch (err) {
      setError(err.message || 'No se pudo iniciar sesión. Vuelve a intentar.')
    } finally {
      submitting.current = false
      setLoading(false)
    }
  }

  return <main className="login-page">
    <section className="login-art">
      <div className="brand-mark"><Store size={20} aria-hidden="true" /> MI TRAMPITA</div>
      <div className="login-quote"><span>01 / POS</span><h1>MI TRAMPITA<br /><em>Trabajo con ritmo.</em></h1><p>Mesas, cocina y caja al ritmo del recreo.</p></div>
      <div className="art-grid" />
    </section>
    <section className="login-panel">
      <div className="login-form">
        <div className="mobile-brand"><Store size={20} aria-hidden="true" /> MI TRAMPITA</div>
        <span className="eyebrow">Punto de venta</span>
        <h2>Bienvenido de vuelta</h2><p className="muted">Accede a tu espacio de trabajo.</p>
        <form onSubmit={submit} aria-busy={loading}>
          <label>Usuario<input name="usuario" required maxLength={50} autoComplete="username"
            autoCapitalize="none" spellCheck={false} autoFocus disabled={loading}
            aria-invalid={Boolean(error)} aria-describedby={error ? errorId : undefined}
            value={name} onChange={event => { setName(event.target.value); setError('') }} placeholder="Tu nombre de usuario" /></label>
          <div className="login-password"><PasswordField required maxLength={72} disabled={loading}
            autoComplete="current-password" aria-invalid={Boolean(error)} aria-describedby={error ? errorId : undefined}
            value={password} onChange={event => { setPassword(event.target.value); setError('') }} placeholder="Tu contraseña" /></div>
          {error && <div id={errorId} className="form-error" role="alert">{error}</div>}
          <button className="primary-button full" type="submit" disabled={loading}>
            {loading ? 'Iniciando sesión...' : 'Entrar'} <ArrowRight size={18} aria-hidden="true" />
          </button>
        </form>
        <p className="login-note"><Zap size={14} aria-hidden="true" /> Acceso seguro al sistema del recreo.</p>
      </div>
      <span className="login-footer">Mi Trampita · 2026</span>
    </section>
  </main>
}
