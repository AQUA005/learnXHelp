package com.ustc.learnx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class GradeDtos {

    private GradeDtos() {
    }

    public record AddGradeRequest(
            @NotBlank(message = "Student username is required")
            String studentUsername,

            @NotBlank(message = "Course name is required")
            @Size(max = 255, message = "Course name is too long")
            String courseName,

            @NotBlank(message = "Assessment name is required")
            @Size(max = 255, message = "Assessment name is too long")
            String assessmentName,

            @NotNull(message = "Marks obtained is required")
            @PositiveOrZero(message = "Marks cannot be negative")
            Double marksObtained,

            @NotNull(message = "Total marks is required")
            @PositiveOrZero(message = "Total marks cannot be negative")
            Double maxMarks) {
    }

    /** A grade with how it compares to the rest of the cohort. */
    public record PerformanceStat(
            Long id,
            String courseName,
            String assessmentName,
            double marksObtained,
            double maxMarks,
            double classAverage,
            double classHighest,
            double percentile) {
    }

    /** A recorded grade, as shown to staff. */
    public record GradeResponse(
            Long id,
            String studentUsername,
            String studentName,
            String courseName,
            String assessmentName,
            double marksObtained,
            double maxMarks) {
    }
}
