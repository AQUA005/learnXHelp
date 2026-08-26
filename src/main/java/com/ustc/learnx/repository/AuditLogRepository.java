package com.ustc.learnx.repository;

import com.ustc.learnx.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * One university's audit trail.
     *
     * <p>The unscoped, unpaginated listing this replaced returned every row on
     * the platform to any administrator who asked.
     */
    Page<AuditLog> findByUniversity_IdOrderByTimestampDesc(Long universityId, Pageable pageable);

    /** Every entry, for a platform owner. */
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
