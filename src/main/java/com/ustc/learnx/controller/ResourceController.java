package com.ustc.learnx.controller;

import com.ustc.learnx.entity.Resource;
import com.ustc.learnx.entity.ResourceReaction;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.ResourceReactionRepository;
import com.ustc.learnx.repository.ResourceRepository;
import com.ustc.learnx.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/resources")
@AllArgsConstructor
public class ResourceController {

    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final ResourceReactionRepository resourceReactionRepository;
    private final com.ustc.learnx.service.CurrentUserService currentUserService;

    @GetMapping("/approved")
    public ResponseEntity<List<Resource>> getApprovedResources(Principal principal) {
        List<Resource> list;
        User user = null;
        if (principal != null) {
            user = userRepository.findByUsername(principal.getName()).orElse(null);
        }

        if (user != null && (user.getRole() == User.Role.STUDENT || user.getRole() == User.Role.CR)) {
            if (user.getStudentClass() != null) {
                list = resourceRepository.findByStudentClassAndApproved(user.getStudentClass(), true);
            } else {
                list = resourceRepository.findByApproved(true);
            }
        } else {
            list = resourceRepository.findByApproved(true);
        }

        // Populate reaction statistics
        populateReactions(list, user != null ? user.getUsername() : null);

        // Sort by net reactions (likes - dislikes) descending, then by id descending
        list.sort((a, b) -> {
            int scoreA = a.getLikesCount() - a.getDislikesCount();
            int scoreB = b.getLikesCount() - b.getDislikesCount();
            if (scoreA != scoreB) {
                return Integer.compare(scoreB, scoreA);
            }
            return Long.compare(b.getId(), a.getId());
        });

        return ResponseEntity.ok(list);
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/pending")
    public ResponseEntity<List<Resource>> getPendingResources(Principal principal) {
        List<Resource> list = resourceRepository.findByApproved(false);
        String username = principal != null ? principal.getName() : null;
        populateReactions(list, username);
        return ResponseEntity.ok(list);
    }

    private void populateReactions(List<Resource> resources, String currentUsername) {
        for (Resource r : resources) {
            r.setFileData(null); // Clear raw data for listing efficiency
            List<ResourceReaction> reactions = resourceReactionRepository.findByResourceId(r.getId());
            int likes = 0;
            int dislikes = 0;
            String userReaction = null;

            for (ResourceReaction rx : reactions) {
                if ("LIKE".equalsIgnoreCase(rx.getReactionType())) {
                    likes++;
                } else if ("DISLIKE".equalsIgnoreCase(rx.getReactionType())) {
                    dislikes++;
                }
                if (currentUsername != null && currentUsername.equalsIgnoreCase(rx.getUsername())) {
                    userReaction = rx.getReactionType();
                }
            }

            r.setLikesCount(likes);
            r.setDislikesCount(dislikes);
            r.setUserReaction(userReaction);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadResource(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("courseName") String courseName,
            @RequestParam(value = "examTags", required = false) String examTags,
            @RequestParam(value = "driveLink", required = false) String driveLink,
            Principal principal) {
        
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            boolean approved = (user.getRole() == User.Role.TEACHER || user.getRole() == User.Role.CR);

            Resource resource = Resource.builder()
                    .title(title)
                    .courseName(courseName)
                    .approved(approved)
                    .examTags(examTags)
                    .driveLink(driveLink != null && !driveLink.trim().isEmpty() ? driveLink.trim() : null)
                    .studentClass(user.getStudentClass())
                    .uploadedBy(user)
                    .build();

            if (file != null && !file.isEmpty()) {
                resource.setFileName(file.getOriginalFilename());
                resource.setContentType(file.getContentType());
                resource.setFileData(file.getBytes());
            } else {
                resource.setFileName("Google Drive Link");
                resource.setContentType("application/octet-stream");
            }

            Resource saved = resourceRepository.save(resource);
            saved.setFileData(null);
            return ResponseEntity.ok(saved);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload file: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveResource(@PathVariable Long id) {
        Resource resource = resourceRepository.findById(id).orElse(null);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        resource.setApproved(true);
        resourceRepository.save(resource);
        return ResponseEntity.ok(Map.of("message", "Resource approved successfully"));
    }

    /** Uploaders may remove their own material; teachers may remove any of it. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteResource(@PathVariable Long id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new com.ustc.learnx.common.NotFoundException("Resource not found"));

        currentUserService.assertSameUniversity(resource.getUniversity());

        User me = currentUserService.requireCurrentUser();
        boolean isOwner = resource.getUploadedBy() != null
                && resource.getUploadedBy().getId().equals(me.getId());
        boolean isModerator = me.getRole() == User.Role.TEACHER || me.getRole() == User.Role.ADMIN;
        if (!isOwner && !isModerator) {
            throw new com.ustc.learnx.common.AccessDeniedException("You may only delete your own uploads");
        }

        resourceRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Resource deleted successfully"));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadResource(@PathVariable Long id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new com.ustc.learnx.common.NotFoundException("Resource not found"));
        if (resource.getFileData() == null) {
            throw new com.ustc.learnx.common.NotFoundException("Resource has no file attached");
        }

        // A file is downloadable only within its own university, and only once
        // approved unless the caller uploaded it or moderates the library.
        currentUserService.assertSameUniversity(resource.getUniversity());
        if (!resource.isApproved()) {
            User me = currentUserService.requireCurrentUser();
            boolean isOwner = resource.getUploadedBy() != null
                    && resource.getUploadedBy().getId().equals(me.getId());
            boolean isModerator = me.getRole() == User.Role.TEACHER || me.getRole() == User.Role.ADMIN;
            if (!isOwner && !isModerator) {
                throw new com.ustc.learnx.common.AccessDeniedException("That resource is awaiting approval");
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(safeFileName(resource.getFileName()))
                                .build().toString())
                .contentType(MediaType.parseMediaType(resource.getContentType() != null
                        ? resource.getContentType() : "application/octet-stream"))
                .body(resource.getFileData());
    }

    /** Strips characters that would let a stored filename break out of the header. */
    private static String safeFileName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "download";
        }
        StringBuilder cleaned = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            cleaned.append((c == 0x0D || c == 0x0A || c == 0x22 || c == 0x5C || c == 0x2F) ? '_' : c);
        }
        String result = cleaned.toString().trim();
        return result.isEmpty() ? "download" : result;
    }

    // --- REACTION ENDPOINTS ---
    @PostMapping("/{id}/react")
    public ResponseEntity<?> reactToResource(
            @PathVariable Long id,
            @RequestParam("type") String type,
            Principal principal) {
        
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        
        if (!"LIKE".equalsIgnoreCase(type) && !"DISLIKE".equalsIgnoreCase(type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reaction type must be LIKE or DISLIKE"));
        }

        Optional<ResourceReaction> existing = resourceReactionRepository.findByResourceIdAndUsername(id, principal.getName());
        
        if (existing.isPresent()) {
            ResourceReaction rx = existing.get();
            rx.setReactionType(type.toUpperCase());
            resourceReactionRepository.save(rx);
        } else {
            ResourceReaction rx = ResourceReaction.builder()
                    .resourceId(id)
                    .username(principal.getName())
                    .reactionType(type.toUpperCase())
                    .build();
            resourceReactionRepository.save(rx);
        }

        return ResponseEntity.ok(Map.of("message", "Reaction saved successfully"));
    }

    @DeleteMapping("/{id}/react")
    public ResponseEntity<?> removeReaction(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        
        Optional<ResourceReaction> existing = resourceReactionRepository.findByResourceIdAndUsername(id, principal.getName());
        if (existing.isPresent()) {
            resourceReactionRepository.delete(existing.get());
            return ResponseEntity.ok(Map.of("message", "Reaction removed successfully"));
        }
        
        return ResponseEntity.ok(Map.of("message", "No reaction found to remove"));
    }
}
