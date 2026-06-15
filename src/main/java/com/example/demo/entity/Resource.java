package com.example.demo.entity;

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

    @Lob
    @Column(length = 104857600) // Support up to 100MB in database
    private byte[] fileData;

    @ManyToOne(optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploadedBy;

    @Column(nullable = false)
    private boolean approved;

    private String examTags; // e.g. "Midterm", "CT1", "Final"

    private String driveLink; // Direct Google Drive resources link

    @ManyToOne
    @JoinColumn(name = "student_class_id")
    private StudentClass studentClass;

    @ManyToOne
    @JoinColumn(name = "university_id")
    private University university;

    @Transient
    private int likesCount;

    @Transient
    private int dislikesCount;

    @Transient
    private String userReaction; // active user's reaction ("LIKE", "DISLIKE", or null)
}
