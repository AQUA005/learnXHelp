package com.ustc.learnx.service;

import com.ustc.learnx.common.PasswordPolicy;
import com.ustc.learnx.common.Slugs;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.entity.SystemMetadata;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.SystemMetadataRepository;
import com.ustc.learnx.repository.UniversityRepository;
import com.ustc.learnx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bringing a new university onto the platform.
 *
 * <p>Three things have to happen together, which is why this is one transaction
 * rather than three calls from a controller:
 *
 * <ol>
 *   <li>the university row;</li>
 *   <li>its first administrator — without one, nobody can ever sign in to it,
 *       and the two used to be saved in separate transactions;</li>
 *   <li>a starter set of departments, semesters, batches, sections and
 *       designations. A tenant with none of these has empty dropdowns on its
 *       signup form, so every applicant types their own spelling of "CSE" and
 *       the class groups fragment one per spelling.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class UniversityProvisioningService {

    /**
     * What a new university starts with, so its signup form is never blank.
     *
     * <p>Departments are deliberately absent: they differ at every institution,
     * and inventing them would be worse than an empty list. Publishing is
     * refused until the administrator adds their own.
     */
    private static final Map<String, List<String>> STARTER_METADATA = Map.of(
            "SEMESTER", List.of(
                    "1st Year 1st Semester", "1st Year 2nd Semester",
                    "2nd Year 1st Semester", "2nd Year 2nd Semester",
                    "3rd Year 1st Semester", "3rd Year 2nd Semester",
                    "4th Year 1st Semester", "4th Year 2nd Semester"),
            "SECTION", List.of("Section A", "Section B", "Section C"),
            "DESIGNATION", List.of(
                    "Lecturer", "Assistant Professor", "Associate Professor", "Professor"));

    private final UniversityRepository universityRepository;
    private final UserRepository userRepository;
    private final SystemMetadataRepository systemMetadataRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /** Details of the university and the account that will administer it. */
    public record NewUniversity(
            String name,
            String domain,
            String description,
            String contactEmail,
            String adminFullName,
            String adminEmail,
            String adminPassword) {
    }

    @Transactional
    public University create(NewUniversity request, String createdBy) {
        String name = require(request.name(), "A university name is required");
        String domain = require(request.domain(), "A domain is required").toLowerCase(Locale.ROOT);
        String adminEmail = require(request.adminEmail(), "An administrator email is required")
                .toLowerCase(Locale.ROOT);

        if (universityRepository.existsByName(name)) {
            throw new ValidationException("A university with that name is already registered");
        }
        if (universityRepository.existsByDomain(domain)) {
            throw new ValidationException("That domain is already taken");
        }
        // Email is the sign-in credential, so a duplicate is a collision the
        // administrator would discover only by being unable to sign in.
        if (userRepository.existsByEmail(adminEmail)) {
            throw new ValidationException("That administrator email is already registered");
        }

        // Held to the same standard as an ordinary signup. It was not, which
        // meant the most powerful account in a tenant could have a one-character
        // password while its students could not.
        String policyError = PasswordPolicy.validate(request.adminPassword());
        if (policyError != null) {
            throw new ValidationException(policyError);
        }

        University university = universityRepository.save(University.builder()
                .name(name)
                .domain(domain)
                .slug(Slugs.uniqueFrom(name, universityRepository::existsBySlug))
                .description(blankToNull(request.description()))
                .contactEmail(blankToNull(request.contactEmail()))
                // Never published on creation. The administrator fills in the
                // profile first, and the platform owner decides when it is ready.
                .published(false)
                .build());

        userRepository.save(User.builder()
                .username(deriveAdminUsername(adminEmail))
                .password(passwordEncoder.encode(request.adminPassword()))
                .fullName(blankToNull(request.adminFullName()) == null
                        ? "University Administrator" : request.adminFullName().trim())
                .email(adminEmail)
                .role(User.Role.ADMIN)
                .approved(true)
                .university(university)
                .build());

        seedMetadata(university);

        auditService.record("UNIVERSITY", "CREATE", createdBy,
                "Registered '" + name + "' (" + university.getSlug() + ")");

        return university;
    }

    /**
     * Whether this university may be listed publicly.
     *
     * <p>Refused without departments: they are what the public page shows and
     * what the signup form asks for, so publishing without them advertises a
     * university nobody can actually complete a registration for.
     */
    @Transactional
    public University setPublished(University university, boolean published, String changedBy) {
        if (published && systemMetadataRepository
                .findByTypeAndUniversity("DEPARTMENT", university).isEmpty()) {
            throw new ValidationException(
                    "Add at least one department before publishing, or the signup form has "
                            + "nothing to offer.");
        }

        university.setPublished(published);
        University saved = universityRepository.save(university);

        auditService.record("UNIVERSITY", published ? "PUBLISH" : "UNPUBLISH", changedBy,
                (published ? "Published '" : "Unpublished '") + university.getName() + "'");

        return saved;
    }

    private void seedMetadata(University university) {
        STARTER_METADATA.forEach((type, values) -> values.forEach(value ->
                systemMetadataRepository.save(SystemMetadata.builder()
                        .type(type)
                        .value(value)
                        .university(university)
                        .build())));
    }

    /** As at signup: derived from the address, never chosen. */
    private String deriveAdminUsername(String email) {
        String base = email.substring(0, email.indexOf('@')).replaceAll("[^a-z0-9.]", "");
        String candidate = base.isBlank() ? "admin" : base;
        for (int suffix = 2; userRepository.existsByUsername(candidate); suffix++) {
            candidate = (base.isBlank() ? "admin" : base) + suffix;
        }
        return candidate;
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
