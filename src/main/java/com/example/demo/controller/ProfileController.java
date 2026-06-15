package com.example.demo.controller;

import com.example.demo.entity.ProfileChangeRequest;
import com.example.demo.entity.User;
import com.example.demo.repository.ProfileChangeRequestRepository;
import com.example.demo.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@AllArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final ProfileChangeRequestRepository profileChangeRequestRepository;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileUpdateRequest {
        private String fullName;
        private String email;
        private String idNo;
        private String department;
        private String batch;
        private String semester;
        private String section;
        private String designation;
        private String profilePicUrl;
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateProfile(@RequestBody ProfileUpdateRequest request, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = userRepository.findByUsername(principal.getName()).orElse(null);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }

        // Instantly update the non-sensitive profile photo if provided
        if (request.getProfilePicUrl() != null) {
            currentUser.setProfilePicUrl(request.getProfilePicUrl());
            userRepository.save(currentUser);
        }

        boolean changed = false;
        
        if (request.getFullName() != null && !request.getFullName().equals(currentUser.getFullName())) changed = true;
        if (request.getEmail() != null && !request.getEmail().equals(currentUser.getEmail())) changed = true;
        if (request.getIdNo() != null && !request.getIdNo().equals(currentUser.getIdNo())) changed = true;
        if (request.getDepartment() != null && !request.getDepartment().equals(currentUser.getDepartment())) changed = true;
        if (request.getBatch() != null && !request.getBatch().equals(currentUser.getBatch())) changed = true;
        if (request.getSemester() != null && !request.getSemester().equals(currentUser.getSemester())) changed = true;
        if (request.getSection() != null && !request.getSection().equals(currentUser.getSection())) changed = true;
        if (request.getDesignation() != null && !request.getDesignation().equals(currentUser.getDesignation())) changed = true;

        if (!changed) {
            return ResponseEntity.ok(Map.of(
                "message", "Profile picture updated successfully.",
                "user", currentUser
            ));
        }

        ProfileChangeRequest changeRequest = ProfileChangeRequest.builder()
                .user(currentUser)
                .newFullName(request.getFullName())
                .newEmail(request.getEmail())
                .newIdNo(request.getIdNo())
                .newDepartment(request.getDepartment())
                .newBatch(request.getBatch())
                .newSemester(request.getSemester())
                .newSection(request.getSection())
                .newDesignation(request.getDesignation())
                .build();

        profileChangeRequestRepository.save(changeRequest);

        return ResponseEntity.ok(Map.of(
            "message", "Sensitive changes submitted. Administrator approval is required before they take effect.",
            "user", currentUser
        ));
    }
}
