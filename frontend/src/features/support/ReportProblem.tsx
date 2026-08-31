import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useLocation } from 'react-router-dom'
import { api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import { Alert, Field } from '@/components/ui'

/**
 * Reporting a problem with LearnX itself.
 *
 * Open to every role, from every screen. The people who hit a broken page are
 * mostly students, and routing them through their university's administrator —
 * who cannot fix the software either — is how a report stops existing.
 *
 * Nothing about the reporter is sent: the server reads that from the session,
 * so a report cannot be filed under somebody else's name. The screen it was
 * filed from goes along with it, because "it did not work" without a page is
 * not a report anybody can act on.
 */
export default function ReportProblem({ onClose }: { onClose: () => void }) {
  const location = useLocation()
  const { notify, reportError } = useToast()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')

  const send = useMutation({
    mutationFn: () =>
      api.post<{ message: string }>('/api/bugs/report', {
        title,
        description,
        pagePath: location.pathname + location.search,
      }),
    onSuccess: (result) => {
      notify(result.message, 'success')
      onClose()
    },
    onError: (error) => reportError(error),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    send.mutate()
  }

  return (
    <div
      className="modal-back"
      onClick={(event) => event.target === event.currentTarget && onClose()}
    >
      <div className="modal card" role="dialog" aria-label="Report a problem">
        <header className="card-head">
          <h2>Report a problem</h2>
          <button className="btn btn-secondary btn-sm" onClick={onClose}>
            Close
          </button>
        </header>

        <form onSubmit={submit} noValidate>
          <Alert kind="info">
            This goes to the team that maintains LearnX, not to your university. For a wrong
            class time or a missing result, ask your administrator instead.
          </Alert>

          <Field label="What went wrong?" htmlFor="bug-title">
            <input
              id="bug-title"
              value={title}
              maxLength={255}
              required
              placeholder="The routine screen is blank"
              onChange={(event) => setTitle(event.target.value)}
            />
          </Field>

          <Field label="What were you doing at the time?" htmlFor="bug-description">
            <textarea
              id="bug-description"
              rows={6}
              value={description}
              maxLength={4000}
              required
              placeholder="Opened the routine from the home screen and it stayed empty."
              onChange={(event) => setDescription(event.target.value)}
            />
          </Field>

          <p className="small muted">
            Sent with your name and the screen you are on (
            <span className="mono">{location.pathname}</span>).
          </p>

          <button className="btn" type="submit" disabled={send.isPending || !title || !description}>
            {send.isPending ? 'Sending…' : 'Send report'}
          </button>
        </form>
      </div>
    </div>
  )
}
