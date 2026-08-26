package com.ustc.learnx.controller;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignupRequest {
        private String username;
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

    @Data
    @AllArgsConstructor
    public static class UserResponse {
        private Long id;
        private String username;
        private String fullName;
        private String email;
        private String role;
        private String idNo;
        private String department;
        private String batch;
        private String semester;
        private String section;
        private String designation;
        private String profilePicUrl;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            HttpSession session = servletRequest.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
            
            Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (!user.isApproved()) {
                    SecurityContextHolder.clearContext();
                    if (session != null) {
                        session.invalidate();
                    }
                    return ResponseEntity.status(403).body(Map.of("error", "Your account is pending administrator approval."));
                }
                return ResponseEntity.ok(new UserResponse(
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
                        user.getProfilePicUrl()
                ));
            }
            // Authentication succeeded, so the account row must exist.
            throw new IllegalStateException("Authenticated principal has no account row");
        } catch (org.springframework.security.authentication.DisabledException e) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Your account is pending administrator approval."));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty() && userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email address is already registered"));
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
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Direct administrator registration is prohibited"));
        }

        String policyError = com.ustc.learnx.common.PasswordPolicy.validate(request.getPassword());
        if (policyError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", policyError));
        }

        // Enforce required fields
        if (request.getUsername() == null || request.getUsername().trim().isEmpty() ||
            request.getPassword() == null || request.getPassword().trim().isEmpty() ||
            request.getFullName() == null || request.getFullName().trim().isEmpty() ||
            request.getEmail() == null || request.getEmail().trim().isEmpty() ||
            request.getIdNo() == null || request.getIdNo().trim().isEmpty() ||
            request.getDepartment() == null || request.getDepartment().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please fill in all necessary registration fields."));
        }

        if (userRole == Role.STUDENT || userRole == Role.CR) {
            if (request.getSemester() == null || request.getSemester().trim().isEmpty() ||
                request.getBatch() == null || request.getBatch().trim().isEmpty() ||
                request.getSection() == null || request.getSection().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Students/CRs must fill in Semester, Batch, and Section."));
            }
        } else if (userRole == Role.TEACHER) {
            if (request.getDesignation() == null || request.getDesignation().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Teachers must specify Designation."));
            }
        }

        // Single-tenant deployment: every new account joins the one university.
        // This deliberately ignores any client-supplied domain hint.
        University uni = universityRepository.findAll().stream().findFirst().orElse(null);

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
            return ResponseEntity.badRequest().body(Map.of("error", optionError));
        }

        boolean approved = false;

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .role(userRole)
                .approved(approved)
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
            log.error("Failed to send the sign-up email to {}: {}", user.getEmail(), ex.getMessage());
        }

        return ResponseEntity.ok(Map.of("message", "Your form is under progress. You will be notified via email once approved."));
    }

    /**
     * Checks one submitted dropdown value against the list an administrator has
     * published for it.
     *
     * @return the message to report back, or null when the value is acceptable
     */
    private String checkOption(University university, String type, String label, String value) {
        List<String> permitted = systemMetadataRepository.findByType(type).stream()
                .filter(m -> m.getUniversity() == null
                        || university == null
                        || university.getId().equals(m.getUniversity().getId()))
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
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        Optional<User> userOpt = userRepository.findByUsername(principal.getName());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            return ResponseEntity.ok(new UserResponse(
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
                    user.getProfilePicUrl()
            ));
        }
        return ResponseEntity.status(401).body(Map.of("error", "User session invalid"));
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
