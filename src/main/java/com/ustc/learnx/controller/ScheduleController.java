package com.ustc.learnx.controller;

import com.ustc.learnx.dto.ScheduleDtos.AuditLogResponse;
import com.ustc.learnx.dto.ScheduleDtos.ClassTestRequest;
import com.ustc.learnx.dto.ScheduleDtos.ClassTestResponse;
import com.ustc.learnx.dto.ScheduleDtos.RoutineItemRequest;
import com.ustc.learnx.dto.ScheduleDtos.RoutineItemResponse;
import com.ustc.learnx.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * The weekly routine, class tests, and the audit trail of changes to them.
 * Which class a change may touch is decided by {@link ScheduleService}.
 */
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/routine")
    public ResponseEntity<List<RoutineItemResponse>> getFullRoutine(
            @RequestParam(required = false) Long classId) {
        return ResponseEntity.ok(scheduleService.listRoutine(classId));
    }

    @PreAuthorize("hasRole('CR')")
    @PostMapping("/routine")
    public ResponseEntity<RoutineItemResponse> addRoutineItem(
            @Valid @RequestBody RoutineItemRequest request) {
        return ResponseEntity.ok(scheduleService.createRoutineItem(request));
    }

    @PreAuthorize("hasRole('CR')")
    @PutMapping("/routine/{id}")
    public ResponseEntity<RoutineItemResponse> updateRoutineItem(
            @PathVariable Long id, @Valid @RequestBody RoutineItemRequest request) {
        return ResponseEntity.ok(scheduleService.updateRoutineItem(id, request));
    }

    @PreAuthorize("hasRole('CR')")
    @DeleteMapping("/routine/{id}")
    public ResponseEntity<?> deleteRoutineItem(@PathVariable Long id) {
        scheduleService.deleteRoutineItem(id);
        return ResponseEntity.ok(Map.of("message", "Routine class deleted"));
    }

    @GetMapping("/ct")
    public ResponseEntity<List<ClassTestResponse>> getUpcomingCTs(
            @RequestParam(required = false) Long classId) {
        return ResponseEntity.ok(scheduleService.listClassTests(classId));
    }

    @PreAuthorize("hasRole('CR')")
    @PostMapping("/ct")
    public ResponseEntity<ClassTestResponse> addClassTest(
            @Valid @RequestBody ClassTestRequest request) {
        return ResponseEntity.ok(scheduleService.createClassTest(request));
    }

    @PreAuthorize("hasRole('CR')")
    @PutMapping("/ct/{id}")
    public ResponseEntity<ClassTestResponse> updateClassTest(
            @PathVariable Long id, @Valid @RequestBody ClassTestRequest request) {
        return ResponseEntity.ok(scheduleService.updateClassTest(id, request));
    }

    @PreAuthorize("hasRole('CR')")
    @DeleteMapping("/ct/{id}")
    public ResponseEntity<?> deleteClassTest(@PathVariable Long id) {
        scheduleService.deleteClassTest(id);
        return ResponseEntity.ok(Map.of("message", "Class test deleted"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs() {
        return ResponseEntity.ok(scheduleService.listAuditLogs());
    }
}
