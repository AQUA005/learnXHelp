import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import type { GradeRecord } from '@/lib/types'
import { Card, EmptyState, Field, Loading, PageHeader } from '@/components/ui'

/** Recording and reviewing marks. */
export default function GradebookPage() {
  const [search, setSearch] = useState('')

  const grades = useQuery({
    queryKey: ['grades'],
    queryFn: () => api.get<GradeRecord[]>('/api/dashboard/all-grades'),
  })

  const needle = search.trim().toLowerCase()
  const visible = (grades.data ?? []).filter(
    (grade) =>
      !needle ||
      (grade.studentName ?? '').toLowerCase().includes(needle) ||
      (grade.studentUsername ?? '').toLowerCase().includes(needle) ||
      grade.courseName.toLowerCase().includes(needle) ||
      grade.assessmentName.toLowerCase().includes(needle),
  )

  return (
    <>
      <PageHeader title="Gradebook" description="Marks recorded for students in your university." />

      <RecordGradeForm />

      <Card
        title={grades.isLoading ? 'Recorded marks' : `${visible.length} record${visible.length === 1 ? '' : 's'}`}
        actions={
          <input
            placeholder="Search"
            aria-label="Search the gradebook"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        }
      >
        {grades.isLoading ? (
          <Loading rows={4} />
        ) : visible.length === 0 ? (
          <EmptyState title="No marks recorded" />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Student</th>
                  <th>Course</th>
                  <th>Assessment</th>
                  <th>Marks</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {visible.map((grade) => (
                  <tr key={grade.id}>
                    <td>
                      {grade.studentName}
                      <div className="small muted">{grade.studentUsername}</div>
                    </td>
                    <td>{grade.courseName}</td>
                    <td>{grade.assessmentName}</td>
                    <td className="mono">
                      {grade.marksObtained} / {grade.maxMarks}
                    </td>
                    <td>
                      <DeleteGradeButton id={grade.id} />
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

function RecordGradeForm() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({
    studentUsername: '',
    courseName: '',
    assessmentName: '',
    marksObtained: 0,
    maxMarks: 100,
  })

  const record = useMutation({
    mutationFn: () => api.post<GradeRecord>('/api/dashboard/grades', form),
    onSuccess: () => {
      notify('Mark recorded', 'success')
      setForm({ ...form, studentUsername: '', marksObtained: 0 })
      void queryClient.invalidateQueries({ queryKey: ['grades'] })
    },
    onError: (error) => reportError(error),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    record.mutate()
  }

  return (
    <Card
      title="Record a mark"
      actions={
        <button className="btn btn-secondary btn-sm" onClick={() => setOpen((v) => !v)}>
          {open ? 'Hide' : 'Add'}
        </button>
      }
    >
      {open && (
        <form onSubmit={submit}>
          <div className="grid grid-3">
            <Field label="Student username" htmlFor="g-student">
              <input
                id="g-student"
                required
                value={form.studentUsername}
                onChange={(e) => setForm({ ...form, studentUsername: e.target.value })}
              />
            </Field>
            <Field label="Course" htmlFor="g-course">
              <input
                id="g-course"
                required
                value={form.courseName}
                onChange={(e) => setForm({ ...form, courseName: e.target.value })}
              />
            </Field>
            <Field label="Assessment" htmlFor="g-assessment">
              <input
                id="g-assessment"
                required
                value={form.assessmentName}
                onChange={(e) => setForm({ ...form, assessmentName: e.target.value })}
              />
            </Field>
            <Field label="Marks obtained" htmlFor="g-obtained">
              <input
                id="g-obtained"
                type="number"
                min={0}
                step="0.5"
                required
                value={form.marksObtained}
                onChange={(e) => setForm({ ...form, marksObtained: Number(e.target.value) })}
              />
            </Field>
            <Field label="Out of" htmlFor="g-max">
              <input
                id="g-max"
                type="number"
                min={1}
                step="0.5"
                required
                value={form.maxMarks}
                onChange={(e) => setForm({ ...form, maxMarks: Number(e.target.value) })}
              />
            </Field>
          </div>
          <button className="btn" type="submit" disabled={record.isPending}>
            {record.isPending ? 'Saving…' : 'Record mark'}
          </button>
        </form>
      )}
    </Card>
  )
}

function DeleteGradeButton({ id }: { id: number }) {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const remove = useMutation({
    mutationFn: () => api.del(`/api/dashboard/grades/${id}`),
    onSuccess: () => {
      notify('Mark removed', 'success')
      void queryClient.invalidateQueries({ queryKey: ['grades'] })
    },
    onError: (error) => reportError(error),
  })

  return (
    <button className="btn btn-secondary btn-sm" onClick={() => remove.mutate()} disabled={remove.isPending}>
      Remove
    </button>
  )
}
