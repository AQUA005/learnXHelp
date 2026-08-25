package com.ustc.learnx.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request and response shapes for exams.
 *
 * <p>These replace direct serialization of the entities. Returning entities
 * exposed whatever happened to be mapped — including answer keys and, before
 * this refactor, password hashes reachable through associations.
 *
 * <p>Boxed types are used for numbers a client may legitimately omit: a missing
 * primitive is rejected outright by Jackson, which turns an incomplete form
 * into an unexplained 400 rather than a validation message naming the field.
 */
public final class ExamDtos {

    private ExamDtos() {
    }

    public record QuestionRequest(
            @NotBlank(message = "Question text is required")
            @Size(max = 5000, message = "Question text is too long")
            String questionText,

            @NotBlank(message = "Question type is required")
            String questionType,

            @Min(value = 0, message = "Points cannot be negative")
            Integer points,

            String options,
            String correctAnswer) {

        public QuestionRequest {
            points = points == null ? 0 : points;
        }
    }

    public record CreateExamRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 255, message = "Title is too long")
            String title,

            @Size(max = 5000, message = "Description is too long")
            String description,

            @Min(value = 1, message = "Duration must be at least one minute")
            Integer durationMinutes,

            @NotBlank(message = "Start time is required")
            String startTime,

            @NotBlank(message = "End time is required")
            String endTime,

            @NotEmpty(message = "An exam needs at least one question")
            @Valid List<QuestionRequest> questions,

            /** Optional. When absent the exam is open to the whole university. */
            Long studentClassId) {

        public CreateExamRequest {
            durationMinutes = durationMinutes == null ? 0 : durationMinutes;
        }
    }

    /** One answer as submitted by a student. */
    public record AnswerSubmission(Long questionId, String answer) {
    }

    /** A question as shown to a candidate. Excludes the answer key. */
    public record QuestionResponse(
            Long id,
            String questionText,
            String questionType,
            int points,
            String options) {
    }

    /** An exam in a list, with this caller's own submission state. */
    public record ExamSummaryResponse(
            Long id,
            String title,
            String description,
            int durationMinutes,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String teacherName,
            boolean published,
            boolean alreadySubmitted,
            Integer score) {
    }

    public record ExamDetailResponse(
            Long id,
            String title,
            String description,
            int durationMinutes,
            LocalDateTime startTime,
            LocalDateTime endTime,
            boolean published,
            String teacherName,
            List<QuestionResponse> questions,
            boolean alreadySubmitted,
            Integer previousScore) {
    }

    public record SubmissionResult(int score, int maxMarks) {
    }

    public record SubmissionResponse(
            Long id,
            String studentName,
            String studentUsername,
            LocalDateTime submittedAt,
            int score) {
    }
}
