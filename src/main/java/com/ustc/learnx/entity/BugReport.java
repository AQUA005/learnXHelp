package com.ustc.learnx.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bug_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BugReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** The reporter's display name, for the console to show. */
    private String reportedBy;

    /** How to reach them. Set from the session, never from the request. */
    private String reporterEmail;

    /** What they were when they filed it, which a later promotion does not change. */
    private String reporterRole;

    /**
     * Which campus the report came from, by name.
     *
     * <p>Not a foreign key: a report has to outlive the university it was filed
     * from, and a key would make deleting a tenant either fail or take its
     * reports with it. Null for the platform owner, who belongs to none.
     */
    private String universityName;

    /** The screen it was filed from, captured by the form rather than typed. */
    private String pagePath;

    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String status; // "PENDING", "REVIEWED", "RESOLVED"
}
