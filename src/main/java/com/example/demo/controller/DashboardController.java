package com.example.demo.controller;

import com.example.demo.entity.GradeBook;
import com.example.demo.entity.User;
import com.example.demo.repository.GradeBookRepository;
import com.example.demo.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@AllArgsConstructor
public class DashboardController {

    private final GradeBookRepository gradeBookRepository;
    private final UserRepository userRepository;

    @Data
    @AllArgsConstructor
    public static class PerformanceStat {
        private Long id;
        private String courseName;
        private String assessmentName;
        private double marksObtained;
        private double maxMarks;
        private double classAverage;
        private double classHighest;
        private double percentile;
    }

    @Data
    public static class AddGradeRequest {
        private String studentUsername;
        private String courseName;
        private String assessmentName;
        private double marksObtained;
        private double maxMarks;
    }

    @GetMapping("/performance")
    public ResponseEntity<?> getPerformance(Principal principal) {
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        // Fetch grades for this student
        List<GradeBook> studentGrades = gradeBookRepository.findByStudent(user);
        List<PerformanceStat> stats = new ArrayList<>();

        for (GradeBook grade : studentGrades) {
            // Find all scores for the same assessment in the class
            List<GradeBook> allScores = gradeBookRepository.findByCourseNameAndAssessmentName(
                    grade.getCourseName(), grade.getAssessmentName());

            double totalMarks = 0;
            double highest = 0;
            int countBelowOrEqual = 0;
            int totalCount = allScores.size();

            for (GradeBook s : allScores) {
                totalMarks += s.getMarksObtained();
                if (s.getMarksObtained() > highest) {
                    highest = s.getMarksObtained();
                }
                if (s.getMarksObtained() <= grade.getMarksObtained()) {
                    countBelowOrEqual++;
                }
            }

            double average = totalCount > 0 ? totalMarks / totalCount : 0;
            double percentile = totalCount > 0 ? ((double) countBelowOrEqual / totalCount) * 100 : 0;

            // Round to 2 decimal places
            average = Math.round(average * 100.0) / 100.0;
            highest = Math.round(highest * 100.0) / 100.0;
            percentile = Math.round(percentile * 100.0) / 100.0;

            stats.add(new PerformanceStat(
                    grade.getId(),
                    grade.getCourseName(),
                    grade.getAssessmentName(),
                    grade.getMarksObtained(),
                    grade.getMaxMarks(),
                    average,
                    highest,
                    percentile
            ));
        }

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/all-grades")
    public ResponseEntity<?> getAllGrades() {
        // Return all grades with student usernames for Teacher/CR dashboard view
        return ResponseEntity.ok(gradeBookRepository.findAll());
    }

    @PostMapping("/grades")
    public ResponseEntity<?> addGrade(@RequestBody AddGradeRequest request) {
        User student = userRepository.findByUsername(request.getStudentUsername()).orElse(null);
        if (student == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Student username not found"));
        }

        GradeBook grade = GradeBook.builder()
                .student(student)
                .courseName(request.getCourseName())
                .assessmentName(request.getAssessmentName())
                .marksObtained(request.getMarksObtained())
                .maxMarks(request.getMaxMarks())
                .build();

        GradeBook saved = gradeBookRepository.save(grade);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/grades/{id}")
    public ResponseEntity<?> deleteGrade(@PathVariable Long id) {
        if (!gradeBookRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        gradeBookRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Grade record deleted successfully"));
    }
}
