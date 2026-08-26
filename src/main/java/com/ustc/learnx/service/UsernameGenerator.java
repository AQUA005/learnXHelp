package com.ustc.learnx.service;

import com.ustc.learnx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Derives the internal username for a new account from its email address.
 *
 * <p>Nobody types a username any more — people sign in with their email. The
 * username survives because it is the security principal and because two
 * columns, {@code resource_reactions.username} and {@code class_tests.created_by},
 * reference it as a string. Generating it keeps it globally unique without
 * making that a user-visible constraint: with tenant after tenant joining, a
 * chosen handle like {@code rahim} would be claimed forever by whoever
 * registered first, and nobody could explain to the second why.
 */
@Service
@RequiredArgsConstructor
public class UsernameGenerator {

    /** Enough suffixes that exhausting them means something else is wrong. */
    private static final int MAX_ATTEMPTS = 10_000;

    private final UserRepository userRepository;

    /**
     * A free username derived from {@code email}.
     *
     * @return the local part reduced to letters, digits and dots, with a numeric
     *         suffix appended if that is already taken
     */
    public String forEmail(String email) {
        String base = sanitise(email);
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        for (int suffix = 2; suffix < MAX_ATTEMPTS; suffix++) {
            String candidate = base + suffix;
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not derive a free username from " + email);
    }

    private static String sanitise(String email) {
        String localPart = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        int at = localPart.indexOf('@');
        if (at > 0) {
            localPart = localPart.substring(0, at);
        }
        String cleaned = localPart.replaceAll("[^a-z0-9.]", "");
        // An address of "!!!@example.test" would otherwise leave nothing at all.
        return cleaned.isBlank() ? "user" : cleaned;
    }
}
