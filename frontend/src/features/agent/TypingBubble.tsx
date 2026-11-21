// The assistant's "writing now" placeholder. It sits in the same slot the reply
// will land in, so the reply reads as this bubble resolving rather than a new
// unrelated block appearing. Motion: three dots in a staggered wave (primary),
// a slow sheen across the bubble surface (ambient). Both stop under
// prefers-reduced-motion; the caption still carries the state.
export function TypingBubble() {
  return <div className="chat-message from-agent typing" role="status">
    <span className="message-author">AgentOps</span>
    <div className="typing-bubble">
      <span className="typing-dots" aria-hidden="true">
        <span className="typing-dot"/>
        <span className="typing-dot"/>
        <span className="typing-dot"/>
      </span>
      <span className="typing-caption">writing a reply…</span>
    </div>
  </div>
}
