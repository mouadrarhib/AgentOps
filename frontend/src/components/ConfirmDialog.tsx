import { useEffect, useId, useRef } from 'react'
import { Button } from './motion/button/base'

type ConfirmDialogProps = {
  open: boolean
  title: string
  description: string
  confirmLabel: string
  pending?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({ open, title, description, confirmLabel, pending = false, onConfirm, onCancel }: ConfirmDialogProps) {
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()

  useEffect(() => {
    const element = dialog.current
    if (!element) return
    if (open && !element.open) element.showModal()
    if (!open && element.open) element.close()
  }, [open])

  return <dialog ref={dialog} className="confirm-dialog" aria-labelledby={titleId} aria-describedby={descriptionId} onCancel={event => { if (pending) event.preventDefault(); else onCancel() }}>
    <h2 id={titleId}>{title}</h2>
    <p id={descriptionId}>{description}</p>
    <div className="dialog-actions">
      <Button type="button" variant="outline" disabled={pending} onClick={onCancel}>Cancel</Button>
      <Button type="button" disabled={pending} onClick={onConfirm}>{pending ? 'Working…' : confirmLabel}</Button>
    </div>
  </dialog>
}
