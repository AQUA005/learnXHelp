package com.ustc.learnx.service;

import com.ustc.learnx.entity.AuditLog;
import com.ustc.learnx.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Records security-relevant events.
 *
 * <p>Sign-in failures were previously invisible, so a password-guessing attempt
 * left no trace. Successes and failures are both written here, alongside the
 * schedule changes the audit table already held.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final String AUTHENTICATION = "AUTHENTICATION";

    private final AuditLogRepository auditLogRepository;

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        record(AUTHENTICATION, "SIGN_IN", event.getAuthentication().getName(), "Signed in");
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String username = String.valueOf(event.getAuthentication().getName());
        // The reason, not the credentials: the attempted password must never
        // reach the log.
        String reason = event.getException().getClass().getSimpleName();
        log.warn("Failed sign-in for '{}' ({})", username, reason);
        record(AUTHENTICATION, "SIGN_IN_FAILED", username, "Failed sign-in: " + reason);
    }

    /**
     * Writes an entry.
     *
     * <p>Runs in its own transaction so that recording an event cannot roll back
     * the work that produced it, and a failure to record is logged rather than
     * propagated.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String entityType, String action, String changedBy, String details) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .entityType(entityType)
                    .action(action)
                    .changedBy(changedBy == null ? "unknown" : changedBy)
                    .timestamp(LocalDateTime.now())
                    .details(details)
                    .build());
        } catch (Exception e) {
            log.error("Could not write an audit entry for {} {}", entityType, action, e);
        }
    }
}
