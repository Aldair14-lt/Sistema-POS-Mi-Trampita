import { useId, useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import '../styles/password-field.css'

/** Campo compartido: la visibilidad nunca modifica el valor ni envía el formulario. */
export default function PasswordField({
  id, label = 'Contraseña', hint, name = 'contrasena', disabled = false,
  autoComplete = 'current-password', 'aria-describedby': describedBy, ...inputProps
}) {
  const generatedId = useId()
  const inputId = id || generatedId
  const hintId = `${inputId}-hint`
  const [visible, setVisible] = useState(false)
  const description = [hint && hintId, describedBy].filter(Boolean).join(' ') || undefined
  const action = visible ? 'Ocultar contraseña' : 'Mostrar contraseña'

  return <div className="password-field">
    <label htmlFor={inputId}>{label}</label>
    <div className="password-control">
      <input {...inputProps} id={inputId} name={name} type={visible ? 'text' : 'password'}
        disabled={disabled} autoComplete={autoComplete} autoCapitalize="none"
        autoCorrect="off" spellCheck={false} aria-describedby={description} />
      <button type="button" className="password-toggle" disabled={disabled}
        aria-label={action} title={action} aria-controls={inputId}
        onPointerDown={event => event.preventDefault()} onClick={() => setVisible(current => !current)}>
        {visible ? <EyeOff size={20} aria-hidden="true" /> : <Eye size={20} aria-hidden="true" />}
      </button>
    </div>
    {hint && <small id={hintId} className="form-hint">{hint}</small>}
  </div>
}
