package com.ustc.learnx.controller;

import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.dto.PublicDtos.PlatformBranding;
import com.ustc.learnx.dto.PublicDtos.UniversityProfile;
import com.ustc.learnx.dto.PublicDtos.UniversitySummary;
import com.ustc.learnx.entity.SystemMetadata;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.repository.SystemMetadataRepository;
import com.ustc.learnx.repository.UniversityRepository;
import com.ustc.learnx.service.BrandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Everything an anonymous visitor may read: the platform's branding, the
 * universities that are listed publicly, and enough of each to sign up.
 *
 * <p>This class deliberately does not depend on {@code CurrentUserService}.
 * There is no caller to scope a response to, and the absence of that dependency
 * is what makes it reviewable at a glance that no tenant-scoped logic has
 * drifted in here.
 *
 * <p>Every university lookup goes through {@code findBySlugAndPublishedTrue}, so
 * an unpublished university answers exactly as a nonexistent one does. Anything
 * else would let a visitor enumerate the names of every draft tenant.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    /** The lists a signup form may ask for. Mirrors {@code MetadataController}. */
    private static final List<String> ALLOWED_TYPES =
            List.of("SEMESTER", "DEPARTMENT", "BATCH", "SECTION", "DESIGNATION");

    /** Long enough to be worth caching, short enough that an edit lands the same day. */
    private static final Duration IMAGE_CACHE = Duration.ofHours(1);

    private final UniversityRepository universityRepository;
    private final SystemMetadataRepository systemMetadataRepository;
    private final BrandingService brandingService;

    // --- Platform branding ---

    @GetMapping("/branding")
    public ResponseEntity<PlatformBranding> branding() {
        return ResponseEntity.ok(PlatformBranding.from(brandingService.settings()));
    }

    @GetMapping("/branding/logo")
    public ResponseEntity<Resource> platformLogo() {
        return image(brandingService.loadPlatformLogo());
    }

    @GetMapping("/branding/icon")
    public ResponseEntity<Resource> platformIcon() {
        return image(brandingService.loadPlatformIcon());
    }

    // --- Universities ---

    @GetMapping("/universities")
    public ResponseEntity<List<UniversitySummary>> listUniversities() {
        return ResponseEntity.ok(universityRepository.findByPublishedTrueOrderByNameAsc()
                .stream().map(UniversitySummary::from).toList());
    }

    @GetMapping("/universities/{slug}")
    public ResponseEntity<UniversityProfile> universityProfile(@PathVariable String slug) {
        University university = requirePublished(slug);
        // Departments are the one internal list that is genuinely public
        // information: it is how somebody confirms they are signing up to the
        // right place. Semesters and sections are not.
        List<String> departments = valuesOf(university, "DEPARTMENT");
        return ResponseEntity.ok(UniversityProfile.from(university, departments));
    }

    @GetMapping("/universities/{slug}/logo")
    public ResponseEntity<Resource> universityLogo(@PathVariable String slug) {
        return image(brandingService.loadUniversityLogo(slug));
    }

    /**
     * The dropdown values a signup form needs.
     *
     * <p>Values only, never ids: an id is the handle the administrator-only
     * delete endpoint takes, and there is no reason to hand one to a visitor.
     */
    @GetMapping("/universities/{slug}/metadata")
    public ResponseEntity<List<String>> metadata(@PathVariable String slug,
                                                 @RequestParam String type) {
        String normalised = type.trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(normalised)) {
            throw new ValidationException("Type must be one of " + String.join(", ", ALLOWED_TYPES));
        }
        return ResponseEntity.ok(valuesOf(requirePublished(slug), normalised));
    }

    // --- Shared ---

    private University requirePublished(String slug) {
        return universityRepository.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> new NotFoundException("No such university"));
    }

    private List<String> valuesOf(University university, String type) {
        return systemMetadataRepository.findByTypeAndUniversity(type, university).stream()
                .map(SystemMetadata::getValue)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * Cached publicly, unlike an avatar, which is cached privately. These images
     * appear on pages anyone can visit, so a shared cache in front of the
     * application should be allowed to hold them.
     */
    private static ResponseEntity<Resource> image(BrandingService.StoredImage stored) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .cacheControl(CacheControl.maxAge(IMAGE_CACHE).cachePublic())
                .body(stored.content());
    }
}
