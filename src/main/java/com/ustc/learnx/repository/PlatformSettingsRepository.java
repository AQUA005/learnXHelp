package com.ustc.learnx.repository;

import com.ustc.learnx.entity.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The platform branding row.
 *
 * <p>Migration V4 seeds it, so {@code findById(PlatformSettings.SINGLETON_ID)}
 * always finds one and no caller has to handle its absence.
 */
public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Long> {
}
