package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.entity.User.Role;
import com.example.demo.entity.SystemAdmin;
import com.example.demo.entity.University;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.SystemAdminRepository;
import com.example.demo.repository.UniversityRepository;
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
    private final SystemAdminRepository systemAdminRepository;
    private final UniversityRepository universityRepository;

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
            } else {
                Optional<SystemAdmin> sysAdminOpt = systemAdminRepository.findByUsername(request.getUsername());
                if (sysAdminOpt.isPresent()) {
                    SystemAdmin sysAdmin = sysAdminOpt.get();
                    return ResponseEntity.ok(new UserResponse(
                            sysAdmin.getId(),
                            sysAdmin.getUsername(),
                            sysAdmin.getFullName(),
                            sysAdmin.getEmail(),
                            "SYSTEM_ADMIN",
                            null, null, null, null, null, null, null
                    ));
                }
            }
            throw new RuntimeException("User not found in repositories");
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request, @RequestHeader(value = "X-University-Domain", required = false) String domainHeader) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty() && userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email address is already registered"));
        }

        Role userRole;
        try {
            userRole = Role.valueOf(request.getRole().toUpperCase());
            if (userRole == Role.ADMIN) {
                return ResponseEntity.badRequest().body(Map.of("error", "Direct administrator registration is prohibited"));
            }
        } catch (Exception e) {
            userRole = Role.STUDENT; // Default to Student
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

        University uni = null;
        if (domainHeader != null && !domainHeader.trim().isEmpty()) {
            uni = universityRepository.findByDomain(domainHeader).orElse(null);
        }
        if (uni == null) {
            java.util.List<University> all = universityRepository.findAll();
            if (!all.isEmpty()) {
                uni = all.get(0);
            }
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
        } else {
            Optional<SystemAdmin> sysAdminOpt = systemAdminRepository.findByUsername(principal.getName());
            if (sysAdminOpt.isPresent()) {
                SystemAdmin sysAdmin = sysAdminOpt.get();
                return ResponseEntity.ok(new UserResponse(
                        sysAdmin.getId(),
                        sysAdmin.getUsername(),
                        sysAdmin.getFullName(),
                        sysAdmin.getEmail(),
                        "SYSTEM_ADMIN",
                        null, null, null, null, null, null, null
                ));
            }
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
