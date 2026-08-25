import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import { formatBytes } from '@/lib/format'
import type { StudyResource } from '@/lib/types'
import { Card, EmptyState, Loading, PageHeader } from '@/components/ui'

/** Notes waiting for a teacher to approve them. */
export default function ModerationPage() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

  const pending = useQuery({
    queryKey: ['resources', 'pending'],
    queryFn: () => api.get<StudyResource[]>('/api/resources/pending'),
  })

  const approve = useMutation({
    mutationFn: (id: number) => api.post(`/api/resources/${id}/approve`),
    onSuccess: () => {
      notify('Notes approved', 'success')
      void queryClient.invalidateQueries({ queryKey: ['resources'] })
    },
    onError: (error) => reportError(error),
  })

  const reject = useMutation({
    mutationFn: (id: number) => api.del(`/api/resources/${id}`),
    onSuccess: () => {
      notify('Notes rejected', 'success')
      void queryClient.invalidateQueries({ queryKey: ['resources'] })
    },
    onError: (error) => reportError(error),
  })

  const items = pending.data ?? []

  return (
    <>
      <PageHeader
        title="Note approvals"
        description="Material submitted by students, waiting to be published."
      />

      <Card title={`${items.length} awaiting review`}>
        {pending.isLoading ? (
          <Loading rows={3} />
        ) : items.length === 0 ? (
          <EmptyState title="Nothing to review" hint="Submitted notes appear here." />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Course</th>
                  <th>Submitted by</th>
                  <th>Size</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((note) => (
                  <tr key={note.id}>
                    <td>{note.title}</td>
                    <td>{note.courseName}</td>
                    <td>{note.uploadedBy?.fullName ?? 'Unknown'}</td>
                    <td className="mono">{note.fileSize ? formatBytes(note.fileSize) : 'Link'}</td>
                    <td>
                      <div className="row">
                        {note.storageKey && (
                          <a className="btn btn-secondary btn-sm" href={`/api/resources/download/${note.id}`}>
                            Preview
                          </a>
                        )}
                        <button
                          className="btn btn-sm"
                          onClick={() => approve.mutate(note.id)}
                          disabled={approve.isPending}
                        >
                          Approve
                        </button>
                        <button
                          className="btn btn-danger btn-sm"
                          onClick={() => reject.mutate(note.id)}
                          disabled={reject.isPending}
                        >
                          Reject
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  )
}
