package com.ustc.learnx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** The published routine, where it comes from, and what has changed since. */
public final class RoutineDtos {

    private RoutineDtos() {}

    /**
     * One class as the sheet publishes it.
     *
     * <p>{@code key} is how a cancellation names this class: the sheet carries
     * no ids of its own, so the time and the course are the identity.
     */
    public record LiveClass(
            String key,
            String timeText,
            int startMinute,
            int endMinute,
            int periods,
            String course,
            String room,
            String teacherCode,
            String teacherName) {}

    public record LiveDay(String day, List<LiveClass> classes) {}

    /**
     * The whole week, and how much of it is trustworthy.
     *
     * <p>{@code stale} says the sheet could not be reached and this is the last
     * good copy; {@code daysLoaded} against {@code daysRequested} says a tab is
     * missing. Both are shown rather than hidden: a student deciding whether to
     * leave for a class deserves to know the data is an hour old.
     */
    public record LiveRoutine(
            boolean configured,
            String section,
            String semester,
            String session,
            List<String> sections,
            List<LiveDay> days,
            List<OverrideResponse> overrides,
            String sheetUrl,
            boolean stale,
            Instant fetchedAt,
            int daysLoaded,
            int daysRequested,
            String message) {}

    // --- Where the sheet is -------------------------------------------------

    public record SourceResponse(
            Long id,
            /** Empty for the university-wide fallback. */
            String department,
            String sheetId,
            String sheetUrl,
            String dayGids,
            String teacherGid,
            String blockHints,
            String updatedBy,
            LocalDateTime updatedAt) {}

    /** What an administrator saves for a department. */
    public record SourceRequest(
            @Size(max = 200) String department,
            /** A full sheet link or a bare id; reduced to the id before storing. */
            @NotBlank String sheet,
            @NotBlank @Size(max = 400) String dayGids,
            @Size(max = 40) String teacherGid,
            String blockHints) {}

    // --- What changed since ------------------------------------------------

    public record OverrideResponse(
            Long id,
            LocalDate date,
            String kind,
            String targetKey,
            String course,
            String room,
            String teacher,
            Integer startMinute,
            Integer endMinute,
            String timeText,
            String note,
            String createdBy,
            LocalDateTime createdAt,
            Long studentClassId,
            String className) {}

    /**
     * A class added or cancelled on one date.
     *
     * <p>A cancellation carries {@code targetKey}; an addition carries the
     * course and its times. Both are checked in the service rather than here,
     * because which fields are required depends on the kind.
     */
    public record OverrideRequest(
            @NotNull LocalDate date,
            @NotBlank String kind,
            @Size(max = 300) String targetKey,
            @Size(max = 200) String course,
            @Size(max = 60) String room,
            @Size(max = 200) String teacher,
            /** "HH:mm", as an input[type=time] sends it. */
            String start,
            String end,
            @Size(max = 400) String note,
            /** Which class group this is for; defaults to the poster's own. */
            Long studentClassId) {}
}
