package com.ustc.learnx.controller;

import com.ustc.learnx.dto.AnnouncementDtos.AnnouncementResponse;
import com.ustc.learnx.dto.AnnouncementDtos.CreateAnnouncementRequest;
import com.ustc.learnx.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public ResponseEntity<List<AnnouncementResponse>> getAnnouncements() {
        return ResponseEntity.ok(announcementService.list());
    }

    @PreAuthorize("hasRole('CR')")
    @PostMapping
    public ResponseEntity<AnnouncementResponse> createAnnouncement(
            @Valid @RequestBody CreateAnnouncementRequest request) {
        return ResponseEntity.ok(announcementService.create(request));
    }

    @PreAuthorize("hasRole('CR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAnnouncement(@PathVariable Long id) {
        announcementService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Announcement deleted successfully"));
    }
}
