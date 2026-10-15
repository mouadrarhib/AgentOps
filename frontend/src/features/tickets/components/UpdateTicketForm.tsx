import { useState, type FormEvent } from 'react'
import { Button } from '../../../components/motion/button/base'
import { ConfirmDialog } from '../../../components/ConfirmDialog'
import { humanize, ticketPriorities, ticketStatuses } from '../../../lib/api'
import { ApiError, errorMessage } from '../../../lib/http'
import { useUpdateTicket } from '../hooks/use-tickets'
import type { TicketPriority, TicketStatus, TicketUpdate } from '../types'

export function UpdateTicketForm({ id }: { id: number }) {
  const [status, setStatus] = useState<TicketStatus | ''>('')
  const [priority, setPriority] = useState<TicketPriority | ''>('')
  const [assignee, setAssignee] = useState('')
  const [unassign, setUnassign] = useState(false)
  const [validation, setValidation] = useState('')
  const [pendingUpdate, setPendingUpdate] = useState<TicketUpdate | null>(null)
  const mutation = useUpdateTicket(id)
  const fields = mutation.error instanceof ApiError ? mutation.error.fields : []
  const changes = pendingUpdate ? [
    pendingUpdate.status && `status to ${humanize(pendingUpdate.status)}`,
    pendingUpdate.priority && `priority to ${humanize(pendingUpdate.priority)}`,
    pendingUpdate.assignedToId && `assignee to staff #${pendingUpdate.assignedToId}`,
    pendingUpdate.unassign && 'clear the assignment',
  ].filter(Boolean).join(', ') : ''

  function submit(event: FormEvent) {
    event.preventDefault()
    setValidation('')
    const assignedToId = assignee ? Number(assignee) : undefined
    if (assignee && (!Number.isSafeInteger(assignedToId) || (assignedToId || 0) <= 0)) {
      setValidation('Enter a positive whole staff ID.')
      return
    }
    if (!status && !priority && !assignedToId && !unassign) {
      setValidation('Choose at least one change.')
      return
    }
    setPendingUpdate({
      ...(status ? { status } : {}),
      ...(priority ? { priority } : {}),
      ...(assignedToId ? { assignedToId } : {}),
      ...(unassign ? { unassign: true } : {}),
    })
  }

  function saveChanges() {
    if (!pendingUpdate) return
    mutation.mutate(pendingUpdate, {
      onSuccess: () => {
        setPendingUpdate(null)
        setStatus('')
        setPriority('')
        setAssignee('')
        setUnassign(false)
      },
      onError: () => setPendingUpdate(null),
    })
  }

  return <section className="content-panel form-panel">
    <div className="panel-heading"><h2>Update ticket</h2><span>Support and admin</span></div>
    <form onSubmit={submit} noValidate>
      <div className="form-grid">
        <div><label htmlFor="update-status">Status</label><select id="update-status" value={status} onChange={event => setStatus(event.target.value as TicketStatus | '')}><option value="">Keep current</option>{ticketStatuses.map(value => <option key={value} value={value}>{humanize(value)}</option>)}</select></div>
        <div><label htmlFor="update-priority">Priority</label><select id="update-priority" value={priority} onChange={event => setPriority(event.target.value as TicketPriority | '')}><option value="">Keep current</option>{ticketPriorities.map(value => <option key={value} value={value}>{humanize(value)}</option>)}</select></div>
      </div>
      <label htmlFor="update-assignee">Assign to staff ID (optional)</label>
      <input id="update-assignee" type="number" min="1" step="1" value={assignee} disabled={unassign} aria-invalid={validation.startsWith('Enter a positive')} onChange={event => setAssignee(event.target.value)} placeholder="Leave unchanged"/>
      <label className="check-row"><input type="checkbox" checked={unassign} onChange={event => { setUnassign(event.target.checked); if (event.target.checked) setAssignee('') }}/> Clear assignment</label>
      {(validation || mutation.error) && <div className="form-error" role="alert">{validation || errorMessage(mutation.error)}{fields.map((field, index) => <p key={index}>{field.field}: {field.message}</p>)}</div>}
      {mutation.isSuccess && <p className="success-message" role="status">Ticket updated.</p>}
      <Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? 'Saving changes…' : 'Save changes'}</Button>
    </form>
    <ConfirmDialog open={pendingUpdate !== null} title="Save ticket changes?" description={`For ticket #${id}: ${changes}.`} confirmLabel="Save changes" pending={mutation.isPending} onCancel={() => setPendingUpdate(null)} onConfirm={saveChanges}/>
  </section>
}
