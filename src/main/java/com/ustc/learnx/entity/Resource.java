package com.ustc.learnx.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "resources")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String courseName;

    private String fileName;

    private String contentType;

    /**
     * Location of the uploaded file within the storage root.
     *
     * <p>File bytes live on disk, not in this row. Holding them here meant every
     * library listing loaded every file into memory before discarding it.
     */
    @Column(name = "storage_key", length = 200)
    private String storageKey;

    /** Size in bytes, so listings can show it without touching the file. */
    private Long fileSize;

    /** SHA-256 of the stored bytes, for integrity checks and de-duplication. */
    @Column(length = 64)
    private String sha256;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploadedBy;

    @Column(nullable = false)
    private boolean approved;

    private String examTags; // e.g. "Midterm", "CT1", "Final"

    private String driveLink; // Direct Google Drive resources link

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_class_id")
    private StudentClass studentClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id")
    private University university;

    @Transient
    private int likesCount;

    @Transient
    private int dislikesCount;

    @Transient
    private String userReaction; // active user's reaction ("LIKE", "DISLIKE", or null)
}
