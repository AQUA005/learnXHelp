package com.ustc.learnx.dto;

import com.ustc.learnx.entity.PlatformSettings;
import com.ustc.learnx.entity.University;

import java.util.List;

/**
 * What an anonymous visitor is allowed to see.
 *
 * <p>These are hand-built rather than entities so the set of published fields is
 * a decision written down in one place. Deliberately absent: the internal
 * {@code domain} and {@code id}, the storage key behind a logo, any count of
 * users or students, the tenant administrator's identity, any address other
 * than the declared contact one, and every timestamp.
 */
public final class PublicDtos {

    /** Roughly a card's worth of prose; the full text is on the profile page. */
    private static final int SUMMARY_LENGTH = 180;

    private PublicDtos() {
    }

    public record PlatformBranding(
            String siteName,
            String tagline,
            String logoUrl,
            String iconUrl,
            String supportEmail) {

        public static PlatformBranding from(PlatformSettings settings) {
            return new PlatformBranding(
                    settings.getSiteName(),
                    settings.getTagline(),
                    settings.getLogoUrl(),
                    settings.getIconUrl(),
                    settings.getSupportEmail());
        }
    }

    /** One card on the public homepage. */
    public record UniversitySummary(
            String slug,
            String name,
            String logoUrl,
            String shortDescription) {

        public static UniversitySummary from(University university) {
            return new UniversitySummary(
                    university.getSlug(),
                    university.getName(),
                    university.getLogoUrl(),
                    // Truncated here so the list does not ship every tenant's
                    // full prose to every visitor.
                    truncate(university.getDescription()));
        }
    }

    /** A university's public page. */
    public record UniversityProfile(
            String slug,
            String name,
            String description,
            String contactEmail,
            String contactPhone,
            String website,
            String address,
            String logoUrl,
            List<String> departments) {

        public static UniversityProfile from(University university, List<String> departments) {
            return new UniversityProfile(
                    university.getSlug(),
                    university.getName(),
                    university.getDescription(),
                    university.getContactEmail(),
                    university.getContactPhone(),
                    university.getWebsite(),
                    university.getAddress(),
                    university.getLogoUrl(),
                    departments);
        }
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= SUMMARY_LENGTH) {
            return text;
        }
        return text.substring(0, SUMMARY_LENGTH).stripTrailing() + "…";
    }
}
