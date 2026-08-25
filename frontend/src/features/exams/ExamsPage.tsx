import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { hasAtLeast, useCurrentUser } from '@/lib/session'
import { useToast } from '@/lib/toast'
import { formatDateTime } from '@/lib/format'
import type { ExamSummary } from '@/lib/types'
import { Badge, Card, EmptyState, Field, Loading, PageHeader } from '@/components/ui'

type Availability = 'open' | 'upcoming' | 'closed'

function availabilityOf(exam: ExamSummary, now = Date.now()): Availability {
  const start = exam.startTime ? new Date(exam.startTime).getTime() : null
  const end = exam.endTime ? new Date(exam.endTime).getTime() : null
  if (start && now < start) return 'upcoming'
  if (end && now > end) return 'closed'
  return 'open'
}

export default function ExamsPage() {
  const user = useCurrentUser()
  const canAuthor = hasAtLeast(user.role, 'TEACHER')

  const exams = useQuery({
    queryKey: ['exams'],
    queryFn: () => api.get<ExamSummary[]>('/api/exams'),
  })

  const items = (exams.data ?? []).slice().sort((a, b) => {
    const order = { open: 0, upcoming: 1, closed: 2 } as const
    return order[availabilityOf(a)] - order[availabilityOf(b)]
  })

  return (
    <>
      <PageHeader title="Exams" description={canAuthor ? 'Set quizzes and review results.' : 'Quizzes set for your class.'} />

      {canAuthor && <ExamCreator />}

      <Card title={exams.isLoading ? 'Your exams' : `${items.length} exam${items.length === 1 ? '' : 's'}`}>
        {exams.isLoading ? (
          <Loading rows={3} />
        ) : items.length === 0 ? (
          <EmptyState title="No exams yet" hint="Quizzes set for your class appear here." />
        ) : (
          <div className="grid grid-2">
            {items.map((exam) => (
              <ExamCard key={exam.id} exam={exam} canAuthor={canAuthor} />
            ))}
          </div>
        )}
      </Card>
    </>
  )
}

function ExamCard({ exam, canAuthor }: { exam: ExamSummary; canAuthor: boolean }) {
  const availability = availabilityOf(exam)

  return (
    <article className="card">
      <div className="card-head">
        <h3>{exam.title}</h3>
        {availability === 'open' && <Badge kind="success">Open</Badge>}
        {availability === 'upcoming' && <Badge kind="warning">Not started</Badge>}
        {availability === 'closed' && <Badge>Closed</Badge>}
      </div>

      {exam.description && <p className="small">{exam.description}</p>}

      <div className="small muted" style={{ marginBottom: '0.6rem' }}>
        {exam.durationMinutes} minutes · set by {exam.teacherName}
        <br />
        {exam.startTime ? `Opens ${formatDateTime(exam.startTime)}` : 'No start time'}
        {exam.endTime ? ` · closes ${formatDateTime(exam.endTime)}` : ''}
      </div>

      <div className="row">
        {exam.alreadySubmitted ? (
          <Badge kind="accent">Submitted · {exam.score ?? 0} marks</Badge>
        ) : availability === 'open' ? (
          <Link className="btn btn-sm" to={`/exams/${exam.id}`}>
            Start exam
          </Link>
        ) : (
          <button className="btn btn-sm" disabled>
            {availability === 'upcoming' ? 'Not open yet' : 'Closed'}
          </button>
        )}
        {canAuthor && <SubmissionsLink examId={exam.id} />}
      </div>
    </article>
  )
}

function SubmissionsLink({ examId }: { examId: number }) {
  const [open, setOpen] = useState(false)
  const submissions = useQuery({
    queryKey: ['exam-submissions', examId],
    queryFn: () =>
      api.get<
        { id: number; studentName: string; studentUsername: string; submittedAt: string; score: number }[]
      >(`/api/exams/${examId}/submissions`),
    enabled: open,
  })

  return (
    <>
      <button className="btn btn-secondary btn-sm" onClick={() => setOpen((v) => !v)}>
        {open ? 'Hide results' : 'Results'}
      </button>
      {open && (
        <div style={{ width: '100%', marginTop: '0.6rem' }}>
          {submissions.isLoading ? (
            <Loading rows={2} />
          ) : (submissions.data ?? []).length === 0 ? (
            <p className="small muted">Nobody has submitted yet.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Student</th>
                    <th>Submitted</th>
                    <th>Score</th>
                  </tr>
                </thead>
                <tbody>
                  {(submissions.data ?? []).map((row) => (
                    <tr key={row.id}>
                      <td>{row.studentName}</td>
                      <td className="small">{formatDateTime(row.submittedAt)}</td>
                      <td className="mono">{row.score}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </>
  )
}

type DraftQuestion = {
  questionText: string
  questionType: 'MCQ' | 'SHORT_ANSWER'
  points: number
  options: string
  correctAnswer: string
}

const BLANK_QUESTION: DraftQuestion = {
  questionText: '',
  questionType: 'MCQ',
  points: 1,
  options: '',
  correctAnswer: '',
}

function ExamCreator() {
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [durationMinutes, setDurationMinutes] = useState(20)
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [questions, setQuestions] = useState<DraftQuestion[]>([{ ...BLANK_QUESTION }])

  const create = useMutation({
    mutationFn: () =>
      api.post<{ examId: number }>('/api/exams/create', {
        title,
        description,
        durationMinutes,
        // The server expects seconds in the timestamp.
        startTime: `${startTime}:00`,
        endTime: `${endTime}:00`,
        questions,
      }),
    onSuccess: () => {
      notify('Exam published', 'success')
      setTitle('')
      setDescription('')
      setQuestions([{ ...BLANK_QUESTION }])
      setOpen(false)
      void queryClient.invalidateQueries({ queryKey: ['exams'] })
    },
    onError: (error) => reportError(error),
  })

  function updateQuestion(index: number, changes: Partial<DraftQuestion>) {
    setQuestions((current) =>
      current.map((question, i) => (i === index ? { ...question, ...changes } : question)),
    )
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return (
    <Card
      title="Set an exam"
      actions={
        <button className="btn btn-secondary btn-sm" onClick={() => setOpen((v) => !v)}>
          {open ? 'Hide' : 'Create'}
        </button>
      }
    >
      {open && (
        <form onSubmit={submit}>
          <div className="grid grid-2">
            <Field label="Title" htmlFor="ex-title">
              <input id="ex-title" required value={title} onChange={(e) => setTitle(e.target.value)} />
            </Field>
            <Field label="Duration (minutes)" htmlFor="ex-duration">
              <input
                id="ex-duration"
                type="number"
                min={1}
                required
                value={durationMinutes}
                onChange={(e) => setDurationMinutes(Number(e.target.value))}
              />
            </Field>
            <Field label="Opens" htmlFor="ex-start">
              <input
                id="ex-start"
                type="datetime-local"
                required
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
              />
            </Field>
            <Field label="Closes" htmlFor="ex-end">
              <input
                id="ex-end"
                type="datetime-local"
                required
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
              />
            </Field>
          </div>

          <Field label="Description" htmlFor="ex-desc">
            <textarea id="ex-desc" value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>

          <h3>Questions</h3>
          {questions.map((question, index) => (
            <div className="question" key={index}>
              <Field label={`Question ${index + 1}`} htmlFor={`q-text-${index}`}>
                <textarea
                  id={`q-text-${index}`}
                  required
                  value={question.questionText}
                  onChange={(e) => updateQuestion(index, { questionText: e.target.value })}
                />
              </Field>
              <div className="grid grid-3">
                <Field label="Type" htmlFor={`q-type-${index}`}>
                  <select
                    id={`q-type-${index}`}
                    value={question.questionType}
                    onChange={(e) =>
                      updateQuestion(index, {
                        questionType: e.target.value as DraftQuestion['questionType'],
                      })
                    }
                  >
                    <option value="MCQ">Multiple choice</option>
                    <option value="SHORT_ANSWER">Short answer</option>
                  </select>
                </Field>
                <Field label="Marks" htmlFor={`q-points-${index}`}>
                  <input
                    id={`q-points-${index}`}
                    type="number"
                    min={0}
                    value={question.points}
                    onChange={(e) => updateQuestion(index, { points: Number(e.target.value) })}
                  />
                </Field>
                <Field label="Correct answer" htmlFor={`q-answer-${index}`}>
                  <input
                    id={`q-answer-${index}`}
                    required
                    value={question.correctAnswer}
                    onChange={(e) => updateQuestion(index, { correctAnswer: e.target.value })}
                  />
                </Field>
              </div>
              {question.questionType === 'MCQ' && (
                <Field label="Options, separated by semicolons" htmlFor={`q-options-${index}`}>
                  <input
                    id={`q-options-${index}`}
                    placeholder="Option A;Option B;Option C"
                    value={question.options}
                    onChange={(e) => updateQuestion(index, { options: e.target.value })}
                  />
                </Field>
              )}
              {questions.length > 1 && (
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => setQuestions((c) => c.filter((_, i) => i !== index))}
                >
                  Remove question
                </button>
              )}
            </div>
          ))}

          <div className="row">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setQuestions((c) => [...c, { ...BLANK_QUESTION }])}
            >
              Add question
            </button>
            <span className="spacer" />
            <button className="btn" type="submit" disabled={create.isPending}>
              {create.isPending ? 'Publishing…' : 'Publish exam'}
            </button>
          </div>
        </form>
      )}
    </Card>
  )
}
