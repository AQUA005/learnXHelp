import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import { formatDateTime } from '@/lib/format'
import type { MetadataOption } from '@/lib/types'
import { Card, EmptyState, Field, Loading } from '@/components/ui'
import type { RoutineSource } from '@/features/schedule/routineData'

/**
 * Where each department's routine is published.
 *
 * A department that has no sheet of its own falls back to the one saved with
 * no department against it, which is why that is offered as a first-class
 * choice rather than hidden: most universities want one sheet and are done.
 */
export default function RoutineSources() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

  const sources = useQuery({
    queryKey: ['routine-sources'],
    queryFn: () => api.get<RoutineSource[]>('/api/routine/sources'),
  })

  // The departments this university actually has, so a sheet cannot be saved
  // against a department nobody belongs to.
  const departments = useQuery({
    queryKey: ['metadata'],
    queryFn: () => api.get<MetadataOption[]>('/api/metadata'),
  })

  const [department, setDepartment] = useState('')
  const [sheet, setSheet] = useState('')
  const [dayGids, setDayGids] = useState('')
  const [teacherGid, setTeacherGid] = useState('')
  const [blockHints, setBlockHints] = useState('')

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      api.put<RoutineSource>('/api/routine/sources', body),
    onSuccess: () => {
      notify('Routine sheet saved', 'success')
      void queryClient.invalidateQueries({ queryKey: ['routine-sources'] })
      void queryClient.invalidateQueries({ queryKey: ['live-routine'] })
    },
    onError: (error) => reportError(error),
  })

  const remove = useMutation({
    mutationFn: (id: number) => api.del(`/api/routine/sources/${id}`),
    onSuccess: () => {
      notify('Routine sheet removed', 'success')
      void queryClient.invalidateQueries({ queryKey: ['routine-sources'] })
    },
    onError: (error) => reportError(error),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate({ department, sheet, dayGids, teacherGid, blockHints: blockHints || null })
  }

  function edit(source: RoutineSource) {
    setDepartment(source.department)
    setSheet(source.sheetId)
    setDayGids(source.dayGids)
    setTeacherGid(source.teacherGid ?? '')
    setBlockHints(source.blockHints ?? '')
  }

  const rows = sources.data ?? []

  return (
    <>
      <Card title="Where each routine is published">
        {sources.isLoading ? (
          <Loading rows={3} />
        ) : rows.length === 0 ? (
          <EmptyState
            icon="calendar"
            title="No routine sheet set"
            hint="Save one below and the routine screen starts reading it."
          />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Department</th>
                  <th>Sheet</th>
                  <th>Weekday tabs</th>
                  <th>Last changed</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map((source) => (
                  <tr key={source.id}>
                    <td>{source.department || 'Every department'}</td>
                    <td>
                      <a href={source.sheetUrl} target="_blank" rel="noopener noreferrer">
                        Open ↗
                      </a>
                    </td>
                    <td className="mono small">{source.dayGids}</td>
                    <td className="small muted">
                      {source.updatedAt ? formatDateTime(source.updatedAt) : '—'}
                      {source.updatedBy ? ` · ${source.updatedBy}` : ''}
                    </td>
                    <td>
                      <div className="row">
                        <button className="btn btn-secondary btn-sm" onClick={() => edit(source)}>
                          Edit
                        </button>
                        <button
                          className="btn btn-danger btn-sm"
                          disabled={remove.isPending}
                          onClick={() => remove.mutate(source.id)}
                        >
                          Remove
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

      <Card title="Save a routine sheet">
        <form onSubmit={submit}>
          <div className="grid grid-2">
            <Field label="Department" htmlFor="rs-dept">
              <select
                id="rs-dept"
                value={department}
                onChange={(event) => setDepartment(event.target.value)}
              >
                <option value="">Every department (the fallback)</option>
                {(departments.data ?? [])
                  .filter((option) => option.type === 'DEPARTMENT')
                  .map((option) => (
                  <option key={option.id} value={option.value}>
                    {option.value}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Google Sheet link or id" htmlFor="rs-sheet-id">
              <input
                id="rs-sheet-id"
                required
                placeholder="https://docs.google.com/spreadsheets/d/…"
                value={sheet}
                onChange={(event) => setSheet(event.target.value)}
              />
            </Field>
            <Field label="Weekday tab ids" htmlFor="rs-day-gids">
              <input
                id="rs-day-gids"
                required
                placeholder="1738789421, 1122127138, …"
                value={dayGids}
                onChange={(event) => setDayGids(event.target.value)}
              />
            </Field>
            <Field label="Teacher tab id" htmlFor="rs-teacher-gid">
              <input
                id="rs-teacher-gid"
                placeholder="50237967"
                value={teacherGid}
                onChange={(event) => setTeacherGid(event.target.value)}
              />
            </Field>
          </div>

          <Field label="Merged-cell hints (optional)" htmlFor="rs-blocks">
            <textarea
              id="rs-blocks"
              rows={3}
              placeholder='{"45(b)":{"SUNDAY":{"6":2}}}'
              value={blockHints}
              onChange={(event) => setBlockHints(event.target.value)}
            />
          </Field>
          <p className="small muted">
            A tab's id is the number after <b>#gid=</b> in its address, and each weekday is a
            separate tab. The hints are only needed where a sheet merges cells for a class that runs
            for more than one period: a merged cell arrives empty, which otherwise reads as a free
            period. Keyed by section, then weekday, then the column's position.
          </p>

          <button className="btn" type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save sheet'}
          </button>
        </form>
      </Card>
    </>
  )
}
