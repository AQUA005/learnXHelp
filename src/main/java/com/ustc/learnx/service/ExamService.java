package com.ustc.learnx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustc.learnx.common.AccessDeniedException;
import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.dto.ExamDtos.AnswerSubmission;
import com.ustc.learnx.dto.ExamDtos.CreateExamRequest;
import com.ustc.learnx.dto.ExamDtos.ExamDetailResponse;
import com.ustc.learnx.dto.ExamDtos.ExamSummaryResponse;
import com.ustc.learnx.dto.ExamDtos.QuestionRequest;
import com.ustc.learnx.dto.ExamDtos.QuestionResponse;
import com.ustc.learnx.dto.ExamDtos.SubmissionResponse;
import com.ustc.learnx.dto.ExamDtos.SubmissionResult;
import com.ustc.learnx.entity.Exam;
import com.ustc.learnx.entity.ExamQuestion;
import com.ustc.learnx.entity.ExamSubmission;
import com.ustc.learnx.entity.GradeBook;
import com.ustc.learnx.entity.StudentClass;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.ExamQuestionRepository;
import com.ustc.learnx.repository.ExamRepository;
import com.ustc.learnx.repository.ExamSubmissionRepository;
import com.ustc.learnx.repository.GradeBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Exam lifecycle: authoring, listing, sitting and marking.
 *
 * <p>Holds the rules that decide whether a student may see or sit an exam. They
 * previously lived in the controller, where several were missing: an exam was
 * created without a class or university (so class scoping silently matched
 * nothing), any signed-in user could read or submit any exam by id, and a
 * submission was accepted whether or not the exam was open.
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final GradeBookRepository gradeBookRepository;
    private final com.ustc.learnx.repository.StudentClassRepository studentClassRepository;
    private final GradingService gradingService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    /** Assessment name used for the gradebook row an exam produces. */
    private static final String QUIZ_ASSESSMENT_NAME = "Quiz Exam";

    @Transactional
    public Long createExam(CreateExamRequest request) {
        User teacher = currentUserService.requireCurrentUser();

        LocalDateTime start = parseTimestamp(request.startTime(), "startTime");
        LocalDateTime end = parseTimestamp(request.endTime(), "endTime");
        if (end.isBefore(start)) {
            throw new ValidationException("The exam cannot end before it starts");
        }
        if (request.questions() == null || request.questions().isEmpty()) {
            throw new ValidationException("An exam needs at least one question");
        }

        // An exam may target one class, or none at all — in which case every
        // student in the university may sit it. Note this cannot be taken from
        // the teacher: staff belong to no class.
        StudentClass targetClass = null;
        if (request.studentClassId() != null) {
            targetClass = studentClassRepository.findById(request.studentClassId())
                    .orElseThrow(() -> new NotFoundException("Class not found"));
            currentUserService.assertSameUniversity(targetClass.getUniversity());
        }

        Exam exam = examRepository.save(Exam.builder()
                .title(request.title())
                .description(request.description())
                .teacher(teacher)
                .durationMinutes(request.durationMinutes())
                .startTime(start)
                .endTime(end)
                .published(true)
                .studentClass(targetClass)
                // The university was previously left unset, which put the exam
                // outside every tenancy check.
                .university(teacher.getUniversity())
                .build());

        List<ExamQuestion> questions = new ArrayList<>();
        for (QuestionRequest q : request.questions()) {
            questions.add(ExamQuestion.builder()
                    .exam(exam)
                    .questionText(q.questionText())
                    .questionType(parseQuestionType(q.questionType()))
                    .points(q.points())
                    .options(q.options())
                    .correctAnswer(q.correctAnswer())
                    .build());
        }
        examQuestionRepository.saveAll(questions);

        return exam.getId();
    }

    /** Exams the caller may see, each with their own submission state. */
    @Transactional(readOnly = true)
    public List<ExamSummaryResponse> listExams() {
        User user = currentUserService.requireCurrentUser();

        List<Exam> exams;
        if (isStudentLike(user)) {
            exams = examRepository.findVisibleToClass(user.getUniversity(), user.getStudentClass());
        } else if (user.getRole() == User.Role.TEACHER) {
            exams = examRepository.findByTeacher(user);
        } else if (user.getRole() == User.Role.SYSTEM_ADMIN) {
            exams = examRepository.findAll();
        } else {
            exams = examRepository.findByUniversity(user.getUniversity());
        }

        List<ExamSummaryResponse> summaries = new ArrayList<>(exams.size());
        for (Exam exam : exams) {
            Optional<ExamSubmission> submission =
                    examSubmissionRepository.findByExamAndStudent(exam, user);
            summaries.add(new ExamSummaryResponse(
                    exam.getId(),
                    exam.getTitle(),
                    exam.getDescription(),
                    exam.getDurationMinutes(),
                    exam.getStartTime(),
                    exam.getEndTime(),
                    exam.getTeacher() != null ? exam.getTeacher().getFullName() : "Unknown",
                    exam.isPublished(),
                    submission.isPresent(),
                    submission.map(ExamSubmission::getScore).orElse(null)));
        }
        return summaries;
    }

    /** An exam with its questions. Answer keys are never included. */
    @Transactional(readOnly = true)
    public ExamDetailResponse getExam(Long examId) {
        User user = currentUserService.requireCurrentUser();
        Exam exam = requireVisibleExam(examId, user);

        List<QuestionResponse> questions = examQuestionRepository.findByExam(exam).stream()
                .map(q -> new QuestionResponse(
                        q.getId(),
                        q.getQuestionText(),
                        q.getQuestionType().name(),
                        q.getPoints(),
                        q.getOptions()))
                .toList();

        Optional<ExamSubmission> submission = examSubmissionRepository.findByExamAndStudent(exam, user);

        return new ExamDetailResponse(
                exam.getId(),
                exam.getTitle(),
                exam.getDescription(),
                exam.getDurationMinutes(),
                exam.getStartTime(),
                exam.getEndTime(),
                exam.isPublished(),
                exam.getTeacher() != null ? exam.getTeacher().getFullName() : "Unknown",
                questions,
                submission.isPresent(),
                submission.map(ExamSubmission::getScore).orElse(null));
    }

    /** Marks a submission and records the result in the gradebook. */
    @Transactional
    public SubmissionResult submit(Long examId, List<AnswerSubmission> answers) {
        User student = currentUserService.requireCurrentUser();
        Exam exam = requireVisibleExam(examId, student);

        if (!exam.isPublished()) {
            throw new AccessDeniedException("That exam is not open for submissions");
        }
        requireExamWindowOpen(exam);

        if (examSubmissionRepository.findByExamAndStudent(exam, student).isPresent()) {
            throw new ValidationException("You have already submitted this exam");
        }

        List<ExamQuestion> questions = examQuestionRepository.findByExam(exam);
        List<GradingService.SubmittedAnswer> submitted = (answers == null ? List.<AnswerSubmission>of() : answers)
                .stream()
                .filter(java.util.Objects::nonNull)
                .map(a -> new GradingService.SubmittedAnswer(a.questionId(), a.answer()))
                .toList();

        GradingService.GradingResult result = gradingService.grade(questions, submitted);

        examSubmissionRepository.save(ExamSubmission.builder()
                .exam(exam)
                .student(student)
                .submittedAt(LocalDateTime.now())
                .score(result.score())
                .answersJson(writeAnswers(answers))
                .build());

        gradeBookRepository.save(GradeBook.builder()
                .student(student)
                .courseName(exam.getTitle())
                .assessmentName(QUIZ_ASSESSMENT_NAME)
                .marksObtained(result.score())
                .maxMarks(result.maxMarks())
                .build());

        return new SubmissionResult(result.score(), result.maxMarks());
    }

    /** Submissions for an exam, for the teacher who set it. */
    @Transactional(readOnly = true)
    public List<SubmissionResponse> listSubmissions(Long examId) {
        User user = currentUserService.requireCurrentUser();
        Exam exam = requireExam(examId);
        currentUserService.assertSameUniversity(exam.getUniversity());

        // A teacher sees their own exams; administrators see any in the university.
        if (user.getRole() == User.Role.TEACHER
                && exam.getTeacher() != null
                && !exam.getTeacher().getId().equals(user.getId())) {
            throw new AccessDeniedException("That exam was set by another teacher");
        }

        return examSubmissionRepository.findByExam(exam).stream()
                .map(s -> new SubmissionResponse(
                        s.getId(),
                        s.getStudent().getFullName(),
                        s.getStudent().getUsername(),
                        s.getSubmittedAt(),
                        s.getScore()))
                .toList();
    }

    private Exam requireExam(Long examId) {
        return examRepository.findById(examId)
                .orElseThrow(() -> new NotFoundException("Exam not found"));
    }

    /**
     * Loads an exam the caller is entitled to see.
     *
     * <p>Students are limited to exams set for their own class; staff to their
     * own university. Previously any id was readable by anyone signed in.
     */
    private Exam requireVisibleExam(Long examId, User user) {
        Exam exam = requireExam(examId);
        currentUserService.assertSameUniversity(exam.getUniversity());

        if (isStudentLike(user) && exam.getStudentClass() != null) {
            // An exam with no class is open to the whole university; one that
            // names a class is limited to it.
            Long ownClassId = user.getStudentClass() == null ? null : user.getStudentClass().getId();
            if (!exam.getStudentClass().getId().equals(ownClassId)) {
                throw new AccessDeniedException("That exam was not set for your class");
            }
        }
        return exam;
    }

    /** An exam accepts submissions only between its start and end times. */
    private void requireExamWindowOpen(Exam exam) {
        LocalDateTime now = LocalDateTime.now();
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            throw new AccessDeniedException("That exam has not started yet");
        }
        if (exam.getEndTime() != null && now.isAfter(exam.getEndTime())) {
            throw new AccessDeniedException("That exam has closed");
        }
    }

    private static boolean isStudentLike(User user) {
        return user.getRole() == User.Role.STUDENT || user.getRole() == User.Role.CR;
    }

    private static LocalDateTime parseTimestamp(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " is required");
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException(field + " must be an ISO date and time, such as 2026-09-01T10:00:00");
        }
    }

    private static ExamQuestion.QuestionType parseQuestionType(String value) {
        try {
            return ExamQuestion.QuestionType.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            throw new ValidationException("Question type must be MCQ or SHORT_ANSWER");
        }
    }

    private String writeAnswers(List<AnswerSubmission> answers) {
        try {
            return objectMapper.writeValueAsString(answers == null ? List.of() : answers);
        } catch (Exception e) {
            // The marks are already known; storing the raw answers is for review
            // only, so a serialization problem must not fail the submission.
            return null;
        }
    }
}
