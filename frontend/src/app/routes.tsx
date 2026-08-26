import { lazy, Suspense } from 'react'
import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { hasAtLeast, useSession } from '@/lib/session'
import type { Role } from '@/lib/types'
import { Loading } from '@/components/ui'
import AppShell from './AppShell'

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
const ClassDetail = lazy(() => import('@/features/admin/ClassDetailPage'))
const Platform = lazy(() => import('@/features/platform/PlatformPage'))
const PlatformUniversity = lazy(() => import('@/features/platform/UniversityDetailPage'))

const PublicShell = lazy(() => import('@/features/public/PublicShell'))
const Home = lazy(() => import('@/features/public/HomePage'))
const UniversityPublic = lazy(() => import('@/features/public/UniversityPublicPage'))
const SignIn = lazy(() => import('@/features/auth/SignInPage'))
const SignUp = lazy(() => import('@/features/auth/SignUpPage'))
const Recover = lazy(() => import('@/features/auth/RecoverPage'))

/** Blocks a screen unless the signed-in user meets the minimum role. */
function Require({ minimum, children }: { minimum: Role; children: ReactNode }) {
  const { user } = useSession()
  if (!user) return <Navigate to="/" replace />
  if (!hasAtLeast(user.role, minimum)) {
    return <Navigate to="/" replace />
  }
  return <>{children}</>
}

/**
 * Two route trees rather than one guarded tree.
 *
 * `/` means the public homepage to a visitor and the dashboard to a member, so a
 * single tree would have to branch there anyway. Splitting keeps the signed-in
 * routes exactly as they were, and replaces the old short-circuit — which
 * rendered the sign-in card in place of the whole router — with something that
 * can actually serve a page before anybody has an account.
 */
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

  return (
    <Suspense fallback={<div className="page"><Loading rows={5} /></div>}>
      {user ? <SignedInRoutes /> : <PublicRoutes />}
    </Suspense>
  )
}

function PublicRoutes() {
  return (
    <Routes>
      <Route element={<PublicShell />}>
        <Route path="/" element={<Home />} />
        <Route path="/u/:slug" element={<UniversityPublic />} />
        <Route path="/signin" element={<SignIn />} />
        <Route path="/signup" element={<SignUp />} />
        <Route path="/recover" element={<Recover />} />
      </Route>

      {/* Keep where they were trying to go, rather than dropping them on the
          dashboard once they sign in. */}
      <Route path="*" element={<SignInWithDestination />} />
    </Routes>
  )
}

function SignInWithDestination() {
  const location = useLocation()
  const next = encodeURIComponent(location.pathname + location.search)
  return <Navigate to={`/signin?next=${next}`} replace />
}

function SignedInRoutes() {
  return (
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
        <Route
          path="/admin/classes/:id"
          element={
            <Require minimum="ADMIN">
              <ClassDetail />
            </Require>
          }
        />
        <Route
          path="/platform"
          element={
            <Require minimum="SYSTEM_ADMIN">
              <Platform />
            </Require>
          }
        />
        <Route
          path="/platform/universities/:id"
          element={
            <Require minimum="SYSTEM_ADMIN">
              <PlatformUniversity />
            </Require>
          }
        />
      </Route>

      {/* Sitting an exam is full screen, without the navigation. */}
      <Route path="/exams/:examId" element={<ExamRoom />} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
