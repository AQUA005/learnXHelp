package com.ustc.learnx.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String entityType; // e.g. "ROUTINE", "CLASS_TEST"

    private Long entityId;

    @Column(nullable = false)
    private String action; // e.g. "CREATE", "UPDATE", "DELETE"

    @Column(nullable = false)
    private String changedBy; // username

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * Whose audit trail this entry belongs to.
     *
     * <p>Null for entries that predate any tenant: a failed sign-in is recorded
     * before an account is resolved, and platform-level actions belong to no
     * university. Only a platform owner sees those.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id")
    private University university;
}
