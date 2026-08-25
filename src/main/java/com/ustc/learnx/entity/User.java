package com.ustc.learnx.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    private String fullName;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    
    @Builder.Default
    @Column(nullable = false)
    private boolean approved = true;
    
    private String idNo;
    private String department;
    private String batch;
    private String semester;
    private String section;
    private String designation;
    /**
     * Where the avatar can be fetched from, or null when there is none.
     *
     * <p>A path served by the application, not image data. This column used to
     * hold an unbounded base64 data URL that travelled with every fetch of the
     * account.
     */
    @Column(columnDefinition = "TEXT")
    private String profilePicUrl;

    /** Location of the avatar within the storage root. */
    @Column(name = "profile_pic_key", length = 200)
    private String profilePicKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_class_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties("students")
    private StudentClass studentClass;

    /** Null for a SYSTEM_ADMIN, who sits above any single university. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id")
    private University university;

    public enum Role {
        STUDENT,
        CR,
        TEACHER,
        /** Administrator of a single university. */
        ADMIN,
        /** Platform owner. Not attached to any university. */
        SYSTEM_ADMIN
    }
}
