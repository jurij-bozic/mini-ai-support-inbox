import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { ApiError, type FieldErrors } from '../api/errors'
import { createMessage } from '../api/messages'

export const MAX_CONTENT = 5000
export const MAX_NAME = 200
export const MAX_EMAIL = 320

const FIELDS = ['content', 'customerName', 'customerEmail'] as const

function validate(content: string, name: string, email: string): FieldErrors {
  const errors: FieldErrors = {}
  if (content.trim() === '') errors.content = 'Message content is required.'
  else if (content.length > MAX_CONTENT) errors.content = `Content must be at most ${MAX_CONTENT} characters.`
  if (name.length > MAX_NAME) errors.customerName = `Name must be at most ${MAX_NAME} characters.`
  if (email.length > MAX_EMAIL) errors.customerEmail = `Email must be at most ${MAX_EMAIL} characters.`
  else if (email !== '' && !/^[^\s@]+@[^\s@]+$/.test(email)) errors.customerEmail = 'Enter a valid email address.'
  return errors
}

function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <span id={id} role="alert">
      {message}
    </span>
  ) : null
}

export function NewMessagePage() {
  const navigate = useNavigate()
  const [content, setContent] = useState('')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [pending, setPending] = useState(false)
  const [slow, setSlow] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [error, setError] = useState<string | null>(null)
  const controllerRef = useRef<AbortController | null>(null)

  // Cancel an in-flight request when the user leaves the page.
  useEffect(() => () => controllerRef.current?.abort(), [])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    const clientErrors = validate(content, name.trim(), email.trim())
    setFieldErrors(clientErrors)
    if (Object.keys(clientErrors).length > 0) return

    const controller = new AbortController()
    controllerRef.current = controller
    setPending(true)
    setSlow(false)
    try {
      const created = await createMessage(
        { content, customerName: name.trim() || null, customerEmail: email.trim() || null },
        { signal: controller.signal, onSlow: () => setSlow(true) },
      )
      navigate(`/messages/${created.id}`)
    } catch (e) {
      if (controller.signal.aborted) return
      if (e instanceof ApiError && e.status === 400 && Object.keys(e.fieldErrors).length > 0) {
        const known: FieldErrors = {}
        const unknown: string[] = []
        for (const [field, message] of Object.entries(e.fieldErrors)) {
          if ((FIELDS as readonly string[]).includes(field)) known[field] = message
          else unknown.push(message)
        }
        setFieldErrors(known)
        if (unknown.length > 0) setError(unknown.join(' '))
      } else {
        setError(e instanceof Error ? e.message : 'Could not create the message.')
      }
      setPending(false)
    }
  }

  return (
    <>
      <h2>New message</h2>
      <form onSubmit={handleSubmit} noValidate>
        <fieldset disabled={pending}>
          <label>
            Message
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows={8}
              aria-invalid={Boolean(fieldErrors.content)}
              aria-describedby="content-error"
            />
          </label>
          <span aria-live="polite">
            {content.length} / {MAX_CONTENT}
          </span>
          <FieldError id="content-error" message={fieldErrors.content} />
          <label>
            Customer name (optional)
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              aria-invalid={Boolean(fieldErrors.customerName)}
              aria-describedby="name-error"
            />
          </label>
          <FieldError id="name-error" message={fieldErrors.customerName} />
          <label>
            Customer email (optional)
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-invalid={Boolean(fieldErrors.customerEmail)}
              aria-describedby="email-error"
            />
          </label>
          <FieldError id="email-error" message={fieldErrors.customerEmail} />
          <button type="submit">{pending ? 'Classifying and drafting…' : 'Submit'}</button>
        </fieldset>
        {pending && <p role="status">The AI is classifying the message and writing a draft, this can take a few seconds…</p>}
        {slow && <p role="status">The server is waking up, this can take up to a minute…</p>}
        {error && <p role="alert">{error}</p>}
      </form>
    </>
  )
}
