import { useEffect, useRef, useState } from 'react'

/** El navegador permite audio solo después de una acción del usuario. */
export default function useOrderAlerts(keys) {
  const seen = useRef(null)
  const context = useRef(null)
  const [sound, setSound] = useState(false)
  const [incoming, setIncoming] = useState(0)
  useEffect(() => {
    if (keys == null) return
    const next = new Set(keys)
    const count = seen.current ? keys.filter(key => !seen.current.has(key)).length : 0
    seen.current = next
    if (!count) return
    setIncoming(current => current + count)
    const audio = context.current
    if (sound && audio?.state === 'running') {
      const oscillator = audio.createOscillator(), gain = audio.createGain()
      oscillator.connect(gain); gain.connect(audio.destination)
      oscillator.frequency.setValueAtTime(880, audio.currentTime)
      gain.gain.setValueAtTime(0.12, audio.currentTime)
      gain.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + 0.35)
      oscillator.start(); oscillator.stop(audio.currentTime + 0.35)
    }
  }, [keys, sound])
  useEffect(() => () => { context.current?.close() }, [])
  const toggleSound = async () => {
    if (sound) { setSound(false); return }
    const Audio = window.AudioContext || window.webkitAudioContext
    if (!Audio) return
    try { context.current ||= new Audio(); await context.current.resume(); setSound(true) } catch { setSound(false) }
  }
  return { sound, incoming, toggleSound, dismiss: () => setIncoming(0) }
}
