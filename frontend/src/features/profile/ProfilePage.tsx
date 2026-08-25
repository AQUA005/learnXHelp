import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { useCurrentUser, useSession } from '@/lib/session'
import { useToast } from '@/lib/toast'
import { Alert, Card, Field, PageHeader, initialsOf } from '@/components/ui'

/** Your own details. Academic fields need an administrator to approve them. */
export default function ProfilePage() {
  const user = useCurrentUser()
  const { patchUser, refresh } = useSession()
  const { notify, reportError } = useToast()

  const [form, setForm] = useState({
    fullName: user.fullName,
    email: user.email,
    idNo: user.idNo ?? '',
    department: user.department ?? '',
    batch: user.batch ?? '',
    semester: user.semester ?? '',
    section: user.section ?? '',
    designation: user.designation ?? '',
  })
  const [avatar, setAvatar] = useState<string | null>(null)
  const [avatarError, setAvatarError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () =>
      api.post<{ message: string; profilePicUrl: string | null }>('/api/profile/update', {
        ...form,
        profilePicUrl: avatar,
      }),
    onSuccess: async (result) => {
      notify(result.message, 'success')
      if (result.profilePicUrl) {
        // Bust the cache so the new picture shows immediately.
        patchUser({ profilePicUrl: `${result.profilePicUrl}?v=${Date.now()}` })
      }
      setAvatar(null)
      await refresh()
    },
    onError: (error) => reportError(error),
  })

  function pickAvatar(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    setAvatarError(null)
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setAvatarError('Choose an image file.')
      return
    }
    if (file.size > 2 * 1024 * 1024) {
      setAvatarError('Images must be smaller than 2 MB.')
      return
    }
    const reader = new FileReader()
    reader.onload = () => setAvatar(typeof reader.result === 'string' ? reader.result : null)
    reader.onerror = () => setAvatarError('That image could not be read.')
    reader.readAsDataURL(file)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    save.mutate()
  }

  return (
    <>
      <PageHeader title="Profile" description="Your details as they appear to others." />

      <Card title="Picture">
        <div className="row">
          {avatar ? (
            <img className="avatar" src={avatar} alt="" style={{ width: 64, height: 64 }} />
          ) : user.profilePicUrl ? (
            <img className="avatar" src={user.profilePicUrl} alt="" style={{ width: 64, height: 64 }} />
          ) : (
            <div className="avatar" style={{ width: 64, height: 64, fontSize: '1.1rem' }} aria-hidden="true">
              {initialsOf(user.fullName)}
            </div>
          )}
          <div>
            <input type="file" accept="image/*" onChange={pickAvatar} aria-label="Choose a profile picture" />
            <div className="small muted">PNG, JPEG, GIF or WebP, up to 2 MB.</div>
            {avatarError && <div className="field-error">{avatarError}</div>}
          </div>
        </div>
      </Card>

      <Card title="Details">
        <Alert kind="info">
          Your name and picture change straight away. Changes to your email, ID, department, batch,
          semester, section or designation are sent to an administrator for approval.
        </Alert>

        <form onSubmit={submit}>
          <div className="grid grid-2">
            <Field label="Full name" htmlFor="p-name">
              <input id="p-name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
            </Field>
            <Field label="Username" htmlFor="p-username">
              <input id="p-username" value={user.username} disabled />
            </Field>
            <Field label="Email" htmlFor="p-email">
              <input id="p-email" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
            </Field>
            <Field label="ID number" htmlFor="p-idno">
              <input id="p-idno" value={form.idNo} onChange={(e) => setForm({ ...form, idNo: e.target.value })} />
            </Field>
            <Field label="Department" htmlFor="p-dept">
              <input id="p-dept" value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} />
            </Field>
            {user.role === 'TEACHER' ? (
              <Field label="Designation" htmlFor="p-designation">
                <input
                  id="p-designation"
                  value={form.designation}
                  onChange={(e) => setForm({ ...form, designation: e.target.value })}
                />
              </Field>
            ) : (
              <>
                <Field label="Batch" htmlFor="p-batch">
                  <input id="p-batch" value={form.batch} onChange={(e) => setForm({ ...form, batch: e.target.value })} />
                </Field>
                <Field label="Semester" htmlFor="p-semester">
                  <input
                    id="p-semester"
                    value={form.semester}
                    onChange={(e) => setForm({ ...form, semester: e.target.value })}
                  />
                </Field>
                <Field label="Section" htmlFor="p-section">
                  <input id="p-section" value={form.section} onChange={(e) => setForm({ ...form, section: e.target.value })} />
                </Field>
              </>
            )}
          </div>

          <button className="btn" type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save changes'}
          </button>
        </form>
      </Card>
    </>
  )
}
