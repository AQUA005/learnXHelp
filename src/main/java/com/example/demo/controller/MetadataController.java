package com.example.demo.controller;

import com.example.demo.entity.SystemMetadata;
import com.example.demo.repository.SystemMetadataRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metadata")
@AllArgsConstructor
public class MetadataController {

    private final SystemMetadataRepository systemMetadataRepository;

    @GetMapping
    public ResponseEntity<?> getAllMetadata() {
        List<SystemMetadata> allOptions = systemMetadataRepository.findAll();
        return ResponseEntity.ok(allOptions);
    }

    @PostMapping
    public ResponseEntity<?> createMetadata(@RequestBody Map<String, String> request) {
        String type = request.get("type");
        String value = request.get("value");

        if (type == null || type.trim().isEmpty() || value == null || value.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Type and value are required."));
        }

        // Standardize types
        String typeUpper = type.trim().toUpperCase();
        if (!List.of("SEMESTER", "DEPARTMENT", "BATCH", "DESIGNATION").contains(typeUpper)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid metadata type. Must be SEMESTER, DEPARTMENT, BATCH, or DESIGNATION."));
        }

        SystemMetadata option = SystemMetadata.builder()
                .type(typeUpper)
                .value(value.trim())
                .build();

        systemMetadataRepository.save(option);
        return ResponseEntity.ok(option);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMetadata(@PathVariable Long id) {
        if (!systemMetadataRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        systemMetadataRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Metadata option deleted successfully."));
    }
}
