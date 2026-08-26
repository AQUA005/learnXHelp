package com.ustc.learnx.service;

import com.ustc.learnx.common.ImageDataUrl;
import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.entity.PlatformSettings;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.repository.PlatformSettingsRepository;
import com.ustc.learnx.repository.UniversityRepository;
import com.ustc.learnx.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Logos: LearnX's own, and each university's.
 *
 * <p>Follows {@link ProfileService}'s avatar handling. Two rules carry over and
 * matter more here, because these images are shown to anonymous visitors:
 *
 * <ul>
 *   <li>A logo is never a URL supplied by a caller. The Content-Security-Policy
 *       is {@code img-src 'self' data:}, so a remote one would not render, and
 *       accepting one would put caller-controlled text into an {@code img} src.
 *       Images enter only by upload, and the stored URL is computed here.</li>
 *   <li>The previous file is deleted only after the new one is safely written,
 *       so a failed upload cannot destroy the logo already in use.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BrandingService {

    /** Shipped in the jar, so the tab icon works before anybody uploads one. */
    private static final String BUNDLED_ICON = "static/learnx_logo.png";

    /** A logo is a small mark. Anything larger is a mistake. */
    private static final int MAX_LOGO_BYTES = 1024 * 1024;

    private final PlatformSettingsRepository platformSettingsRepository;
    private final UniversityRepository universityRepository;
    private final FileStorageService fileStorageService;

    /** An image ready to be written to a response. */
    public record StoredImage(Resource content, String contentType) {
    }

    // --- Platform branding ---

    /** The single settings row, seeded by migration V4. */
    @Transactional(readOnly = true)
    public PlatformSettings settings() {
        return platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "The platform settings row is missing; did migration V4 apply?"));
    }

    @Transactional
    public PlatformSettings storePlatformLogo(String dataUrl) {
        PlatformSettings settings = settings();
        String previousKey = settings.getLogoKey();

        var image = ImageDataUrl.decode(dataUrl, "Logo", MAX_LOGO_BYTES);
        var stored = fileStorageService.store(image.bytes(), image.extension());
        settings.setLogoKey(stored.storageKey());
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setLogoUrl(versioned("/api/public/branding/logo", settings.getUpdatedAt()));
        PlatformSettings saved = platformSettingsRepository.save(settings);

        deleteQuietly(previousKey);
        return saved;
    }

    @Transactional
    public PlatformSettings storePlatformIcon(String dataUrl) {
        PlatformSettings settings = settings();
        String previousKey = settings.getIconKey();

        var image = ImageDataUrl.decode(dataUrl, "Icon", MAX_LOGO_BYTES);
        var stored = fileStorageService.store(image.bytes(), image.extension());
        settings.setIconKey(stored.storageKey());
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setIconUrl(versioned("/api/public/branding/icon", settings.getUpdatedAt()));
        PlatformSettings saved = platformSettingsRepository.save(settings);

        deleteQuietly(previousKey);
        return saved;
    }

    @Transactional(readOnly = true)
    public StoredImage loadPlatformLogo() {
        return load(settings().getLogoKey(), "The platform has no logo");
    }

    /**
     * The small square mark, used as the browser tab icon.
     *
     * <p>Falls back to the bundled logo rather than answering 404. The page links
     * to this address unconditionally, and a 404 here is the missing-favicon
     * request every page load used to make.
     */
    @Transactional(readOnly = true)
    public StoredImage loadPlatformIcon() {
        String key = settings().getIconKey();
        if (key != null) {
            return load(key, "The platform has no icon");
        }
        return new StoredImage(new ClassPathResource(BUNDLED_ICON), "image/png");
    }

    // --- University logos ---

    @Transactional
    public University storeUniversityLogo(University university, String dataUrl) {
        String previousKey = university.getLogoKey();

        var image = ImageDataUrl.decode(dataUrl, "Logo", MAX_LOGO_BYTES);
        var stored = fileStorageService.store(image.bytes(), image.extension());
        university.setLogoKey(stored.storageKey());
        university.setUpdatedAt(LocalDateTime.now());
        university.setLogoUrl(versioned(
                "/api/public/universities/" + university.getSlug() + "/logo",
                university.getUpdatedAt()));
        University saved = universityRepository.save(university);

        deleteQuietly(previousKey);
        return saved;
    }

    @Transactional
    public University removeUniversityLogo(University university) {
        String previousKey = university.getLogoKey();
        university.setLogoKey(null);
        university.setLogoUrl(null);
        University saved = universityRepository.save(university);
        deleteQuietly(previousKey);
        return saved;
    }

    @Transactional(readOnly = true)
    public StoredImage loadUniversityLogo(String slug) {
        University university = universityRepository.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> new NotFoundException("No such university"));
        return load(university.getLogoKey(), "That university has no logo");
    }

    // --- Shared ---

    private StoredImage load(String storageKey, String absentMessage) {
        if (storageKey == null) {
            throw new NotFoundException(absentMessage);
        }
        return new StoredImage(fileStorageService.load(storageKey),
                ImageDataUrl.contentTypeFor(storageKey));
    }

    /**
     * Appends the last-modified time to the URL.
     *
     * <p>These images are served with a long public cache lifetime, so without a
     * version in the path a replaced logo would keep showing the old one for an
     * hour and be reported as broken.
     */
    private static String versioned(String path, LocalDateTime updatedAt) {
        return path + "?v=" + updatedAt.toEpochSecond(ZoneOffset.UTC);
    }

    /** A logo that outlives its row wastes space; a failure to remove it is not worth failing over. */
    private void deleteQuietly(String storageKey) {
        if (storageKey != null) {
            fileStorageService.delete(storageKey);
        }
    }
}
