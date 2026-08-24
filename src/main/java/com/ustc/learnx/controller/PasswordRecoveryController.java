package com.ustc.learnx.controller;

import com.ustc.learnx.common.PasswordPolicy;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Self-service password reset by emailed one-time code.
 *
 * <p>Codes are single-use, expire after {@link #CODE_TTL}, and allow a limited
 * number of guesses. Both endpoints answer identically whether or not the
 * account exists, so this cannot be used to discover registered addresses.
 *
 * <p>Pending codes are held in memory, so a multi-instance deployment needs to
 * move this store into the database or a shared cache.
 */
@RestController
@RequestMapping("/api/auth/recover")
@AllArgsConstructor
public class PasswordRecoveryController {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryController.class);

    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS = 5;
    /** Guards against unbounded growth if the endpoint is hammered. */
    private static final int MAX_PENDING = 10_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Keyed by the resolved account email — never by raw client input. */
    private static final ConcurrentHashMap<String, PendingReset> PENDING = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final org.springframework.core.env.Environment env;

    /** A code hash plus its expiry and remaining guesses. */
    private record PendingReset(String codeHash, Instant expiresAt, int attemptsLeft) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecoveryRequest {
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetRequest {
        private String email;
        private String code;
        private String password;
    }

    @PostMapping("/request")
    public ResponseEntity<?> requestRecovery(@RequestBody RecoveryRequest request) {
        String identifier = request.getEmail();
        if (identifier == null || identifier.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email or username is required"));
        }

        purgeExpired();

        findAccount(identifier).ifPresent(user -> {
            if (PENDING.size() >= MAX_PENDING) {
                log.warn("Password recovery store is full; dropping request");
                return;
            }
            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            PENDING.put(user.getEmail(), new PendingReset(
                    passwordEncoder.encode(code),
                    Instant.now().plus(CODE_TTL),
                    MAX_ATTEMPTS));
            sendCode(user, code);
        });

        // Identical response whether or not the account exists.
        return ResponseEntity.ok(Map.of(
                "message", "If that account exists, a verification code has been sent to its email address."));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetPassword(@RequestBody ResetRequest request) {
        String policyError = PasswordPolicy.validate(request.getPassword());
        if (policyError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", policyError));
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Verification code is required"));
        }

        purgeExpired();

        Optional<User> account = findAccount(request.getEmail());
        if (account.isEmpty()) {
            return invalidCode();
        }
        User user = account.get();

        PendingReset pending = PENDING.get(user.getEmail());
        if (pending == null || pending.isExpired()) {
            PENDING.remove(user.getEmail());
            return invalidCode();
        }

        if (!passwordEncoder.matches(request.getCode(), pending.codeHash())) {
            int left = pending.attemptsLeft() - 1;
            if (left <= 0) {
                // Burn the code rather than allowing unlimited guesses.
                PENDING.remove(user.getEmail());
            } else {
                PENDING.put(user.getEmail(),
                        new PendingReset(pending.codeHash(), pending.expiresAt(), left));
            }
            return invalidCode();
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        PENDING.remove(user.getEmail());

        log.info("Password reset completed for user id {}", user.getId());
        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully. You can now login."));
    }

    /** Accepts either an email address or a username. */
    private Optional<User> findAccount(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifier.trim();
        return userRepository.findByEmail(trimmed)
                .or(() -> userRepository.findByUsername(trimmed))
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank());
    }

    private ResponseEntity<?> invalidCode() {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired verification code."));
    }

    private void sendCode(User user, String code) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            String fromEmail = env.getProperty("spring.mail.username");
            if (fromEmail != null && !fromEmail.isEmpty()) {
                mailMessage.setFrom(fromEmail);
            }
            mailMessage.setTo(user.getEmail());
            mailMessage.setSubject("LearnX Password Recovery Verification Code");
            mailMessage.setText("Hello,\n\n"
                    + "You have requested to reset your password on LearnX.\n"
                    + "Your password recovery verification code is: " + code + "\n\n"
                    + "This code expires in " + CODE_TTL.toMinutes() + " minutes. "
                    + "If you did not make this request, you can ignore this email.\n\n"
                    + "Best regards,\n"
                    + "LearnX Team");
            mailSender.send(mailMessage);
        } catch (Exception e) {
            // Never log the code itself, and never surface the failure to the
            // caller, since that would reveal whether the account exists.
            log.error("Failed to send recovery email for user id {}: {}", user.getId(), e.getMessage());
        }
    }

    private static void purgeExpired() {
        PENDING.values().removeIf(PendingReset::isExpired);
    }
}
