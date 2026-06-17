package com.example.demo.controller;

import com.example.demo.entity.AuditLog;
import com.example.demo.entity.ClassTest;
import com.example.demo.entity.ScheduleItem;
import com.example.demo.repository.AuditLogRepository;
import com.example.demo.repository.ClassTestRepository;
import com.example.demo.repository.ScheduleItemRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
@AllArgsConstructor
public class ScheduleController {

    private final ScheduleItemRepository scheduleItemRepository;
    private final ClassTestRepository classTestRepository;
    private final AuditLogRepository auditLogRepository;
    private final com.example.demo.repository.UserRepository userRepository;
    private final com.example.demo.repository.StudentClassRepository studentClassRepository;

    // --- Routine endpoints ---

    @GetMapping("/routine")
    public ResponseEntity<List<ScheduleItem>> getFullRoutine(
            @RequestParam(required = false) Long classId,
            Principal principal) {
        if (principal != null) {
            com.example.demo.entity.User user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user != null) {
                if (classId != null) {
                    com.example.demo.entity.StudentClass sc = studentClassRepository.findById(classId).orElse(null);
                    if (sc != null) {
                        if (user.getUniversity() != null && sc.getUniversity() != null &&
                            !sc.getUniversity().getId().equals(user.getUniversity().getId())) {
                            return ResponseEntity.status(403).body(List.of());
                        }
                        return ResponseEntity.ok(scheduleItemRepository.findByStudentClass(sc));
                    }
                }
                
                if (user.getRole() == com.example.demo.entity.User.Role.STUDENT || user.getRole() == com.example.demo.entity.User.Role.CR) {
                    if (user.getStudentClass() != null) {
                        return ResponseEntity.ok(scheduleItemRepository.findByStudentClass(user.getStudentClass()));
                    }
                    return ResponseEntity.ok(List.of());
                } else {
                    if (user.getUniversity() != null) {
                        return ResponseEntity.ok(scheduleItemRepository.findByUniversity(user.getUniversity()));
                    }
                }
            }
        }
        return ResponseEntity.ok(scheduleItemRepository.findAll());
    }

    @PostMapping("/routine")
    public ResponseEntity<?> addRoutineItem(@RequestBody ScheduleItem item, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        com.example.demo.entity.User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not found"));
        }
        if (user.getRole() != com.example.demo.entity.User.Role.CR && 
            user.getRole() != com.example.demo.entity.User.Role.TEACHER && 
            user.getRole() != com.example.demo.entity.User.Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden: Insufficient privileges"));
        }

        if (user.getRole() == com.example.demo.entity.User.Role.CR) {
            if (user.getStudentClass() == null) {
                return ResponseEntity.status(400).body(Map.of("error", "Bad Request: CR does not belong to a class"));
            }
            item.setStudentClass(user.getStudentClass());
        }
        if (user.getUniversity() != null) {
            item.setUniversity(user.getUniversity());
        }

        ScheduleItem saved = scheduleItemRepository.save(item);
        
        // Track Audit Log
        String details = String.format("Added Routine Class: Course '%s', Day '%s', Time %s-%s, Room '%s', Teacher '%s'",
                item.getCourseName(), item.getDayOfWeek(), item.getStartTime(), item.getEndTime(), item.getRoomNo(), item.getTeacherName());
        auditLogRepository.save(AuditLog.builder()
                .entityType("ROUTINE")
                .entityId(saved.getId())
                .action("CREATE")
                .changedBy(principal.getName())
                .timestamp(LocalDateTime.now())
                .details(details)
                .build());

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/routine/{id}")
    public ResponseEntity<?> updateRoutineItem(@PathVariable Long id, @RequestBody ScheduleItem updated, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        com.example.demo.entity.User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not found"));
        }
        if (user.getRole() != com.example.demo.entity.User.Role.CR && 
            user.getRole() != com.example.demo.entity.User.Role.TEACHER && 
            user.getRole() != com.example.demo.entity.User.Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden: Insufficient privileges"));
        }

        ScheduleItem existing = scheduleItemRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        if (user.getRole() == com.example.demo.entity.User.Role.CR) {
            if (user.getStudentClass() == null || existing.getStudentClass() == null || 
                !existing.getStudentClass().getId().equals(user.getStudentClass().getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Forbidden: You cannot modify schedules for another class"));
            }
            updated.setStudentClass(user.getStudentClass());
        }

        String details = String.format("Updated Routine Class: Day changed from '%s' to '%s', Time from %s-%s to %s-%s, Course from '%s' to '%s', Room from '%s' to '%s', Teacher '%s' to '%s'",
                existing.getDayOfWeek(), updated.getDayOfWeek(), existing.getStartTime(), existing.getEndTime(), updated.getStartTime(), updated.getEndTime(),
                existing.getCourseName(), updated.getCourseName(), existing.getRoomNo(), updated.getRoomNo(), existing.getTeacherName(), updated.getTeacherName());

        existing.setDayOfWeek(updated.getDayOfWeek());
        existing.setStartTime(updated.getStartTime());
        existing.setEndTime(updated.getEndTime());
        existing.setCourseName(updated.getCourseName());
        existing.setRoomNo(updated.getRoomNo());
        existing.setTeacherName(updated.getTeacherName());
        if (updated.getStudentClass() != null) {
            existing.setStudentClass(updated.getStudentClass());
        }
        if (user.getUniversity() != null) {
            existing.setUniversity(user.getUniversity());
        }

        ScheduleItem saved = scheduleItemRepository.save(existing);

        auditLogRepository.save(AuditLog.builder()
                .entityType("ROUTINE")
                .entityId(id)
                .action("UPDATE")
                .changedBy(principal.getName())
                .timestamp(LocalDateTime.now())
                .details(details)
                .build());

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/routine/{id}")
    public ResponseEntity<?> deleteRoutineItem(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        com.example.demo.entity.User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not found"));
        }
        if (user.getRole() != com.example.demo.entity.User.Role.CR && 
            user.getRole() != com.example.demo.entity.User.Role.TEACHER && 
            user.getRole() != com.example.demo.entity.User.Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden: Insufficient privileges"));
        }

        ScheduleItem existing = scheduleItemRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        if (user.getRole() == com.example.demo.entity.User.Role.CR) {
            if (user.getStudentClass() == null || existing.getStudentClass() == null || 
                !existing.getStudentClass().getId().equals(user.getStudentClass().getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Forbidden: You cannot delete schedules for another class"));
            }
        }

        String details = String.format("Deleted Routine Class: Course '%s', Day '%s', Time %s-%s, Room '%s', Teacher '%s'",
                existing.getCourseName(), existing.getDayOfWeek(), existing.getStartTime(), existing.getEndTime(), existing.getRoomNo(), existing.getTeacherName());

        scheduleItemRepository.deleteById(id);

        auditLogRepository.save(AuditLog.builder()
                .entityType("ROUTINE")
                .entityId(id)
                .action("DELETE")
                .changedBy(principal.getName())
                .timestamp(LocalDateTime.now())
                .details(details)
                .build());

        return ResponseEntity.ok(Map.of("message", "Routine class deleted"));
    }

    // --- Class Test (CT) endpoints ---

    @GetMapping("/ct")
    public ResponseEntity<List<ClassTest>> getUpcomingCTs(
            @RequestParam(required = false) Long classId,
            Principal principal) {
        if (principal != null) {
            com.example.demo.entity.User user = userRepository.findByUsername(principal.getName()).orElse(null);
            if (user != null) {
                if (classId != null) {
                    com.example.demo.entity.StudentClass sc = studentClassRepository.findById(classId).orElse(null);
                    if (sc != null) {
                        if (user.getUniversity() != null && sc.getUniversity() != null &&
                            !sc.getUniversity().getId().equals(user.getUniversity().getId())) {
                            return ResponseEntity.status(403).body(List.of());
                        }
                        return ResponseEntity.ok(classTestRepository.findByStudentClassOrderByDateTimeAsc(sc));
                    }
                }
                
                if (user.getRole() == com.example.demo.entity.User.Role.STUDENT || user.getRole() == com.example.demo.entity.User.Role.CR) {
                    if (user.getStudentClass() != null) {
                        return ResponseEntity.ok(classTestRepository.findByStudentClassOrderByDateTimeAsc(user.getStudentClass()));
                    }
                    return ResponseEntity.ok(List.of());
                }
            }
        }
        return ResponseEntity.ok(classTestRepository.findAllByOrderByDateTimeAsc());
    }

    @PostMapping("/ct")
    public ResponseEntity<?> addClassTest(@RequestBody ClassTest ct, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        com.example.demo.entity.User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not found"));
        }

        if (ct.getStudentClass() == null) {
            if (user.getRole() == com.example.demo.entity.User.Role.CR || user.getRole() == com.example.demo.entity.User.Role.STUDENT) {
                ct.setStudentClass(user.getStudentClass());
            }
        }
        if (ct.getUniversity() == null) {
            ct.setUniversity(user.getUniversity());
        }

        ct.setCreatedBy(principal.getName());
        ClassTest saved = classTestRepository.save(ct);

        String details = String.format("Scheduled Class Test (CT): Course '%s', Date/Time '%s', Duration %d mins, Room '%s', Topic '%s'",
                ct.getCourseName(), ct.getDateTime(), ct.getDurationMinutes(), ct.getRoomNo(), ct.getTopic());

        auditLogRepository.save(AuditLog.builder()
                .entityType("CLASS_TEST")
                .entityId(saved.getId())
                .action("CREATE")
                .changedBy(principal.getName())
                .timestamp(LocalDateTime.now())
                .details(details)
                .build());

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/ct/{id}")
    public ResponseEntity<?> updateClassTest(@PathVariable Long id, @RequestBody ClassTest updated, Principal principal) {
        ClassTest existing = classTestRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        String details = String.format("Updated Class Test (CT): Course changed from '%s' to '%s', Date/Time from '%s' to '%s', Room from '%s' to '%s', Topic from '%s' to '%s'",
                existing.getCourseName(), updated.getCourseName(), existing.getDateTime(), updated.getDateTime(), 
                existing.getRoomNo(), updated.getRoomNo(), existing.getTopic(), updated.getTopic());

        existing.setCourseName(updated.getCourseName());
        existing.setDateTime(updated.getDateTime());
        existing.setDurationMinutes(updated.getDurationMinutes());
        existing.setRoomNo(updated.getRoomNo());
        existing.setTopic(updated.getTopic());

        ClassTest saved = classTestRepository.save(existing);

        auditLogRepository.save(AuditLog.builder()
                .entityType("CLASS_TEST")
                .entityId(id)
                .action("UPDATE")
                .changedBy(principal.getName())
                .timestamp(LocalDateTime.now())
                .details(details)
                .build());

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/ct/{id}")
    public ResponseEntity<?> deleteClassTest(@PathVariable Long id, Principal principal) {
        ClassTest existing = classTestRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        String details = String.format("Cancelled/Deleted Class Test (CT): Course '%s', Date/Time '%s', Room '%s', Topic '%s'",
                existing.getCourseName(), existing.getDateTime(), existing.getRoomNo(), existing.getTopic());

        classTestRepository.deleteById(id);

        auditLogRepository.save(AuditLog.builder()
                .entityType("CLASS_TEST")
                .entityId(id)
                .action("DELETE")
                .changedBy(principal.getName())
                .timestamp(LocalDateTime.now())
                .details(details)
                .build());

        return ResponseEntity.ok(Map.of("message", "Class test deleted"));
    }

    // --- Audit Logs endpoints ---

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(auditLogRepository.findAllByOrderByTimestampDesc());
    }
}
