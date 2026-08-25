package com.ustc.learnx.controller;

import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.entity.SystemMetadata;
import com.ustc.learnx.repository.SystemMetadataRepository;
import com.ustc.learnx.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * The lists behind the signup and class-administration dropdowns:
 * departments, semesters, batches and staff designations.
 */
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    /** The only lists the application recognises. */
    private static final List<String> ALLOWED_TYPES =
            List.of("SEMESTER", "DEPARTMENT", "BATCH", "SECTION", "DESIGNATION");

    private final SystemMetadataRepository systemMetadataRepository;
    private final CurrentUserService currentUserService;

    public record MetadataResponse(Long id, String type, String value) {
    }

    @GetMapping
    public ResponseEntity<List<MetadataResponse>> getAllMetadata() {
        Long universityId = currentUserService.currentUser()
                .map(u -> u.getUniversity() == null ? null : u.getUniversity().getId())
                .orElse(null);

        List<SystemMetadata> options = systemMetadataRepository.findAll().stream()
                .filter(m -> universityId == null
                        || m.getUniversity() == null
                        || universityId.equals(m.getUniversity().getId()))
                .toList();

        return ResponseEntity.ok(options.stream()
                .map(m -> new MetadataResponse(m.getId(), m.getType(), m.getValue()))
                .toList());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<MetadataResponse> createMetadata(@RequestBody Map<String, String> request) {
        String type = request.get("type");
        String value = request.get("value");

        if (type == null || type.isBlank() || value == null || value.isBlank()) {
            throw new ValidationException("Type and value are required");
        }

        String normalisedType = type.trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(normalisedType)) {
            throw new ValidationException(
                    "Type must be one of " + String.join(", ", ALLOWED_TYPES));
        }

        SystemMetadata saved = systemMetadataRepository.save(SystemMetadata.builder()
                .type(normalisedType)
                .value(value.trim())
                // Without this the entry belongs to no university, and the
                // scoped lookups behind the signup form never return it.
                .university(currentUserService.requireUniversity())
                .build());

        return ResponseEntity.ok(new MetadataResponse(saved.getId(), saved.getType(), saved.getValue()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMetadata(@PathVariable Long id) {
        SystemMetadata option = systemMetadataRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Metadata option not found"));
        currentUserService.assertSameUniversity(option.getUniversity());

        systemMetadataRepository.delete(option);
        return ResponseEntity.ok(Map.of("message", "Metadata option deleted"));
    }
}
