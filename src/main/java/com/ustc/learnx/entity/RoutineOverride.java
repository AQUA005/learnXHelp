package com.ustc.learnx.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A change to the published routine, announced for a whole class.
 *
 * <p>The sheet says what usually happens; this says what happens on one date.
 * A class representative posts it and every member of their class sees it,
 * because a cancelled class is news rather than a personal note.
 */
@Entity
@Table(name = "routine_overrides")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutineOverride {

    public enum Kind { ADDED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_class_id", nullable = false)
    private StudentClass studentClass;

    @Column(name = "on_date", nullable = false)
    private LocalDate onDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    /**
     * Which class in the sheet a cancellation refers to, as "start-end|course".
     *
     * <p>The sheet carries no ids, so a cancellation has to describe its
     * target. Normalised on both sides before comparison, so spacing and case
     * in the sheet do not decide whether a cancellation still applies.
     */
    @Column(name = "target_key")
    private String targetKey;

    @Column(name = "course_name")
    private String courseName;

    @Column(name = "room_no")
    private String roomNo;

    @Column(name = "teacher_name")
    private String teacherName;

    /** Minutes from midnight, so an added class sorts against the sheet's own. */
    @Column(name = "start_minute")
    private Integer startMinute;

    @Column(name = "end_minute")
    private Integer endMinute;

    private String note;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
