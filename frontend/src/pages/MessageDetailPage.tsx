import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/errors'
import {
  approveMessage,
  getMessage,
  regenerateMessage,
  saveReply,
  type MessageDetail,
} from '../api/messages'
import { CategoryBadge, StatusBadge } from '../components/Badges'
import { aiErrorMessage } from '../components/labels'

export const MAX_REPLY = 5000

type Action = 'save' | 'approve' | 'regenerate'

function formatDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString() : '—'
}

function initialReply(message: MessageDetail): string {
  return message.finalReply ?? message.aiDraft ?? ''
}

export function MessageDetailPage() {
  const { id = '' } = useParams()
  const [message, setMessage] = useState<MessageDetail | null>(null)
  const [reply, setReply] = useState('')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)
  const [busy, setBusy] = useState<Action | null>(null)
  const [slow, setSlow] = useState(false)
  const [reloadCount, setReloadCount] = useState(0)
  const controllerRef = useRef<AbortController | null>(null)

  const applyServerState = useCallback((next: MessageDetail) => {
    setMessage(next)
    setReply(initialReply(next))
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    getMessage(id, { signal: controller.signal })
      .then((loaded) => {
        if (controller.signal.aborted) return
        applyServerState(loaded)
        setLoadError(null)
        setConflict(false)
        setActionError(null)
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return
        setLoadError(
          err instanceof ApiError && err.status === 404
            ? 'Message not found.'
            : err instanceof Error
              ? err.message
              : 'Could not load the message.',
        )
      })
    return () => controller.abort()
  }, [id, reloadCount, applyServerState])

  // Cancel an in-flight action when the user leaves the page.
  useEffect(() => () => controllerRef.current?.abort(), [])

  async function run(action: Action, call: (signal: AbortSignal, onSlow: () => void) => Promise<MessageDetail>) {
    const controller = new AbortController()
    controllerRef.current = controller
    setBusy(action)
    setSlow(false)
    setActionError(null)
    try {
      const updated = await call(controller.signal, () => setSlow(true))
      if (controller.signal.aborted) return
      applyServerState(updated)
    } catch (e) {
      if (controller.signal.aborted) return
      if (e instanceof ApiError && e.status === 409) setConflict(true)
      else if (e instanceof ApiError && e.status === 400 && e.fieldErrors.finalReply) {
        setActionError(e.fieldErrors.finalReply)
      } else setActionError(e instanceof Error ? e.message : 'The action failed.')
    } finally {
      if (!controller.signal.aborted) {
        setBusy(null)
        setSlow(false)
      }
    }
  }

  if (loadError) {
    return (
      <>
        <p role="alert">{loadError}</p>
        <Link to="/">Back to messages</Link>
      </>
    )
  }
  if (!message) return <p role="status">Loading…</p>

  const isDraft = message.status === 'DRAFT_READY'
  const canRegenerate = isDraft || message.status === 'AI_FAILED'
  const replyInvalid = reply.trim() === '' || reply.length > MAX_REPLY
  const dirty = reply !== initialReply(message)
  const disabled = busy !== null || conflict
  const input = { finalReply: reply, version: message.version }

  function handleRegenerate() {
    if (dirty && !window.confirm('Regenerating replaces the AI draft and discards your unsaved edits. Continue?')) {
      return
    }
    void run('regenerate', (signal, onSlow) => regenerateMessage(id, { signal, onSlow }))
  }

  return (
    <>
      <p>
        <Link to="/">← Back to messages</Link>
      </p>
      <h2>Message</h2>
      <p>
        <StatusBadge status={message.status} /> <CategoryBadge category={message.category} />
      </p>
      <dl>
        <dt>Customer</dt>
        <dd>
          {message.customerName || 'Anonymous'}
          {message.customerEmail ? ` (${message.customerEmail})` : ''}
        </dd>
        <dt>Created</dt>
        <dd>{formatDate(message.createdAt)}</dd>
        <dt>Updated</dt>
        <dd>{formatDate(message.updatedAt)}</dd>
        {message.approvedAt && (
          <>
            <dt>Approved</dt>
            <dd>{formatDate(message.approvedAt)}</dd>
          </>
        )}
      </dl>

      <h3>Customer message</h3>
      <p className="preformatted">{message.content}</p>

      {message.status === 'PENDING_AI' && <p role="status">The AI is still processing this message.</p>}

      {message.status === 'AI_FAILED' && (
        <p role="alert">
          {aiErrorMessage(message.aiErrorCode)} You can try to regenerate the draft.
        </p>
      )}

      {message.aiDraft !== null && (
        <>
          <h3>AI draft</h3>
          <p className="preformatted" data-testid="ai-draft">
            {message.aiDraft}
          </p>
        </>
      )}

      {message.status === 'APPROVED' && (
        <>
          <h3>Approved reply</h3>
          <p className="preformatted" data-testid="approved-reply">
            {message.finalReply}
          </p>
        </>
      )}

      {isDraft && (
        <>
          <h3>Final reply</h3>
          <label>
            Final reply
            <textarea value={reply} onChange={(e) => setReply(e.target.value)} rows={10} disabled={disabled} />
          </label>
          <span aria-live="polite">
            {reply.length} / {MAX_REPLY}
          </span>
        </>
      )}

      {conflict && (
        <p role="alert">
          This message was changed elsewhere. <button onClick={() => setReloadCount((n) => n + 1)}>Reload</button>
        </p>
      )}
      {actionError && <p role="alert">{actionError}</p>}
      {busy === 'regenerate' && <p role="status">The AI is writing a new draft, this can take a few seconds…</p>}
      {slow && <p role="status">The server is waking up, this can take up to a minute…</p>}

      {message.status !== 'APPROVED' && message.status !== 'PENDING_AI' && (
        <div>
          {isDraft && (
            <>
              <button
                onClick={() => void run('save', (signal, onSlow) => saveReply(id, input, { signal, onSlow }))}
                disabled={disabled || replyInvalid}
              >
                {busy === 'save' ? 'Saving…' : 'Save'}
              </button>{' '}
              <button
                onClick={() => void run('approve', (signal, onSlow) => approveMessage(id, input, { signal, onSlow }))}
                disabled={disabled || replyInvalid}
              >
                {busy === 'approve' ? 'Approving…' : 'Approve'}
              </button>{' '}
            </>
          )}
          {canRegenerate && (
            <button onClick={handleRegenerate} disabled={disabled}>
              {busy === 'regenerate' ? 'Regenerating…' : 'Regenerate'}
            </button>
          )}
        </div>
      )}
    </>
  )
}
