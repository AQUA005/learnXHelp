import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, request } from '@/lib/api'
import { useCurrentUser } from '@/lib/session'
import { useToast } from '@/lib/toast'
import { formatBytes } from '@/lib/format'
import type { StudyResource } from '@/lib/types'
import { Badge, Card, EmptyState, Field, Loading, PageHeader } from '@/components/ui'

/** Shared study material for the class. */
export default function NotesPage() {
  const user = useCurrentUser()
  const [search, setSearch] = useState('')
  const [course, setCourse] = useState('')

  const notes = useQuery({
    queryKey: ['resources', 'approved'],
    queryFn: () => api.get<StudyResource[]>('/api/resources/approved'),
  })

  const courses = useMemo(() => {
    const names = new Set((notes.data ?? []).map((n) => n.courseName).filter(Boolean))
    return Array.from(names).sort()
  }, [notes.data])

  const visible = (notes.data ?? []).filter((note) => {
    const matchesCourse = !course || note.courseName === course
    const needle = search.trim().toLowerCase()
    const matchesSearch =
      !needle ||
      note.title.toLowerCase().includes(needle) ||
      note.courseName.toLowerCase().includes(needle) ||
      (note.examTags ?? '').toLowerCase().includes(needle)
    return matchesCourse && matchesSearch
  })

  return (
    <>
      <PageHeader
        title="Notes library"
        description="Material shared by your class and teachers."
      />

      <UploadForm />

      <Card
        title={notes.isLoading ? 'Shared notes' : `${visible.length} item${visible.length === 1 ? '' : 's'}`}
        actions={
          <div className="row">
            <input
              placeholder="Search notes"
              value={search}
              aria-label="Search notes"
              onChange={(e) => setSearch(e.target.value)}
            />
            <select value={course} onChange={(e) => setCourse(e.target.value)} aria-label="Filter by course">
              <option value="">All courses</option>
              {courses.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </div>
        }
      >
        {notes.isLoading ? (
          <Loading rows={4} />
        ) : visible.length === 0 ? (
          <EmptyState
            title="Nothing here yet"
            hint="Upload the first set of notes for your class."
          />
        ) : (
          <div className="grid grid-2">
            {visible.map((note) => (
              <NoteCard key={note.id} note={note} canDelete={note.uploadedBy?.id === user.id} />
            ))}
          </div>
        )}
      </Card>
    </>
  )
}

function NoteCard({ note, canDelete }: { note: StudyResource; canDelete: boolean }) {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

  const react = useMutation({
    mutationFn: (type: 'LIKE' | 'DISLIKE') =>
      note.userReaction === type
        ? api.del(`/api/resources/${note.id}/react`)
        : api.post(`/api/resources/${note.id}/react?type=${type}`),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['resources'] }),
    onError: (error) => reportError(error),
  })

  const remove = useMutation({
    mutationFn: () => api.del(`/api/resources/${note.id}`),
    onSuccess: () => {
      notify('Note removed', 'success')
      void queryClient.invalidateQueries({ queryKey: ['resources'] })
    },
    onError: (error) => reportError(error),
  })

  return (
    <article className="card">
      <h3>{note.title}</h3>
      <div className="small muted" style={{ marginBottom: '0.5rem' }}>
        {note.courseName}
        {note.uploadedBy ? ` · ${note.uploadedBy.fullName}` : ''}
        {note.fileSize ? ` · ${formatBytes(note.fileSize)}` : ''}
      </div>

      {note.examTags && (
        <div style={{ marginBottom: '0.5rem' }}>
          <Badge kind="accent">{note.examTags}</Badge>
        </div>
      )}

      <div className="row">
        {note.storageKey && (
          <a className="btn btn-sm" href={`/api/resources/download/${note.id}`}>
            Download
          </a>
        )}
        {note.driveLink && (
          <a
            className="btn btn-secondary btn-sm"
            href={note.driveLink}
            target="_blank"
            rel="noreferrer noopener"
          >
            Open link
          </a>
        )}
        <span className="spacer" />
        <button
          className={note.userReaction === 'LIKE' ? 'btn btn-sm' : 'btn btn-secondary btn-sm'}
          onClick={() => react.mutate('LIKE')}
          disabled={react.isPending}
          aria-label="Mark as helpful"
        >
          Helpful {note.likesCount > 0 && <span className="mono">{note.likesCount}</span>}
        </button>
        <button
          className={note.userReaction === 'DISLIKE' ? 'btn btn-sm' : 'btn btn-secondary btn-sm'}
          onClick={() => react.mutate('DISLIKE')}
          disabled={react.isPending}
          aria-label="Mark as not helpful"
        >
          Not helpful {note.dislikesCount > 0 && <span className="mono">{note.dislikesCount}</span>}
        </button>
        {canDelete && (
          <button className="btn btn-danger btn-sm" onClick={() => remove.mutate()} disabled={remove.isPending}>
            Delete
          </button>
        )}
      </div>
    </article>
  )
}

function UploadForm() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [courseName, setCourseName] = useState('')
  const [examTags, setExamTags] = useState('')
  const [driveLink, setDriveLink] = useState('')
  const [file, setFile] = useState<File | null>(null)

  const upload = useMutation({
    mutationFn: async () => {
      const body = new FormData()
      body.append('title', title)
      body.append('courseName', courseName)
      if (examTags) body.append('examTags', examTags)
      if (driveLink) body.append('driveLink', driveLink)
      if (file) body.append('file', file)
      return request<StudyResource>('/api/resources/upload', { method: 'POST', formData: body })
    },
    onSuccess: (saved) => {
      notify(
        saved.approved ? 'Notes published' : 'Notes submitted for approval',
        'success',
      )
      setTitle('')
      setCourseName('')
      setExamTags('')
      setDriveLink('')
      setFile(null)
      setOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['resources'] })
    },
    onError: (error) => reportError(error),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!file && !driveLink.trim()) {
      notify('Attach a file or provide a link', 'error')
      return
    }
    upload.mutate()
  }

  return (
    <Card
      title="Share notes"
      actions={
        <button className="btn btn-secondary btn-sm" onClick={() => setOpen((v) => !v)}>
          {open ? 'Hide' : 'Upload'}
        </button>
      }
    >
      {open && (
        <form onSubmit={submit}>
          <div className="grid grid-2">
            <Field label="Title" htmlFor="note-title">
              <input id="note-title" required value={title} onChange={(e) => setTitle(e.target.value)} />
            </Field>
            <Field label="Course" htmlFor="note-course">
              <input
                id="note-course"
                required
                value={courseName}
                onChange={(e) => setCourseName(e.target.value)}
              />
            </Field>
            <Field label="Tag (optional)" htmlFor="note-tags">
              <input
                id="note-tags"
                placeholder="CT1, Midterm, Final"
                value={examTags}
                onChange={(e) => setExamTags(e.target.value)}
              />
            </Field>
            <Field label="Link instead of a file (optional)" htmlFor="note-link">
              <input
                id="note-link"
                type="url"
                value={driveLink}
                onChange={(e) => setDriveLink(e.target.value)}
              />
            </Field>
          </div>

          <Field label="File" htmlFor="note-file">
            <input
              id="note-file"
              type="file"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </Field>
          <p className="small muted">
            Documents, slides, spreadsheets, images and archives up to 50 MB.
          </p>

          <button className="btn" type="submit" disabled={upload.isPending}>
            {upload.isPending ? 'Uploading…' : 'Share notes'}
          </button>
        </form>
      )}
    </Card>
  )
}
