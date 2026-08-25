package com.ustc.learnx.controller;

import com.ustc.learnx.common.AccessDeniedException;
import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.entity.Resource;
import com.ustc.learnx.entity.ResourceReaction;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.ResourceReactionRepository;
import com.ustc.learnx.repository.ResourceRepository;
import com.ustc.learnx.repository.UserRepository;
import com.ustc.learnx.service.CurrentUserService;
import com.ustc.learnx.service.storage.FileStorageService;
import lombok.AllArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.Comparator;
import java.util.HashMap;
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
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;

    @GetMapping("/approved")
    public ResponseEntity<List<Resource>> getApprovedResources(Principal principal) {
        User user = principal == null ? null
                : userRepository.findByUsername(principal.getName()).orElse(null);

        boolean scopedToClass = user != null
                && (user.getRole() == User.Role.STUDENT || user.getRole() == User.Role.CR)
                && user.getStudentClass() != null;

        List<Resource> list = scopedToClass
                ? resourceRepository.findByStudentClassAndApproved(user.getStudentClass(), true)
                : resourceRepository.findByApproved(true);

        populateReactions(list, user != null ? user.getUsername() : null);

        // Most appreciated first, then newest.
        list.sort(Comparator
                .comparingInt((Resource r) -> r.getLikesCount() - r.getDislikesCount())
                .thenComparing(Resource::getId)
                .reversed());

        return ResponseEntity.ok(list);
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/pending")
    public ResponseEntity<List<Resource>> getPendingResources(Principal principal) {
        List<Resource> list = resourceRepository.findByApproved(false);
        populateReactions(list, principal != null ? principal.getName() : null);
        return ResponseEntity.ok(list);
    }

    /**
     * Fills in reaction counts for a listing using two queries in total, rather
     * than one per resource.
     */
    private void populateReactions(List<Resource> resources, String currentUsername) {
        if (resources.isEmpty()) {
            return;
        }
        List<Long> ids = resources.stream().map(Resource::getId).toList();

        Map<Long, int[]> counts = new HashMap<>();
        for (Object[] row : resourceReactionRepository.countByResourceIds(ids)) {
            Long resourceId = (Long) row[0];
            String type = (String) row[1];
            int count = ((Number) row[2]).intValue();
            int[] tally = counts.computeIfAbsent(resourceId, key -> new int[2]);
            if ("LIKE".equalsIgnoreCase(type)) {
                tally[0] += count;
            } else if ("DISLIKE".equalsIgnoreCase(type)) {
                tally[1] += count;
            }
        }

        Map<Long, String> ownReactions = new HashMap<>();
        if (currentUsername != null) {
            for (ResourceReaction rx : resourceReactionRepository
                    .findByResourceIdInAndUsername(ids, currentUsername)) {
                ownReactions.put(rx.getResourceId(), rx.getReactionType());
            }
        }

        for (Resource r : resources) {
            int[] tally = counts.getOrDefault(r.getId(), new int[2]);
            r.setLikesCount(tally[0]);
            r.setDislikesCount(tally[1]);
            r.setUserReaction(ownReactions.get(r.getId()));
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

        boolean approved = (user.getRole() == User.Role.TEACHER || user.getRole() == User.Role.CR);

        Resource resource = Resource.builder()
                .title(title)
                .courseName(courseName)
                .approved(approved)
                .examTags(examTags)
                .driveLink(driveLink != null && !driveLink.trim().isEmpty() ? driveLink.trim() : null)
                .studentClass(user.getStudentClass())
                .university(user.getUniversity())
                .uploadedBy(user)
                .build();

        if (file != null && !file.isEmpty()) {
            // Streamed to disk; the row stores only a key.
            FileStorageService.StoredFile stored = fileStorageService.store(file);
            resource.setFileName(file.getOriginalFilename());
            resource.setContentType(file.getContentType());
            resource.setStorageKey(stored.storageKey());
            resource.setFileSize(stored.size());
            resource.setSha256(stored.sha256());
        } else {
            resource.setFileName("Google Drive Link");
            resource.setContentType("application/octet-stream");
        }

        return ResponseEntity.ok(resourceRepository.save(resource));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveResource(@PathVariable Long id) {
        Resource resource = requireResource(id);
        currentUserService.assertSameUniversity(resource.getUniversity());
        resource.setApproved(true);
        resourceRepository.save(resource);
        return ResponseEntity.ok(Map.of("message", "Resource approved successfully"));
    }

    /** Uploaders may remove their own material; teachers may remove any of it. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteResource(@PathVariable Long id) {
        Resource resource = requireResource(id);
        currentUserService.assertSameUniversity(resource.getUniversity());
        requireOwnerOrModerator(resource, "You may only delete your own uploads");

        String storageKey = resource.getStorageKey();
        resourceReactionRepository.deleteByResourceId(id);
        resourceRepository.deleteById(id);
        // Only once the row is gone, so a failure here cannot orphan the record.
        fileStorageService.delete(storageKey);

        return ResponseEntity.ok(Map.of("message", "Resource deleted successfully"));
    }

    /**
     * Streams the stored file back.
     *
     * <p>Returning a {@code Resource} lets Spring MVC copy it to the response
     * directly, so the file never has to be held in memory.
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadResource(@PathVariable Long id) {
        Resource resource = requireResource(id);
        if (resource.getStorageKey() == null) {
            throw new NotFoundException("Resource has no file attached");
        }

        // Downloadable only within its own university, and only once approved
        // unless the caller uploaded it or moderates the library.
        currentUserService.assertSameUniversity(resource.getUniversity());
        if (!resource.isApproved()) {
            requireOwnerOrModerator(resource, "That resource is awaiting approval");
        }

        org.springframework.core.io.Resource body = fileStorageService.load(resource.getStorageKey());

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(safeFileName(resource.getFileName()))
                                .build().toString())
                .contentType(MediaType.parseMediaType(resource.getContentType() != null
                        ? resource.getContentType() : "application/octet-stream"));
        if (resource.getFileSize() != null) {
            response.contentLength(resource.getFileSize());
        }
        return response.body(body);
    }

    private Resource requireResource(Long id) {
        return resourceRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Resource not found"));
    }

    private void requireOwnerOrModerator(Resource resource, String message) {
        User me = currentUserService.requireCurrentUser();
        boolean isOwner = resource.getUploadedBy() != null
                && resource.getUploadedBy().getId().equals(me.getId());
        boolean isModerator = me.getRole() == User.Role.TEACHER
                || me.getRole() == User.Role.ADMIN
                || me.getRole() == User.Role.SYSTEM_ADMIN;
        if (!isOwner && !isModerator) {
            throw new AccessDeniedException(message);
        }
    }

    /** Strips characters that would let a stored filename break out of the header. */
    private static String safeFileName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "download";
        }
        StringBuilder cleaned = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            boolean unsafe = c == 0x0D || c == 0x0A || c == 0x22 || c == 0x5C || c == 0x2F;
            cleaned.append(unsafe ? '_' : c);
        }
        String result = cleaned.toString().trim();
        return result.isEmpty() ? "download" : result;
    }

    // --- Reactions ---

    @PostMapping("/{id}/react")
    public ResponseEntity<?> reactToResource(
            @PathVariable Long id,
            @RequestParam("type") String type,
            Principal principal) {

        if (!"LIKE".equalsIgnoreCase(type) && !"DISLIKE".equalsIgnoreCase(type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reaction type must be LIKE or DISLIKE"));
        }
        Resource resource = requireResource(id);
        currentUserService.assertSameUniversity(resource.getUniversity());

        Optional<ResourceReaction> existing =
                resourceReactionRepository.findByResourceIdAndUsername(id, principal.getName());

        ResourceReaction reaction = existing.orElseGet(() -> ResourceReaction.builder()
                .resourceId(id)
                .username(principal.getName())
                .build());
        reaction.setReactionType(type.toUpperCase());
        resourceReactionRepository.save(reaction);

        return ResponseEntity.ok(Map.of("message", "Reaction saved successfully"));
    }

    @DeleteMapping("/{id}/react")
    public ResponseEntity<?> removeReaction(@PathVariable Long id, Principal principal) {
        resourceReactionRepository.findByResourceIdAndUsername(id, principal.getName())
                .ifPresent(resourceReactionRepository::delete);
        return ResponseEntity.ok(Map.of("message", "Reaction removed successfully"));
    }
}
