package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.entity.User.Role;
import com.example.demo.entity.StudentClass;
import com.example.demo.entity.ClassCourseAssignment;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.StudentClassRepository;
import com.example.demo.repository.ClassCourseAssignmentRepository;
import com.example.demo.service.SlotDetectionService;
import com.example.demo.service.SlotDetectionService.TimeInterval;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/slots")
@AllArgsConstructor
public class SlotController {

    private final SlotDetectionService slotDetectionService;
    private final UserRepository userRepository;
    private final StudentClassRepository studentClassRepository;
    private final ClassCourseAssignmentRepository classCourseAssignmentRepository;

    @GetMapping("/detect")
    public ResponseEntity<?> getFreeSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "60") int duration,
            @RequestParam(required = false) Long classId,
            Principal principal) {
        
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not found"));
        }

        StudentClass targetClass = null;
        if (classId != null) {
            targetClass = studentClassRepository.findById(classId).orElse(null);
        } else {
            if (user.getRole() == Role.CR || user.getRole() == Role.STUDENT) {
                targetClass = user.getStudentClass();
            } else if (user.getRole() == Role.TEACHER) {
                List<ClassCourseAssignment> assignments = classCourseAssignmentRepository.findByTeacher(user);
                if (!assignments.isEmpty()) {
                    targetClass = assignments.get(0).getStudentClass();
                } else {
                    List<StudentClass> classes = studentClassRepository.findByUniversity(user.getUniversity());
                    if (!classes.isEmpty()) {
                        targetClass = classes.get(0);
                    }
                }
            } else if (user.getRole() == Role.ADMIN) {
                List<StudentClass> classes = studentClassRepository.findByUniversity(user.getUniversity());
                if (!classes.isEmpty()) {
                    targetClass = classes.get(0);
                }
            }
        }

        if (targetClass == null) {
            return ResponseEntity.ok(List.of());
        }

        List<TimeInterval> freeSlots = slotDetectionService.detectFreeSlots(date, duration, targetClass);
        return ResponseEntity.ok(freeSlots);
    }
}
