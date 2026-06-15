package com.example.demo.controller;

import com.example.demo.entity.Announcement;
import com.example.demo.entity.User;
import com.example.demo.repository.AnnouncementRepository;
import com.example.demo.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/announcements")
@AllArgsConstructor
public class AnnouncementController {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateAnnouncementRequest {
        private String title;
        private String content;
        private boolean global;
    }

    @GetMapping
    public ResponseEntity<?> getAnnouncements(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = userRepository.findByUsername(principal.getName()).orElse(null);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }

        List<Announcement> announcements;
        // If Student or CR, show global announcements + class-specific announcements
        if (currentUser.getRole() == User.Role.STUDENT || currentUser.getRole() == User.Role.CR) {
            if (currentUser.getStudentClass() != null) {
                announcements = announcementRepository.findByStudentClassOrStudentClassIsNullOrderByCreatedAtDesc(currentUser.getStudentClass());
            } else {
                announcements = announcementRepository.findAllByOrderByCreatedAtDesc();
            }
        } else {
            // Admin and Teacher can see all announcements
            announcements = announcementRepository.findAllByOrderByCreatedAtDesc();
        }

        return ResponseEntity.ok(announcements);
    }

    @PostMapping
    public ResponseEntity<?> createAnnouncement(@RequestBody CreateAnnouncementRequest request, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = userRepository.findByUsername(principal.getName()).orElse(null);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }

        // Only Admin, Teacher, and CR can create announcements
        if (currentUser.getRole() == User.Role.STUDENT) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only teachers, CRs, and administrators can publish announcements."));
        }

        Announcement announcement = Announcement.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .createdAt(LocalDateTime.now())
                .createdBy(currentUser.getFullName())
                .createdByRole(currentUser.getRole().name())
                .studentClass(request.isGlobal() ? null : currentUser.getStudentClass())
                .build();

        // If CR creates, always target their class (cannot create global announcements)
        if (currentUser.getRole() == User.Role.CR) {
            announcement.setStudentClass(currentUser.getStudentClass());
        }

        Announcement saved = announcementRepository.save(announcement);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAnnouncement(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = userRepository.findByUsername(principal.getName()).orElse(null);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }

        Announcement announcement = announcementRepository.findById(id).orElse(null);
        if (announcement == null) {
            return ResponseEntity.notFound().build();
        }

        // Only Admin, Teacher, or creator can delete
        boolean canDelete = currentUser.getRole() == User.Role.ADMIN || 
                            currentUser.getRole() == User.Role.TEACHER ||
                            announcement.getCreatedBy().equals(currentUser.getFullName());

        if (!canDelete) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to delete this announcement."));
        }

        announcementRepository.delete(announcement);
        return ResponseEntity.ok(Map.of("message", "Announcement deleted successfully"));
    }
}
