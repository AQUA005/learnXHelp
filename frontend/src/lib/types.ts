/** Shapes returned by the LearnX API. */

export type Role = 'STUDENT' | 'CR' | 'TEACHER' | 'ADMIN' | 'SYSTEM_ADMIN'

/** Which university a signed-in account belongs to. Null for a platform owner. */
export type UserUniversity = {
  slug: string
  name: string
  logoUrl: string | null
}

export type CurrentUser = {
  id: number
  /** Generated from the email address, never typed. Email is the credential. */
  username: string
  fullName: string
  email: string
  role: Role
  idNo: string | null
  department: string | null
  batch: string | null
  semester: string | null
  section: string | null
  designation: string | null
  profilePicUrl: string | null
  approved: boolean
  university: UserUniversity | null
}

/** A university as the public homepage lists it. */
export type UniversitySummary = {
  slug: string
  name: string
  logoUrl: string | null
  shortDescription: string | null
}

/** A university's public page. */
export type UniversityProfile = {
  slug: string
  name: string
  description: string | null
  contactEmail: string | null
  contactPhone: string | null
  website: string | null
  address: string | null
  logoUrl: string | null
  departments: string[]
}

/** LearnX's own branding, shown above every tenant. */
export type PlatformBranding = {
  siteName: string
  tagline: string | null
  logoUrl: string | null
  iconUrl: string | null
  supportEmail: string | null
}

export type RoutineItem = {
  id: number
  dayOfWeek: string
  startTime: string
  endTime: string
  courseName: string
  teacherName: string | null
  roomNo: string | null
  studentClassId: number | null
  className: string | null
}

export type ClassTest = {
  id: number
  courseName: string
  dateTime: string
  durationMinutes: number
  roomNo: string | null
  topic: string | null
  createdBy: string | null
  studentClassId: number | null
  className: string | null
}

export type Announcement = {
  id: number
  title: string
  content: string
  createdAt: string
  createdBy: string
  createdByRole: string
  studentClassId: number | null
  className: string | null
}

export type StudyResource = {
  id: number
  title: string
  courseName: string
  fileName: string | null
  contentType: string | null
  storageKey: string | null
  fileSize: number | null
  approved: boolean
  examTags: string | null
  driveLink: string | null
  likesCount: number
  dislikesCount: number
  userReaction: 'LIKE' | 'DISLIKE' | null
  uploadedBy: { id: number; fullName: string } | null
}

export type ExamSummary = {
  id: number
  title: string
  description: string | null
  durationMinutes: number
  startTime: string | null
  endTime: string | null
  teacherName: string
  published: boolean
  alreadySubmitted: boolean
  score: number | null
}

export type ExamQuestion = {
  id: number
  questionText: string
  questionType: 'MCQ' | 'SHORT_ANSWER'
  points: number
  options: string | null
}

export type ExamDetail = {
  id: number
  title: string
  description: string | null
  durationMinutes: number
  startTime: string | null
  endTime: string | null
  published: boolean
  teacherName: string
  questions: ExamQuestion[]
  alreadySubmitted: boolean
  previousScore: number | null
}

export type PerformanceStat = {
  id: number
  courseName: string
  assessmentName: string
  marksObtained: number
  maxMarks: number
  classAverage: number
  classHighest: number
  percentile: number
}

export type GradeRecord = {
  id: number
  studentUsername: string | null
  studentName: string | null
  courseName: string
  assessmentName: string
  marksObtained: number
  maxMarks: number
}

export type MetadataOption = {
  id: number
  type: string
  value: string
}

export type PendingUser = {
  id: number
  username: string
  fullName: string
  email: string
  role: Role
}

export type FreeSlot = {
  start: string
  end: string
}
