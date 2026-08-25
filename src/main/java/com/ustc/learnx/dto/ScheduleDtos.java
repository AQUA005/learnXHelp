package com.ustc.learnx.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Request and response shapes for the weekly routine and class tests.
 *
 * <p>These replace binding request bodies straight onto the JPA entities. That
 * let a client set the owning class and university on the record it was
 * creating, which is how an item could be written into another class — or
 * another university — regardless of who sent it. Only the fields below are
 * read from a request; ownership is decided by the server.
 */
public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    /**
     * A reference to a class by id.
     *
     * <p>Nested rather than a plain {@code studentClassId} because that is the
     * shape the existing clients send.
     */
    public record ClassRef(Long id) {
    }

    public record RoutineItemRequest(
            @NotBlank(message = "Course name is required")
            @Size(max = 255, message = "Course name is too long")
            String courseName,

            @NotBlank(message = "Day of week is required")
            String dayOfWeek,

            @NotNull(message = "Start time is required")
            LocalTime startTime,

            @NotNull(message = "End time is required")
            LocalTime endTime,

            @Size(max = 255, message = "Teacher name is too long")
            String teacherName,

            @Size(max = 255, message = "Room is too long")
            String roomNo,

            /** Optional. Ignored for a class representative, who may only edit their own class. */
            ClassRef studentClass) {
    }

    public record RoutineItemResponse(
            Long id,
            String dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String courseName,
            String teacherName,
            String roomNo,
            Long studentClassId,
            String className) {
    }

    public record ClassTestRequest(
            @NotBlank(message = "Course name is required")
            @Size(max = 255, message = "Course name is too long")
            String courseName,

            @NotNull(message = "Date and time are required")
            LocalDateTime dateTime,

            @Min(value = 0, message = "Duration cannot be negative")
            Integer durationMinutes,

            @Size(max = 255, message = "Room is too long")
            String roomNo,

            @Size(max = 255, message = "Topic is too long")
            String topic,

            /** Optional. Ignored for a class representative. */
            ClassRef studentClass) {

        public ClassTestRequest {
            durationMinutes = durationMinutes == null ? 0 : durationMinutes;
        }
    }

    public record ClassTestResponse(
            Long id,
            String courseName,
            LocalDateTime dateTime,
            int durationMinutes,
            String roomNo,
            String topic,
            String createdBy,
            Long studentClassId,
            String className) {
    }

    public record AuditLogResponse(
            Long id,
            String entityType,
            Long entityId,
            String action,
            String changedBy,
            LocalDateTime timestamp,
            String details) {
    }
}
