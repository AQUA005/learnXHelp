import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { hasAtLeast, useCurrentUser } from '@/lib/session'
import { useToast } from '@/lib/toast'
import { formatDateTime } from '@/lib/format'
import type { Announcement } from '@/lib/types'
import { Badge, Card, EmptyState, Field, Loading, PageHeader } from '@/components/ui'

/** Notices to the class or the whole university. */
export default function AnnouncementsPage() {
  const user = useCurrentUser()
  const canPost = hasAtLeast(user.role, 'CR')

  const announcements = useQuery({
    queryKey: ['announcements'],
    queryFn: () => api.get<Announcement[]>('/api/announcements'),
  })

  const items = announcements.data ?? []

  return (
    <>
      <PageHeader title="Announcements" description="Notices from your class representative and teachers." />

      {canPost && <ComposeForm canAddressUniversity={hasAtLeast(user.role, 'TEACHER')} />}

      {announcements.isLoading ? (
        <Card>
          <Loading rows={4} />
        </Card>
      ) : items.length === 0 ? (
        <Card>
          <EmptyState title="No announcements" hint="Anything posted for your class appears here." />
        </Card>
      ) : (
        items.map((item) => <AnnouncementCard key={item.id} item={item} canRemove={canPost} />)
      )}
    </>
  )
}

function AnnouncementCard({ item, canRemove }: { item: Announcement; canRemove: boolean }) {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

  const remove = useMutation({
    mutationFn: () => api.del(`/api/announcements/${item.id}`),
    onSuccess: () => {
      notify('Announcement removed', 'success')
      void queryClient.invalidateQueries({ queryKey: ['announcements'] })
    },
    onError: (error) => reportError(error),
  })

  return (
    <Card
      title={
        <div>
          <h2>{item.title}</h2>
          <div className="small muted">
            {item.createdBy} · {formatDateTime(item.createdAt)}{' '}
            {item.className ? <Badge>{item.className}</Badge> : <Badge kind="accent">Everyone</Badge>}
          </div>
        </div>
      }
      actions={
        canRemove ? (
          <button className="btn btn-secondary btn-sm" onClick={() => remove.mutate()} disabled={remove.isPending}>
            Remove
          </button>
        ) : undefined
      }
    >
      {/* Rendered as text, never as markup. */}
      <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{item.content}</p>
    </Card>
  )
}

function ComposeForm({ canAddressUniversity }: { canAddressUniversity: boolean }) {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [global, setGlobal] = useState(false)

  const post = useMutation({
    mutationFn: () => api.post<Announcement>('/api/announcements', { title, content, global }),
    onSuccess: () => {
      notify('Announcement published', 'success')
      setTitle('')
      setContent('')
      setOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['announcements'] })
    },
    onError: (error) => reportError(error),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    post.mutate()
  }

  return (
    <Card
      title="Post an announcement"
      actions={
        <button className="btn btn-secondary btn-sm" onClick={() => setOpen((v) => !v)}>
          {open ? 'Hide' : 'Compose'}
        </button>
      }
    >
      {open && (
        <form onSubmit={submit}>
          <Field label="Title" htmlFor="an-title">
            <input id="an-title" required value={title} onChange={(e) => setTitle(e.target.value)} />
          </Field>
          <Field label="Message" htmlFor="an-content">
            <textarea id="an-content" required value={content} onChange={(e) => setContent(e.target.value)} />
          </Field>
          {canAddressUniversity && (
            <div className="option">
              <input
                id="an-global"
                type="checkbox"
                checked={global}
                onChange={(e) => setGlobal(e.target.checked)}
              />
              <label htmlFor="an-global" style={{ margin: 0 }}>
                Send to everyone, not just one class
              </label>
            </div>
          )}
          <button className="btn" type="submit" disabled={post.isPending}>
            {post.isPending ? 'Publishing…' : 'Publish'}
          </button>
        </form>
      )}
    </Card>
  )
}
