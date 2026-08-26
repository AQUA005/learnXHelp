package com.ustc.learnx.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * LearnX's own branding: the name, logos and support address shown above every
 * tenant.
 *
 * <p>Exactly one row, with id 1, seeded by migration V4 and enforced by a check
 * constraint. There is no {@code @GeneratedValue} because the id is never
 * chosen — read it with {@code findById(SINGLETON_ID)}.
 */
@Entity
@Table(name = "platform_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSettings {

    /** The only id this table ever holds. */
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "site_name", nullable = false, length = 120)
    private String siteName;

    private String tagline;

    /** A path this application serves, set by the server. */
    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @JsonIgnore
    @Column(name = "logo_key", length = 200)
    private String logoKey;

    /** The small square mark, used as the favicon. */
    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;

    @JsonIgnore
    @Column(name = "icon_key", length = 200)
    private String iconKey;

    @Column(name = "support_email")
    private String supportEmail;

    /** Doubles as the logo and icon cache-busting version. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
