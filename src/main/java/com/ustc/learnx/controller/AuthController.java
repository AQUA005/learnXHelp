package com.ustc.learnx.controller;

import com.ustc.learnx.common.AccessDeniedException;
import com.ustc.learnx.common.UnauthorizedException;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.entity.User.Role;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.repository.UserRepository;
import com.ustc.learnx.repository.UniversityRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Signing in, signing up and reading back the current account.
 *
 * <p>Failures here are thrown as {@link ValidationException},
 * {@link AccessDeniedException} and {@link UnauthorizedException} rather than returned as
 * {@code Map.of("error", …)} bodies. The frontend reads {@code message},
 * {@code detail} then {@code title} from a response; an {@code error} key
 * matches none of them, so every validation failure used to reach the user as
 * the unhelpful "Request failed (400)".
 */
@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UniversityRepository universityRepository;
    private final com.ustc.learnx.repository.SystemMetadataRepository systemMetadataRepository;
    private final org.springframework.mail.javamail.JavaMailSender mailSender;
    private final org.springframework.core.env.Environment env;
    private final com.ustc.learnx.service.UsernameGenerator usernameGenerator;

    /**
     * Credentials from the sign-in form.
     *
     * <p>{@code username} is accepted as an alias so a browser holding an older
     * bundle keeps working; {@link com.ustc.learnx.service.CustomUserDetailsService}
     * resolves either form.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @com.fasterxml.jackson.annotation.JsonAlias("username")
        private String email;
        private String password;
    }

    /**
     * A new account request.
     *
     * <p>No username: it is derived from the email by {@link UsernameGenerator}.
     * {@code universitySlug} says which university is being joined, and is
     * checked against a published one — signing up to an unlisted university
     * must be refused, or the platform owner's publish gate is decorative.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignupRequest {
        private String universitySlug;
        private String password;
        private String fullName;
        private String email;
        private String role; // STUDENT, CR, TEACHER
        private String idNo;
        private String department;
        private String batch;
        private String semester;
        private String section;
        private String designation;
    }

    /**
     * The account as the client sees it.
     *
     * <p>Built only through {@link #from(User)}. Both the login response and
     * {@code /current-user} return this shape, and the frontend stores the login
     * response directly as its session user — so the two must never drift.
     * Constructing it positionally at two call sites is how they would.
     */
    public record UserResponse(
            Long id,
            String username,
            String fullName,
            String email,
            String role,
            String idNo,
            String department,
            String batch,
            String semester,
            String section,
            String designation,
            String profilePicUrl,
            boolean approved,
            UniversitySummary university) {

        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getFullName(),
                    user.getEmail(),
                    user.getRole().name(),
                    user.getIdNo(),
                    user.getDepartment(),
                    user.getBatch(),
                    user.getSemester(),
                    user.getSection(),
                    user.getDesignation(),
                    user.getProfilePicUrl(),
                    user.isApproved(),
                    UniversitySummary.of(user.getUniversity()));
        }
    }

    /**
     * Which university the signed-in account belongs to, for branding the shell.
     *
     * <p>Null for a platform owner, who sits above any single university.
     */
    public record UniversitySummary(String slug, String name, String logoUrl) {

        static UniversitySummary of(University university) {
            return university == null ? null : new UniversitySummary(
                    university.getSlug(), university.getName(), university.getLogoUrl());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (DisabledException e) {
            // CustomUserDetailsService marks an unapproved account disabled.
            throw new AccessDeniedException("Your account is pending administrator approval.");
        } catch (AuthenticationException e) {
            // Deliberately the same answer for an unknown account and a wrong
            // password, so this cannot be used to enumerate accounts. Only
            // authentication failures land here: a server fault must surface as
            // a 500 rather than be reported to the user as bad credentials.
            throw new UnauthorizedException("Invalid email or password");
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        HttpSession session = servletRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());

        User user = userRepository.findByUsername(authentication.getName())
                // Authentication succeeded, so the account row must exist.
                .orElseThrow(() -> new IllegalStateException("Authenticated principal has no account row"));

        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ValidationException("An email address is required");
        }
        // Stored lower-cased so the unique constraint, which is case-sensitive,
        // cannot end up holding two rows that a sign-in could not tell apart.
        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new ValidationException("Email address is already registered");
        }

        // Only these roles may be self-selected. Administrator accounts are
        // created by an existing administrator, never by signing up.
        Role userRole;
        try {
            userRole = Role.valueOf(request.getRole().toUpperCase());
        } catch (Exception e) {
            userRole = Role.STUDENT;
        }
        if (userRole != Role.STUDENT && userRole != Role.CR && userRole != Role.TEACHER) {
            throw new ValidationException("Direct administrator registration is prohibited");
        }

        String policyError = com.ustc.learnx.common.PasswordPolicy.validate(request.getPassword());
        if (policyError != null) {
            throw new ValidationException(policyError);
        }

        // Enforce required fields
        if (request.getPassword() == null || request.getPassword().trim().isEmpty() ||
            request.getFullName() == null || request.getFullName().trim().isEmpty() ||
            request.getIdNo() == null || request.getIdNo().trim().isEmpty() ||
            request.getDepartment() == null || request.getDepartment().trim().isEmpty()) {
            throw new ValidationException("Please fill in all necessary registration fields.");
        }

        if (userRole == Role.STUDENT || userRole == Role.CR) {
            if (request.getSemester() == null || request.getSemester().trim().isEmpty() ||
                request.getBatch() == null || request.getBatch().trim().isEmpty() ||
                request.getSection() == null || request.getSection().trim().isEmpty()) {
                throw new ValidationException("Students/CRs must fill in Semester, Batch, and Section.");
            }
        } else if (userRole == Role.TEACHER) {
            if (request.getDesignation() == null || request.getDesignation().trim().isEmpty()) {
                throw new ValidationException("Teachers must specify Designation.");
            }
        }

        // The university being joined, named by the signup form. Only a
        // published one: an unlisted university is not open for registration,
        // and treating it as one would make the publish gate decorative.
        if (request.getUniversitySlug() == null || request.getUniversitySlug().isBlank()) {
            throw new ValidationException("Choose the university you are joining");
        }
        University uni = universityRepository
                .findBySlugAndPublishedTrue(request.getUniversitySlug().trim())
                .orElseThrow(() -> new ValidationException("That university is not open for registration"));

        // The form offers these as dropdowns, but a request posted straight at
        // the API carries whatever the sender chose to put in it. Where an
        // administrator has published a list of permitted values, hold the
        // submission to it: an invented department or semester matches no
        // class, routine or gradebook, and the mismatch only surfaces later as
        // a student who cannot see anything.
        String optionError = checkOption(uni, "DEPARTMENT", "Department", request.getDepartment());
        if (optionError == null && (userRole == Role.STUDENT || userRole == Role.CR)) {
            optionError = checkOption(uni, "BATCH", "Batch", request.getBatch());
            if (optionError == null) {
                optionError = checkOption(uni, "SEMESTER", "Semester", request.getSemester());
            }
            if (optionError == null) {
                optionError = checkOption(uni, "SECTION", "Section", request.getSection());
            }
        }
        if (optionError == null && userRole == Role.TEACHER) {
            optionError = checkOption(uni, "DESIGNATION", "Designation", request.getDesignation());
        }
        if (optionError != null) {
            throw new ValidationException(optionError);
        }

        User user = User.builder()
                .username(usernameGenerator.forEmail(email))
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(email)
                .role(userRole)
                // Every new account waits for an administrator.
                .approved(false)
                .idNo(request.getIdNo())
                .department(request.getDepartment())
                .batch(request.getBatch())
                .semester(request.getSemester())
                .section(request.getSection())
                .designation(request.getDesignation())
                .university(uni)
                .build();

        userRepository.save(user);

        try {
            org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
            String fromEmail = env.getProperty("learnx.mail.from");
            if (fromEmail != null && !fromEmail.isEmpty()) {
                message.setFrom(fromEmail);
            }
            message.setTo(user.getEmail());
            message.setSubject("LearnX Registration Under Review");
            message.setText("Hello " + user.getFullName() + ",\n\n"
                    + "Thank you for registering on LearnX.\n"
                    + "Your account request is currently pending administrator approval.\n"
                    + "You will receive another email once your account has been approved.\n\n"
                    + "Best regards,\n"
                    + "LearnX Team");
            mailSender.send(message);
        } catch (Exception ex) {
            // Warn, not error: the account was created, and the exception is
            // passed so the cause reaches the log rather than just its message.
            log.warn("Could not send the signup email to {}", user.getEmail(), ex);
        }

        return ResponseEntity.ok(Map.of("message",
                "Your form is under progress. You will be notified via email once approved."));
    }

    /**
     * Checks one submitted dropdown value against the list an administrator has
     * published for it.
     *
     * @return the message to report back, or null when the value is acceptable
     */
    private String checkOption(University university, String type, String label, String value) {
        // Scoped by the query. Reading every row and filtering in memory used to
        // let a row belonging to no university satisfy this check for all of
        // them; since V4 every row has one, so there is nothing to fall back to.
        List<String> permitted = systemMetadataRepository
                .findByTypeAndUniversity(type, university).stream()
                .map(com.ustc.learnx.entity.SystemMetadata::getValue)
                .toList();

        // Nothing published for this field, so it is free text by design — the
        // form renders it as a text box in exactly the same case.
        if (permitted.isEmpty()) {
            return null;
        }
        if (value == null || !permitted.contains(value.trim())) {
            return label + " must be one of the options offered on the form.";
        }
        return null;
    }

    @GetMapping("/current-user")
    public ResponseEntity<?> getCurrentUser(Principal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        Optional<User> userOpt = userRepository.findByUsername(principal.getName());
        if (userOpt.isEmpty()) {
            throw new UnauthorizedException("User session invalid");
        }
        return ResponseEntity.ok(UserResponse.from(userOpt.get()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
