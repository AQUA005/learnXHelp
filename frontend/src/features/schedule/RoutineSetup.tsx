import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { Field } from '@/components/ui'
import type { RoutinePrefs, RoutineSource } from './routineData'

/**
 * Which section to read, and -- if nobody has configured one -- which sheet.
 *
 * The section is the everyday setting: a sheet lists every section in a
 * department and only one of them is yours. The sheet fields underneath are the
 * escape hatch, kept shut by default, for a department whose administrator has
 * not set a source yet. They are stored in this browser only, so nothing here
 * can change what a classmate sees.
 */
export default function RoutineSetup({
  prefs,
  sections,
  onSave,
  onClose,
}: {
  prefs: RoutinePrefs
  sections: string[]
  onSave: (prefs: RoutinePrefs) => void
  onClose: () => void
}) {
  const [section, setSection] = useState(prefs.section ?? '')
  const [advanced, setAdvanced] = useState(Boolean(prefs.sheet))
  const [sheet, setSheet] = useState(prefs.sheet ?? '')
  const [dayGids, setDayGids] = useState(prefs.dayGids ?? '')
  const [teacherGid, setTeacherGid] = useState(prefs.teacherGid ?? '')

  // What the department is set to, so the panel can say where the data is from.
  const source = useQuery({
    queryKey: ['routine-source'],
    queryFn: () => api.get<RoutineSource | null>('/api/routine/source'),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    onSave({
      section: section.trim() || undefined,
      sheet: advanced && sheet.trim() ? sheet.trim() : undefined,
      dayGids: advanced && dayGids.trim() ? dayGids.trim() : undefined,
      teacherGid: advanced && teacherGid.trim() ? teacherGid.trim() : undefined,
    })
  }

  return (
    <div className="modal-back" onClick={(event) => event.target === event.currentTarget && onClose()}>
      <div className="modal card" role="dialog" aria-label="Routine settings">
        <header className="card-head">
          <h2>Your routine</h2>
          <button className="btn btn-secondary btn-sm" onClick={onClose}>
            Close
          </button>
        </header>

        <form onSubmit={submit}>
          <Field label="Section" htmlFor="rs-section">
            {sections.length > 0 ? (
              <select
                id="rs-section"
                value={section}
                onChange={(event) => setSection(event.target.value)}
              >
                <option value="">Work it out from my account</option>
                {sections.map((option) => (
                  <option key={option} value={option}>
                    {option.toUpperCase()}
                  </option>
                ))}
              </select>
            ) : (
              <input
                id="rs-section"
                placeholder="e.g. 45(b)"
                value={section}
                onChange={(event) => setSection(event.target.value)}
              />
            )}
          </Field>
          <p className="small muted">
            {sections.length > 0
              ? `${sections.length} section${sections.length === 1 ? '' : 's'} listed in this sheet.`
              : 'Sections appear here once the sheet has been read.'}
          </p>

          {source.data && !prefs.sheet && (
            <p className="small muted">
              Reading{' '}
              {source.data.department ? `the ${source.data.department} sheet` : "your university's sheet"}
              {source.data.updatedBy ? `, set by ${source.data.updatedBy}` : ''}.
            </p>
          )}

          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => setAdvanced((value) => !value)}
            style={{ marginTop: '0.75rem' }}
          >
            {advanced ? 'Hide the sheet settings' : 'Use a different sheet'}
          </button>

          {advanced && (
            <div className="routine-advanced">
              <Field label="Google Sheet link or id" htmlFor="rs-sheet">
                <input
                  id="rs-sheet"
                  placeholder="https://docs.google.com/spreadsheets/d/…"
                  value={sheet}
                  onChange={(event) => setSheet(event.target.value)}
                />
              </Field>
              <Field label="Weekday tab ids" htmlFor="rs-gids">
                <input
                  id="rs-gids"
                  placeholder="e.g. 1738789421, 1122127138, …"
                  value={dayGids}
                  onChange={(event) => setDayGids(event.target.value)}
                />
              </Field>
              <Field label="Teacher tab id" htmlFor="rs-teacher">
                <input
                  id="rs-teacher"
                  placeholder="e.g. 50237967"
                  value={teacherGid}
                  onChange={(event) => setTeacherGid(event.target.value)}
                />
              </Field>
              <p className="small muted">
                Each weekday is a separate tab. A tab's id is the number after <b>#gid=</b> in its
                address. This is remembered in this browser only — to change it for everyone, ask an
                administrator.
              </p>
            </div>
          )}

          <div className="row row-end" style={{ marginTop: '1rem' }}>
            <button className="btn" type="submit">
              Save
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
