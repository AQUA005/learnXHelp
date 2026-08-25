package com.ustc.learnx.controller;

import com.ustc.learnx.service.ProfileService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    public record ProfileUpdateRequest(
            @Size(max = 255, message = "Name is too long") String fullName,
            String email,
            String idNo,
            String department,
            String batch,
            String semester,
            String section,
            String designation,
            /** A data URL holding the new avatar, when one is being changed. */
            String profilePicUrl) {
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateProfile(@RequestBody ProfileUpdateRequest request) {
        ProfileService.UpdateOutcome outcome = profileService.update(
                request.fullName(), request.profilePicUrl(),
                request.email(), request.idNo(), request.department(),
                request.batch(), request.semester(), request.section(),
                request.designation());

        String message = outcome.approvalRequired()
                ? "Sensitive changes submitted. Administrator approval is required before they take effect."
                : "Profile updated successfully.";

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("message", message);
        body.put("profilePicUrl", outcome.profilePicUrl());
        return ResponseEntity.ok(body);
    }

    /** Serves a member's avatar from storage. */
    @GetMapping("/avatar/{userId}")
    public ResponseEntity<Resource> getAvatar(@PathVariable Long userId) {
        ProfileService.Avatar avatar = profileService.loadAvatar(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                // Avatars change rarely, and the URL is per user.
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(avatar.content());
    }
}
