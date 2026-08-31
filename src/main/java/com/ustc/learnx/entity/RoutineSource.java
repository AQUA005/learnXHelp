package com.ustc.learnx.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Where a department's routine is published.
 *
 * <p>A university keeps its timetable in a Google Sheet with one tab per
 * weekday, and departments do not share one. The row whose department is empty
 * is the university's fallback, used by anyone whose own department has no row.
 */
@Entity
@Table(name = "routine_sources")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutineSource {

    /** The department value that marks the university-wide fallback. */
    public static final String FALLBACK = "";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @Column(nullable = false)
    private String department;

    /** The sheet's id, never a full URL. */
    @Column(name = "sheet_id", nullable = false)
    private String sheetId;

    /** The gid of each weekday tab, comma separated, in the sheet's own order. */
    @Column(name = "day_gids", nullable = false)
    private String dayGids;

    /** The tab mapping teacher codes to names, if the sheet has one. */
    @Column(name = "teacher_gid")
    private String teacherGid;

    /**
     * Merged-cell hints, as JSON keyed by section then weekday then column.
     *
     * <p>A merged cell is reported by the sheet as one value followed by empty
     * cells, which reads exactly like a free period, so the span of a
     * multi-period class cannot be inferred from the data alone.
     */
    @Column(name = "block_hints", columnDefinition = "TEXT")
    private String blockHints;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}
