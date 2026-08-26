import { useCurrentUser } from '@/lib/session'
import StudentDashboard from './StudentDashboard'
import TeacherDashboard from './TeacherDashboard'
import AdminDashboard from './AdminDashboard'
import PlatformDashboard from './PlatformDashboard'

/**
 * The home screen, chosen by who is looking at it.
 *
 * Every role used to land on the student dashboard, which asks the server for
 * "my class today" and "my results". For a teacher that is the wrong question;
 * for a platform owner, who belongs to no university, it is one the server
 * answers with 403.
 *
 * The four screens share their queries through `./queries`, under the same cache
 * keys the routine, announcements and results screens use, so switching between
 * them costs no extra requests.
 */
export default function DashboardPage() {
  const user = useCurrentUser()

  switch (user.role) {
    case 'SYSTEM_ADMIN':
      return <PlatformDashboard />
    case 'ADMIN':
      return <AdminDashboard />
    case 'TEACHER':
      return <TeacherDashboard />
    default:
      // A class representative sees the student view; their extra powers live
      // on the routine and announcement screens, not here.
      return <StudentDashboard />
  }
}
