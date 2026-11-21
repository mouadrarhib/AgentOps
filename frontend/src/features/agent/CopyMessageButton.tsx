import { useEffect, useRef, useState } from 'react'
import { Check, Copy, WarningCircle } from '@phosphor-icons/react'
import { Button } from '../../components/motion/button/base'

const RESET_DELAY = 2000

async function writeToClipboard(text: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }
  const area = document.createElement('textarea')
  area.value = text
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.opacity = '0'
  document.body.appendChild(area)
  area.select()
  const copied = typeof document.execCommand === 'function' && document.execCommand('copy')
  area.remove()
  if (!copied) throw new Error('Clipboard unavailable')
}

export function CopyMessageButton({ text }: { text: string }) {
  const [status, setStatus] = useState<'idle' | 'copied' | 'error'>('idle')
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => () => window.clearTimeout(timer.current), [])

  async function copy() {
    try {
      await writeToClipboard(text)
      setStatus('copied')
    } catch {
      setStatus('error')
    }
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setStatus('idle'), RESET_DELAY)
  }

  const Icon = status === 'copied' ? Check : status === 'error' ? WarningCircle : Copy
  return (
    <Button type="button" variant="ghost" size="sm" className="message-copy" onClick={() => void copy()} aria-label={status === 'copied' ? 'Message copied' : 'Copy message'}>
      <Icon size={14} weight={status === 'idle' ? 'regular' : 'bold'} />
      <span aria-live="polite">{status === 'copied' ? 'Copied!' : status === 'error' ? 'Copy failed' : 'Copy'}</span>
    </Button>
  )
}
