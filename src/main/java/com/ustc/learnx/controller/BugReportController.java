package com.ustc.learnx.controller;

import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.entity.BugReport;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.BugReportRepository;
import com.ustc.learnx.service.CurrentUserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Filing a problem with LearnX itself.
 *
 * <p>Open to every signed-in role, because the person who runs into a broken
 * screen is usually a student and routing them through their university's
 * administrator loses the report. Only the platform owner reads them, from
 * {@code /api/master/bugs}.
 *
 * <p>Who filed it is taken from the session. It used to be taken from the
 * request body, which meant any account could file a report under anybody
 * else's name, and the one person who reads these had no way to tell.
 */
@RestController
@RequestMapping("/api/bugs")
@AllArgsConstructor
public class BugReportController {

    /** Long enough for a real description, short enough not to be a payload. */
    private static final int MAX_DESCRIPTION = 4000;

    private final BugReportRepository bugReportRepository;
    private final CurrentUserService currentUserService;

    /**
     * What the reporter tells us: what broke, and where.
     *
     * <p>Deliberately no reporter field. The screen is trusted only as a hint
     * for reproducing the problem, and is truncated rather than validated.
     */
    public record BugReportSubmission(String title, String description, String pagePath) {
    }

    @PostMapping("/report")
    public ResponseEntity<?> reportBug(@RequestBody BugReportSubmission submission) {
        String title = required(submission.title(), "Tell us what went wrong in a few words");
        String description = required(submission.description(), "A description is required");
        if (description.length() > MAX_DESCRIPTION) {
            throw new ValidationException("That description is too long to submit");
        }

        User reporter = currentUserService.requireCurrentUser();

        bugReportRepository.save(BugReport.builder()
                .title(title.length() > 255 ? title.substring(0, 255) : title)
                .description(description)
                .reportedBy(reporter.getFullName())
                .reporterEmail(reporter.getEmail())
                .reporterRole(reporter.getRole() == null ? null : reporter.getRole().name())
                .universityName(reporter.getUniversity() == null ? null : reporter.getUniversity().getName())
                .pagePath(trimmedToLength(submission.pagePath(), 255))
                .createdAt(LocalDateTime.now())
                .status("PENDING")
                .build());

        return ResponseEntity.ok(Map.of(
                "message", "Thank you — your report has reached the LearnX team."));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }

    private static String trimmedToLength(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
