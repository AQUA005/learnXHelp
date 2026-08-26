package com.ustc.learnx.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One tenant: a university, its public profile, and whether it is listed
 * publicly.
 */
@Entity
@Table(name = "universities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class University {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** Internal identifier, e.g. {@code ustc.ac.bd}. Never a public URL key. */
    @Column(nullable = false, unique = true)
    private String domain;

    /**
     * The public URL key, as in {@code /u/ustc-ac-bd}.
     *
     * <p>Assigned once at creation and never changed. {@code domain} is not used
     * for this: it may be edited by the platform owner, which would break every
     * link anyone had shared.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    // --- Public profile ---

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone", length = 64)
    private String contactPhone;

    private String website;

    @Column(length = 500)
    private String address;

    /**
     * Where the logo can be fetched from, or null when there is none.
     *
     * <p>A path this application serves, never a remote URL and never image
     * data. Set by the server, not by whoever sends the update.
     */
    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    /** Location of the logo within the storage root. Never leaves the server. */
    @JsonIgnore
    @Column(name = "logo_key", length = 200)
    private String logoKey;

    /** Whether this university appears on the public homepage. */
    @Builder.Default
    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** Also the logo's cache-busting version; see the branding endpoints. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
