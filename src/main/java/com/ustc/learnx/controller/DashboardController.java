package com.ustc.learnx.controller;

import com.ustc.learnx.dto.GradeDtos.AddGradeRequest;
import com.ustc.learnx.dto.GradeDtos.GradeResponse;
import com.ustc.learnx.dto.GradeDtos.PerformanceStat;
import com.ustc.learnx.service.GradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final GradeService gradeService;

    @GetMapping("/performance")
    public ResponseEntity<List<PerformanceStat>> getPerformance() {
        return ResponseEntity.ok(gradeService.performanceForCurrentUser());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/all-grades")
    public ResponseEntity<List<GradeResponse>> getAllGrades() {
        return ResponseEntity.ok(gradeService.listAllGrades());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping("/grades")
    public ResponseEntity<GradeResponse> addGrade(@Valid @RequestBody AddGradeRequest request) {
        return ResponseEntity.ok(gradeService.addGrade(request));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @DeleteMapping("/grades/{id}")
    public ResponseEntity<?> deleteGrade(@PathVariable Long id) {
        gradeService.deleteGrade(id);
        return ResponseEntity.ok(Map.of("message", "Grade record deleted successfully"));
    }
}
