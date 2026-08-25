import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { useToast } from '@/lib/toast'
import { formatDuration } from '@/lib/format'
import type { ExamDetail } from '@/lib/types'
import { Alert, Card, Loading } from '@/components/ui'

/** Where answers are kept between renders and reloads. */
type AnswerMap = Record<number, string>

function draftKey(examId: string): string {
  return `learnx.exam.${examId}.answers`
}

/**
 * Sitting an exam.
 *
 * Answers are held locally as they are typed and written to session storage, so
 * a reload or an accidental navigation does not lose the candidate's work. When
 * the timer runs out the paper is submitted automatically.
 */
export default function ExamRoomPage() {
  const { examId = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { notify, reportError } = useToast()

  const [answers, setAnswers] = useState<AnswerMap>({})
  const [remaining, setRemaining] = useState<number | null>(null)
  const submittedRef = useRef(false)

  const exam = useQuery({
    queryKey: ['exam', examId],
    queryFn: () => api.get<ExamDetail>(`/api/exams/${examId}`),
    enabled: Boolean(examId),
  })

  // Restore anything typed before a reload.
  useEffect(() => {
    const saved = sessionStorage.getItem(draftKey(examId))
    if (saved) {
      try {
        setAnswers(JSON.parse(saved) as AnswerMap)
      } catch {
        sessionStorage.removeItem(draftKey(examId))
      }
    }
  }, [examId])

  useEffect(() => {
    if (Object.keys(answers).length > 0) {
      sessionStorage.setItem(draftKey(examId), JSON.stringify(answers))
    }
  }, [answers, examId])

  const submit = useMutation({
    mutationFn: (payload: AnswerMap) =>
      api.post<{ score: number; maxMarks: number }>(
        `/api/exams/${examId}/submit`,
        Object.entries(payload).map(([questionId, answer]) => ({
          questionId: Number(questionId),
          answer,
        })),
      ),
    onSuccess: (result) => {
      sessionStorage.removeItem(draftKey(examId))
      notify(`Submitted. You scored ${result.score} of ${result.maxMarks}.`, 'success')
      // The exam list and the results both change on submission, so their
      // cached copies have to be dropped or the paper still looks unsat.
      void queryClient.invalidateQueries({ queryKey: ['exams'] })
      void queryClient.invalidateQueries({ queryKey: ['exam', examId] })
      void queryClient.invalidateQueries({ queryKey: ['performance'] })
      navigate('/exams', { replace: true })
    },
    onError: (error) => {
      submittedRef.current = false
      reportError(error)
    },
  })

  const finish = useCallback(
    (automatic: boolean) => {
      if (submittedRef.current) return
      submittedRef.current = true
      if (automatic) {
        notify('Time is up. Submitting your answers.', 'info')
      }
      submit.mutate(answers)
    },
    [answers, notify, submit],
  )

  // The clock runs from the exam's own duration, and stops the paper at zero.
  const durationSeconds = useMemo(
    () => (exam.data ? exam.data.durationMinutes * 60 : null),
    [exam.data],
  )

  useEffect(() => {
    if (durationSeconds == null) return
    setRemaining(durationSeconds)
    const started = Date.now()
    const timer = window.setInterval(() => {
      const left = durationSeconds - Math.floor((Date.now() - started) / 1000)
      setRemaining(left)
      if (left <= 0) {
        window.clearInterval(timer)
        finish(true)
      }
    }, 1000)
    return () => window.clearInterval(timer)
    // finish is intentionally excluded: it changes as answers do, and
    // restarting the clock on every keystroke would give unlimited time.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [durationSeconds])

  if (exam.isLoading) {
    return (
      <div className="page">
        <Loading rows={5} label="Loading the exam" />
      </div>
    )
  }

  if (exam.isError || !exam.data) {
    return (
      <div className="page">
        <Alert kind="error">This exam could not be opened.</Alert>
        <button className="btn" onClick={() => navigate('/exams')}>
          Back to exams
        </button>
      </div>
    )
  }

  if (exam.data.alreadySubmitted) {
    return (
      <div className="page">
        <Card title={exam.data.title}>
          <Alert kind="info">
            You have already submitted this exam. You scored {exam.data.previousScore ?? 0}.
          </Alert>
          <button className="btn" onClick={() => navigate('/exams')}>
            Back to exams
          </button>
        </Card>
      </div>
    )
  }

  const answered = Object.values(answers).filter((value) => value.trim() !== '').length
  const urgent = remaining != null && remaining <= 60

  return (
    <div className="page">
      <div className={urgent ? 'exam-timer urgent' : 'exam-timer'} role="timer" aria-live="off">
        <div>
          <strong>{exam.data.title}</strong>
          <div className="small muted">
            {answered} of {exam.data.questions.length} answered
          </div>
        </div>
        <div className="mono" style={{ fontSize: '1.3rem', fontWeight: 700 }}>
          {remaining == null ? '—' : formatDuration(remaining)}
        </div>
      </div>

      {exam.data.questions.map((question, index) => (
        <div className="question" key={question.id}>
          <div className="row" style={{ marginBottom: '0.4rem' }}>
            <strong>Question {index + 1}</strong>
            <span className="spacer" />
            <span className="small muted">
              {question.points} mark{question.points === 1 ? '' : 's'}
            </span>
          </div>

          <p style={{ whiteSpace: 'pre-wrap' }}>{question.questionText}</p>

          {question.questionType === 'MCQ' ? (
            (question.options ?? '')
              .split(';')
              .map((option) => option.trim())
              .filter(Boolean)
              .map((option) => (
                <label className="option" key={option}>
                  <input
                    type="radio"
                    name={`question-${question.id}`}
                    value={option}
                    checked={answers[question.id] === option}
                    onChange={() => setAnswers((current) => ({ ...current, [question.id]: option }))}
                  />
                  {option}
                </label>
              ))
          ) : (
            <input
              aria-label={`Answer to question ${index + 1}`}
              value={answers[question.id] ?? ''}
              onChange={(e) =>
                setAnswers((current) => ({ ...current, [question.id]: e.target.value }))
              }
            />
          )}
        </div>
      ))}

      <div className="row row-end">
        <button className="btn btn-secondary" onClick={() => navigate('/exams')} disabled={submit.isPending}>
          Leave without submitting
        </button>
        <button className="btn" onClick={() => finish(false)} disabled={submit.isPending}>
          {submit.isPending ? 'Submitting…' : 'Submit answers'}
        </button>
      </div>
    </div>
  )
}
