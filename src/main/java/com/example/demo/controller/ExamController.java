package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/exams")
@AllArgsConstructor
public class ExamController {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final UserRepository userRepository;
    private final GradeBookRepository gradeBookRepository;
    private final ObjectMapper objectMapper;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDto {
        private String questionText;
        private String questionType; // MCQ, SHORT_ANSWER
        private int points;
        private String options; // Semicolon-separated for MCQ
        private String correctAnswer;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateExamRequest {
        private String title;
        private String description;
        private int durationMinutes;
        private String startTime; // ISO string
        private String endTime; // ISO string
        private List<QuestionDto> questions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerSubmission {
        private Long questionId;
        private String answer;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExamResponseDto {
        private Long id;
        private String title;
        private String description;
        private int durationMinutes;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean published;
        private String teacherName;
        private List<QuestionResponseDto> questions;
        private boolean alreadySubmitted;
        private Integer previousScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionResponseDto {
        private Long id;
        private String questionText;
        private String questionType;
        private int points;
        private String options;
        // Excluded: correctAnswer (for students)
    }

    @PostMapping("/create")
    public ResponseEntity<?> createExam(@RequestBody CreateExamRequest request, Principal principal) {
        User teacher = userRepository.findByUsername(principal.getName()).orElse(null);
        if (teacher == null || teacher.getRole() == User.Role.STUDENT) {
            return ResponseEntity.status(403).body(Map.of("error", "Only teachers or CRs can create exams"));
        }

        LocalDateTime start = LocalDateTime.parse(request.getStartTime());
        LocalDateTime end = LocalDateTime.parse(request.getEndTime());

        Exam exam = Exam.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .teacher(teacher)
                .durationMinutes(request.getDurationMinutes())
                .startTime(start)
                .endTime(end)
                .published(true) // Auto-publish for demo purposes
                .build();

        Exam savedExam = examRepository.save(exam);

        List<ExamQuestion> questions = new ArrayList<>();
        for (QuestionDto qDto : request.getQuestions()) {
            ExamQuestion question = ExamQuestion.builder()
                    .exam(savedExam)
                    .questionText(qDto.getQuestionText())
                    .questionType(ExamQuestion.QuestionType.valueOf(qDto.getQuestionType().toUpperCase()))
                    .points(qDto.getPoints())
                    .options(qDto.getOptions())
                    .correctAnswer(qDto.getCorrectAnswer())
                    .build();
            questions.add(question);
        }
        examQuestionRepository.saveAll(questions);

        return ResponseEntity.ok(Map.of("message", "Exam created and published successfully", "examId", savedExam.getId()));
    }

    @GetMapping
    public ResponseEntity<?> getExams(Principal principal) {
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        List<Exam> exams;
        if (user.getRole() == User.Role.STUDENT || user.getRole() == User.Role.CR) {
            if (user.getStudentClass() != null) {
                exams = examRepository.findByStudentClassAndPublished(user.getStudentClass(), true);
            } else {
                exams = examRepository.findByPublished(true);
            }
        } else {
            exams = examRepository.findAll();
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Exam exam : exams) {
            Optional<ExamSubmission> submission = examSubmissionRepository.findByExamAndStudent(exam, user);
            Map<String, Object> examMap = new java.util.HashMap<>();
            examMap.put("id", exam.getId());
            examMap.put("title", exam.getTitle());
            examMap.put("description", exam.getDescription());
            examMap.put("durationMinutes", exam.getDurationMinutes());
            examMap.put("startTime", exam.getStartTime());
            examMap.put("endTime", exam.getEndTime());
            examMap.put("teacherName", exam.getTeacher() != null ? exam.getTeacher().getFullName() : "Unknown");
            examMap.put("published", exam.isPublished());
            examMap.put("alreadySubmitted", submission.isPresent());
            examMap.put("score", submission.map(ExamSubmission::getScore).orElse(null));
            list.add(examMap);
        }

        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getExamDetails(@PathVariable Long id, Principal principal) {
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        Exam exam = examRepository.findById(id).orElse(null);
        if (exam == null) {
            return ResponseEntity.notFound().build();
        }

        List<ExamQuestion> questions = examQuestionRepository.findByExam(exam);
        List<QuestionResponseDto> qResponseList = new ArrayList<>();

        for (ExamQuestion q : questions) {
            qResponseList.add(new QuestionResponseDto(
                    q.getId(),
                    q.getQuestionText(),
                    q.getQuestionType().name(),
                    q.getPoints(),
                    q.getOptions()
            ));
        }

        Optional<ExamSubmission> submission = examSubmissionRepository.findByExamAndStudent(exam, user);

        ExamResponseDto response = new ExamResponseDto(
                exam.getId(),
                exam.getTitle(),
                exam.getDescription(),
                exam.getDurationMinutes(),
                exam.getStartTime(),
                exam.getEndTime(),
                exam.isPublished(),
                exam.getTeacher().getFullName(),
                qResponseList,
                submission.isPresent(),
                submission.map(ExamSubmission::getScore).orElse(null)
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submitExam(
            @PathVariable Long id, 
            @RequestBody List<AnswerSubmission> answers, 
            Principal principal) {
        
        User student = userRepository.findByUsername(principal.getName()).orElse(null);
        if (student == null) {
            return ResponseEntity.status(401).build();
        }

        Exam exam = examRepository.findById(id).orElse(null);
        if (exam == null) {
            return ResponseEntity.notFound().build();
        }

        // Check if student already submitted
        if (examSubmissionRepository.findByExamAndStudent(exam, student).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "You have already submitted this exam"));
        }

        List<ExamQuestion> questions = examQuestionRepository.findByExam(exam);
        int finalScore = 0;

        for (AnswerSubmission ans : answers) {
            ExamQuestion question = questions.stream()
                    .filter(q -> q.getId().equals(ans.getQuestionId()))
                    .findFirst()
                    .orElse(null);
            
            if (question != null && ans.getAnswer() != null) {
                String studentAns = ans.getAnswer().trim();
                String correctAns = question.getCorrectAnswer().trim();
                
                if (question.getQuestionType() == ExamQuestion.QuestionType.MCQ) {
                    if (studentAns.equalsIgnoreCase(correctAns)) {
                        finalScore += question.getPoints();
                    }
                } else if (question.getQuestionType() == ExamQuestion.QuestionType.SHORT_ANSWER) {
                    // Simple case-insensitive matching
                    if (studentAns.equalsIgnoreCase(correctAns)) {
                        finalScore += question.getPoints();
                    }
                }
            }
        }

        // Save submission
        try {
            String answersJsonStr = objectMapper.writeValueAsString(answers);
            ExamSubmission submission = ExamSubmission.builder()
                    .exam(exam)
                    .student(student)
                    .submittedAt(LocalDateTime.now())
                    .score(finalScore)
                    .answersJson(answersJsonStr)
                    .build();

            examSubmissionRepository.save(submission);

            // Also automatically add to GradeBook for performance dashboard integration
            GradeBook gradeBook = GradeBook.builder()
                    .student(student)
                    .courseName(exam.getTitle())
                    .assessmentName("Quiz Exam")
                    .marksObtained(finalScore)
                    .maxMarks(questions.stream().mapToInt(ExamQuestion::getPoints).sum())
                    .build();
            
            // Delete old one if exists (unlikely given check)
            // Save to gradebook
            gradeBookRepository.save(gradeBook);

            return ResponseEntity.ok(Map.of("message", "Exam submitted successfully", "score", finalScore));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error processing submission: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/submissions")
    public ResponseEntity<?> getExamSubmissions(@PathVariable Long id, Principal principal) {
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null || user.getRole() == User.Role.STUDENT) {
            return ResponseEntity.status(403).body(Map.of("error", "Only teachers or CRs can view submissions"));
        }

        Exam exam = examRepository.findById(id).orElse(null);
        if (exam == null) {
            return ResponseEntity.notFound().build();
        }

        List<ExamSubmission> submissions = examSubmissionRepository.findByExam(exam);
        List<Map<String, Object>> result = new ArrayList<>();

        for (ExamSubmission sub : submissions) {
            result.add(Map.of(
                    "id", sub.getId(),
                    "studentName", sub.getStudent().getFullName(),
                    "studentUsername", sub.getStudent().getUsername(),
                    "submittedAt", sub.getSubmittedAt(),
                    "score", sub.getScore()
            ));
        }

        return ResponseEntity.ok(result);
    }
}
