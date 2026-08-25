package com.ustc.learnx.controller;

import com.ustc.learnx.dto.ExamDtos.AnswerSubmission;
import com.ustc.learnx.dto.ExamDtos.CreateExamRequest;
import com.ustc.learnx.dto.ExamDtos.ExamDetailResponse;
import com.ustc.learnx.dto.ExamDtos.ExamSummaryResponse;
import com.ustc.learnx.dto.ExamDtos.SubmissionResponse;
import com.ustc.learnx.dto.ExamDtos.SubmissionResult;
import com.ustc.learnx.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Exams. Binds requests and returns responses; the rules about who may see or
 * sit an exam live in {@link ExamService}.
 */
@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping("/create")
    public ResponseEntity<?> createExam(@Valid @RequestBody CreateExamRequest request) {
        Long examId = examService.createExam(request);
        return ResponseEntity.ok(Map.of(
                "message", "Exam created and published successfully",
                "examId", examId));
    }

    @GetMapping
    public ResponseEntity<List<ExamSummaryResponse>> getExams() {
        return ResponseEntity.ok(examService.listExams());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamDetailResponse> getExamDetails(@PathVariable Long id) {
        return ResponseEntity.ok(examService.getExam(id));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submitExam(@PathVariable Long id,
                                        @RequestBody List<AnswerSubmission> answers) {
        SubmissionResult result = examService.submit(id, answers);
        return ResponseEntity.ok(Map.of(
                "message", "Exam submitted successfully",
                "score", result.score(),
                "maxMarks", result.maxMarks()));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/{id}/submissions")
    public ResponseEntity<List<SubmissionResponse>> getExamSubmissions(@PathVariable Long id) {
        return ResponseEntity.ok(examService.listSubmissions(id));
    }
}
