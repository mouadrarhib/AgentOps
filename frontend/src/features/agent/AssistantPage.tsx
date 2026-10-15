import { useState, type FormEvent, type KeyboardEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowUp, ChatCircleDots, Plus, Sparkle } from '@phosphor-icons/react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button } from '../../components/motion/button/base'
import { DataState, PageHeader, Pager } from '../../components/Workspace'
import { api, dateLabel, timeLabel } from '../../lib/api'
import { errorMessage } from '../../lib/http'

const suggestions = ['Show my recent orders', 'List my support tickets', 'Help me create a ticket for an order']

export function AssistantPage() {
  const navigate = useNavigate()
  const client = useQueryClient()
  const param = useParams().conversationId
  const conversationId = param ? Number(param) : undefined
  const invalidId = param !== undefined && (!Number.isInteger(conversationId) || !conversationId || conversationId <= 0)
  const [conversationPage, setConversationPage] = useState(0)
  const [messagePage, setMessagePage] = useState(0)
  const [message, setMessage] = useState('')
  const [pendingMessage, setPendingMessage] = useState('')
  const [lastToolActivity, setLastToolActivity] = useState<{ conversationId: number; count: number } | null>(null)
  const conversations = useQuery({ queryKey: ['conversations', conversationPage], queryFn: () => api.conversations(conversationPage) })
  const detail = useQuery({ queryKey: ['conversation', conversationId, messagePage], queryFn: () => api.conversation(conversationId!, messagePage), enabled: !!conversationId && !invalidId })
  const send = useMutation({
    mutationFn: (text: string) => api.chat(text, conversationId),
    onSuccess: response => {
      setMessage('')
      setPendingMessage('')
      setLastToolActivity({ conversationId: response.conversationId, count: response.toolCalls })
      void client.invalidateQueries({ queryKey: ['conversations'] })
      void client.invalidateQueries({ queryKey: ['conversation', response.conversationId] })
      if (!conversationId) navigate(`/assistant/${response.conversationId}`, { replace: true })
      else {
        const nextLastPage = Math.floor(((detail.data?.totalElements ?? 0) + 1) / 100)
        setMessagePage(nextLastPage)
      }
    },
    onError: () => setPendingMessage(''),
  })
  function submit(event?: FormEvent) {
    event?.preventDefault()
    const text = message.trim()
    if (!text || send.isPending) return
    setPendingMessage(text)
    send.mutate(text)
  }
  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); submit() }
  }
  function chooseConversation(id?: number) {
    setMessagePage(0)
    setLastToolActivity(null)
    setMessage('')
    send.reset()
    navigate(id ? `/assistant/${id}` : '/assistant')
  }

  return <div className="workspace-page assistant-page">
    <PageHeader title="AI assistant" description="Ask about your orders and support tickets. Backend rules control every action." />
    <div className="assistant-layout">
      <aside className="conversation-panel" aria-label="Conversations"><div className="conversation-heading"><h2>Conversations</h2><Button size="icon" variant="outline" aria-label="New conversation" onClick={() => chooseConversation()}><Plus size={18}/></Button></div>
        <DataState loading={conversations.isPending} error={conversations.error} onRetry={() => void conversations.refetch()} empty={conversations.data?.content.length === 0}>
          {conversations.data?.content.length === 0 ? <p>No conversations yet. Start with a question.</p> : <div className="conversation-list">{conversations.data?.content.map(item => <button key={item.id} type="button" className={`conversation-item${conversationId === item.id ? ' selected' : ''}`} onClick={() => chooseConversation(item.id)}><ChatCircleDots size={19}/><span><strong>Conversation #{item.id}</strong><small>{dateLabel(item.createdAt)}</small></span></button>)}</div>}
        </DataState>
        <Pager page={conversationPage} totalPages={conversations.data?.totalPages ?? 0} onChange={setConversationPage}/>
      </aside>
      <section className="chat-panel" aria-label="Assistant chat"><div className="chat-panel-top"><span className="agent-avatar"><Sparkle size={20} weight="fill"/></span><div><strong>AgentOps assistant</strong><small>Responses use your account's available records</small></div></div>
        <div className="chat-thread" aria-live="polite">
          {invalidId ? <div className="data-state data-error" role="alert">Invalid conversation ID.</div> : conversationId ? <DataState loading={detail.isPending} error={detail.error} onRetry={() => void detail.refetch()} empty={detail.data?.messages.length === 0}>
            {detail.data?.messages.length === 0 ? <p>No messages in this conversation yet.</p> : <><div className="message-page-control"><Pager page={messagePage} totalPages={detail.data?.totalPages ?? 0} onChange={setMessagePage}/></div>{detail.data?.messages.map(item => <div key={item.id} className={`chat-message ${item.role === 'USER' ? 'from-user' : 'from-agent'}`}><span className="message-author">{item.role === 'USER' ? 'You' : 'AgentOps'}<time>{timeLabel(item.createdAt)}</time></span><p>{item.content}</p></div>)}{lastToolActivity?.conversationId === conversationId && lastToolActivity.count > 0 && <span className="tool-count">{lastToolActivity.count} backend tool {lastToolActivity.count === 1 ? 'call' : 'calls'} in the last reply</span>}</>}
          </DataState> : <div className="chat-empty"><div className="chat-empty-mark"><Sparkle size={32} weight="fill"/></div><h2>Ask AgentOps</h2><p>Find order details, check tickets, or ask for help with a support request. The assistant can request backend tools; Java checks and runs them.</p><div className="suggestions">{suggestions.map(text => <button key={text} type="button" onClick={() => setMessage(text)}>{text} <ArrowUp size={15}/></button>)}</div></div>}
          {send.isPending && <div className="chat-message from-user pending"><span className="message-author">You</span><p>{pendingMessage}</p></div>}
          {send.isPending && <div className="reply-waiting" role="status"><span className="loading-dot"/> AgentOps is preparing a reply…</div>}
          {send.error && <div className="chat-error" role="alert"><strong>Could not complete that reply.</strong><p>{errorMessage(send.error)}</p><small>If the connection failed after a write request, check orders or tickets before trying again.</small></div>}
        </div>
        <form className="chat-composer" onSubmit={submit}><label htmlFor="agent-message" className="sr-only">Message AgentOps</label><textarea id="agent-message" rows={2} maxLength={4000} value={message} onChange={event => setMessage(event.target.value)} onKeyDown={handleKeyDown} placeholder="Ask about an order or support ticket…" disabled={send.isPending || invalidId}/><Button type="submit" size="icon" aria-label="Send message" disabled={!message.trim() || send.isPending || invalidId}><ArrowUp size={20} weight="bold"/></Button><small>Enter to send · Shift+Enter for a new line</small></form>
      </section>
    </div>
    <p className="assistant-note">The model may request a tool. AgentOps validates permissions and executes it in the Java backend.</p>
  </div>
}
