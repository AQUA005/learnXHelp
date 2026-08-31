package com.ustc.learnx.controller;

import com.ustc.learnx.dto.RoutineDtos.LiveRoutine;
import com.ustc.learnx.dto.RoutineDtos.OverrideRequest;
import com.ustc.learnx.dto.RoutineDtos.OverrideResponse;
import com.ustc.learnx.dto.RoutineDtos.SourceRequest;
import com.ustc.learnx.dto.RoutineDtos.SourceResponse;
import com.ustc.learnx.service.LiveRoutineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The routine as the university publishes it.
 *
 * <p>Reading is open to any member: the timetable is the least private thing a
 * university has. Pointing a department at a sheet is an administrator's job,
 * and announcing a cancellation is a class representative's.
 */
@RestController
@RequestMapping("/api/routine")
@RequiredArgsConstructor
public class RoutineController {

    private final LiveRoutineService liveRoutineService;

    /**
     * The week for one section.
     *
     * <p>The sheet parameters are the escape hatch for a department that has
     * not been configured yet: passing them reads that sheet for this request
     * only, and stores nothing.
     */
    @GetMapping("/live")
    public ResponseEntity<LiveRoutine> live(
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String sheet,
            @RequestParam(required = false) String dayGids,
            @RequestParam(required = false) String teacherGid,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(
                liveRoutineService.live(section, sheet, dayGids, teacherGid, refresh));
    }

    /** Where the caller's own routine is read from, for the setup panel. */
    @GetMapping("/source")
    public ResponseEntity<SourceResponse> mySource() {
        return ResponseEntity.ok(liveRoutineService.mySource());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/sources")
    public ResponseEntity<List<SourceResponse>> listSources() {
        return ResponseEntity.ok(liveRoutineService.listSources());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/sources")
    public ResponseEntity<SourceResponse> saveSource(@Valid @RequestBody SourceRequest request) {
        return ResponseEntity.ok(liveRoutineService.saveSource(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/sources/{id}")
    public ResponseEntity<?> deleteSource(@PathVariable Long id) {
        liveRoutineService.deleteSource(id);
        return ResponseEntity.ok(Map.of("message", "Routine source removed"));
    }

    @GetMapping("/overrides")
    public ResponseEntity<List<OverrideResponse>> overrides(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(liveRoutineService.listOverrides(from, to));
    }

    @PreAuthorize("hasRole('CR')")
    @PostMapping("/overrides")
    public ResponseEntity<OverrideResponse> addOverride(@Valid @RequestBody OverrideRequest request) {
        return ResponseEntity.ok(liveRoutineService.addOverride(request));
    }

    @PreAuthorize("hasRole('CR')")
    @DeleteMapping("/overrides/{id}")
    public ResponseEntity<?> deleteOverride(@PathVariable Long id) {
        liveRoutineService.deleteOverride(id);
        return ResponseEntity.ok(Map.of("message", "Change removed"));
    }
}
