import { lazy, Suspense } from 'react'
import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { hasAtLeast, useSession } from '@/lib/session'
import type { Role } from '@/lib/types'
import { Loading } from '@/components/ui'
import AppShell from './AppShell'
import AuthPage from '@/features/auth/AuthPage'

// Screens are split so the first paint only loads what it needs.
const Dashboard = lazy(() => import('@/features/dashboard/DashboardPage'))
const Schedule = lazy(() => import('@/features/schedule/SchedulePage'))
const Notes = lazy(() => import('@/features/resources/NotesPage'))
const Moderation = lazy(() => import('@/features/resources/ModerationPage'))
const Announcements = lazy(() => import('@/features/announcements/AnnouncementsPage'))
const Exams = lazy(() => import('@/features/exams/ExamsPage'))
const ExamRoom = lazy(() => import('@/features/exams/ExamRoomPage'))
const Performance = lazy(() => import('@/features/exams/PerformancePage'))
const Gradebook = lazy(() => import('@/features/exams/GradebookPage'))
const Profile = lazy(() => import('@/features/profile/ProfilePage'))
const Admin = lazy(() => import('@/features/admin/AdminPage'))

/** Blocks a screen unless the signed-in user meets the minimum role. */
function Require({ minimum, children }: { minimum: Role; children: ReactNode }) {
  const { user } = useSession()
  if (!user) return <Navigate to="/" replace />
  if (!hasAtLeast(user.role, minimum)) {
    return <Navigate to="/" replace />
  }
  return <>{children}</>
}

export default function AppRoutes() {
  const { user, loading } = useSession()

  if (loading) {
    return (
      <div className="auth-shell">
        <div className="auth-card">
          <Loading rows={4} label="Checking your session" />
        </div>
      </div>
    )
  }

  if (!user) {
    return <AuthPage />
  }

  return (
    <Suspense fallback={<div className="page"><Loading rows={5} /></div>}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/schedule" element={<Schedule />} />
          <Route path="/notes" element={<Notes />} />
          <Route path="/announcements" element={<Announcements />} />
          <Route path="/exams" element={<Exams />} />
          <Route path="/performance" element={<Performance />} />
          <Route path="/profile" element={<Profile />} />

          <Route
            path="/moderation"
            element={
              <Require minimum="TEACHER">
                <Moderation />
              </Require>
            }
          />
          <Route
            path="/gradebook"
            element={
              <Require minimum="TEACHER">
                <Gradebook />
              </Require>
            }
          />
          <Route
            path="/admin"
            element={
              <Require minimum="ADMIN">
                <Admin />
              </Require>
            }
          />
        </Route>

        {/* Sitting an exam is full screen, without the navigation. */}
        <Route path="/exams/:examId" element={<ExamRoom />} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
