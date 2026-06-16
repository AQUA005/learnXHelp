package com.example.demo.controller;

import com.example.demo.entity.BugReport;
import com.example.demo.repository.BugReportRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/bugs")
@AllArgsConstructor
public class BugReportController {

    private final BugReportRepository bugReportRepository;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BugReportSubmission {
        private String title;
        private String description;
        private String reportedBy;
    }

    @PostMapping("/report")
    public ResponseEntity<?> reportBug(@RequestBody BugReportSubmission submission) {
        if (submission.getTitle() == null || submission.getTitle().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bug title is required"));
        }
        if (submission.getDescription() == null || submission.getDescription().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bug description is required"));
        }

        BugReport bug = BugReport.builder()
                .title(submission.getTitle().trim())
                .description(submission.getDescription().trim())
                .reportedBy(submission.getReportedBy() != null && !submission.getReportedBy().trim().isEmpty() ? 
                        submission.getReportedBy().trim() : "Anonymous")
                .createdAt(LocalDateTime.now())
                .status("PENDING")
                .build();

        bugReportRepository.save(bug);
        return ResponseEntity.ok(Map.of("message", "Bug report submitted successfully! Thank you for your feedback."));
    }
}
