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
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UniversityRepository universityRepository;
    private final com.ustc.learnx.service.MailService mailService;

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

        // A failure here is recorded but does not fail the sign-up: the account
        // exists and an administrator can approve it regardless.
        mailService.send(user.getEmail(), "LearnX Registration Under Review", "Hello " + user.getFullName() + ",\n\n"
                    + "Thank you for registering on LearnX.\n"
                    + "Your account request is currently pending administrator approval.\n"
                    + "You will receive another email once your account has been approved.\n\n"
                    + "Best regards,\n"
                    + "LearnX Team");

        return ResponseEntity.ok(Map.of("message", "Your form is under progress. You will be notified via email once approved."));
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
