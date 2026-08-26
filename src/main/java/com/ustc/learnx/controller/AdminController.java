package com.ustc.learnx.controller;

import com.ustc.learnx.common.AccessDeniedException;
import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.common.PasswordPolicy;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.UserRepository;
import com.ustc.learnx.service.AuditService;
import com.ustc.learnx.service.CurrentUserService;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * Managing the people at one university: approving new accounts, and helping
 * someone who can no longer get in.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@AllArgsConstructor
public class AdminController {

    /** Unambiguous characters only, since these get read aloud and typed by hand. */
    private static final String GENERATED_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final int GENERATED_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final com.ustc.learnx.service.ClassPlacementService classPlacementService;

    /** Accounts awaiting approval, scoped to the caller's university. */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingUsers() {
        Long universityId = currentUserService.requireUniversityId();

        List<Map<String, Object>> pending = userRepository
                .findByApprovedFalseAndUniversity_Id(universityId).stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "fullName", u.getFullName(),
                        "email", u.getEmail(),
                        "role", u.getRole().name()))
                .toList();

        return ResponseEntity.ok(pending);
    }

    /** Everyone at this university, for finding the person who needs help. */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        Long universityId = currentUserService.requireUniversityId();

        List<Map<String, Object>> people = userRepository.findByUniversity_Id(universityId).stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "fullName", u.getFullName(),
                        "email", u.getEmail(),
                        "role", u.getRole().name(),
                        "approved", u.isApproved()))
                .toList();

        return ResponseEntity.ok(people);
    }

    /**
     * Approves an account and places it in its class.
     *
     * <p>The placement is the part that was missing. Approving used to set the
     * flag and nothing else, so a student approved from the administration
     * screen belonged to no class — and the routine, notes, announcements and
     * class tests are all scoped to one, so every such screen stayed empty for
     * them with nothing to explain it.
     */
    @PostMapping("/approve/{id}")
    @Transactional
    public ResponseEntity<?> approveUser(@PathVariable Long id) {
        User user = requireUserInOwnUniversity(id);
        user.setApproved(true);
        classPlacementService.place(user);
        userRepository.save(user);
        auditService.record("ACCOUNT", "APPROVE", currentUserService.requireCurrentUser().getUsername(),
                "Approved the account '" + user.getUsername() + "'");
        return ResponseEntity.ok(Map.of("message", "User account approved successfully"));
    }

    @DeleteMapping("/reject/{id}")
    public ResponseEntity<?> rejectUser(@PathVariable Long id) {
        User user = requireUserInOwnUniversity(id);
        String username = user.getUsername();
        userRepository.delete(user);
        auditService.record("ACCOUNT", "REJECT", currentUserService.requireCurrentUser().getUsername(),
                "Rejected and deleted the account '" + username + "'");
        return ResponseEntity.ok(Map.of("message", "User account request rejected and deleted"));
    }

    public record ResetPasswordRequest(
            /** Leave empty to have one generated. */
            @Size(max = 128, message = "Password is too long") String password) {
    }

    /**
     * Sets a new password for someone who can no longer sign in.
     *
     * <p>Self-service recovery needs working email. Where that is unavailable —
     * or the address on the account is wrong — this is the only way back in, and
     * without it a forgotten password locked someone out permanently.
     *
     * <p>The new password is returned once, in the response, so the
     * administrator can pass it on. It is not stored anywhere in the clear and
     * cannot be retrieved again.
     */
    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id,
                                           @RequestBody(required = false) ResetPasswordRequest request) {
        User actor = currentUserService.requireCurrentUser();
        User target = requireUserInOwnUniversity(id);

        // An administrator may help students, representatives and teachers. One
        // administrator resetting another's password would be a way to take over
        // the account, so that is left to the platform owner.
        if (target.getRole() == User.Role.ADMIN || target.getRole() == User.Role.SYSTEM_ADMIN) {
            if (actor.getRole() != User.Role.SYSTEM_ADMIN) {
                throw new AccessDeniedException(
                        "Only the platform owner can reset an administrator's password");
            }
        }

        String password = (request == null || request.password() == null || request.password().isBlank())
                ? generatePassword()
                : request.password().trim();

        String policyError = PasswordPolicy.validate(password);
        if (policyError != null) {
            throw new ValidationException(policyError);
        }

        target.setPassword(passwordEncoder.encode(password));
        userRepository.save(target);

        auditService.record("ACCOUNT", "RESET_PASSWORD", actor.getUsername(),
                "Reset the password for '" + target.getUsername() + "'");

        // The email, not the username: that is what they sign in with, and the
        // username is generated and never shown to them.
        return ResponseEntity.ok(Map.of(
                "message", "Password reset. Give this to " + target.getFullName()
                        + ", and ask them to change it after signing in.",
                "email", target.getEmail(),
                "password", password));
    }

    /** A password that is easy to read out and still hard to guess. */
    private static String generatePassword() {
        StringBuilder generated = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            generated.append(GENERATED_ALPHABET.charAt(RANDOM.nextInt(GENERATED_ALPHABET.length())));
        }
        // The policy requires a digit, and the alphabet above may not have
        // produced one.
        generated.setCharAt(GENERATED_LENGTH - 1, (char) ('2' + RANDOM.nextInt(8)));
        return generated.toString();
    }

    /**
     * Loads a user and refuses if they belong to a different university, so an
     * administrator of one tenant cannot act on another's accounts.
     */
    private User requireUserInOwnUniversity(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        currentUserService.assertSameUniversity(user.getUniversity());
        return user;
    }
}
