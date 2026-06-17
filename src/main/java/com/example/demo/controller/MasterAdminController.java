package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.entity.User.Role;
import com.example.demo.repository.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/master")
@AllArgsConstructor
public class MasterAdminController {

    private final UniversityRepository universityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResourceReactionRepository resourceReactionRepository;
    private final ResourceRepository resourceRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamRepository examRepository;
    private final GradeBookRepository gradeBookRepository;
    private final ProfileChangeRequestRepository profileChangeRequestRepository;
    private final PromotionHistoryRepository promotionHistoryRepository;
    private final ClassCourseAssignmentRepository classCourseAssignmentRepository;
    private final ClassTestRepository classTestRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final AnnouncementRepository announcementRepository;
    private final StudentClassRepository studentClassRepository;
    private final CourseRepository courseRepository;
    private final SystemMetadataRepository systemMetadataRepository;
    private final SystemAdminRepository systemAdminRepository;
    private final BugReportRepository bugReportRepository;
    private final org.springframework.mail.javamail.JavaMailSender mailSender;
    private final org.springframework.core.env.Environment env;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UniversityRegistrationRequest {
        private String name;
        private String domain;
        private String logoUrl;
        private String adminUsername;
        private String adminPassword;
        private String adminFullName;
        private String adminEmail;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UniversityUpdateRequest {
        private String name;
        private String domain;
        private String logoUrl;
    }

    @PostMapping("/universities")
    public ResponseEntity<?> registerUniversity(@RequestBody UniversityRegistrationRequest request) {
        if (universityRepository.existsByName(request.getName())) {
            return ResponseEntity.badRequest().body(Map.of("error", "University name already registered"));
        }
        if (universityRepository.existsByDomain(request.getDomain())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Subdomain already taken"));
        }
        if (userRepository.existsByUsername(request.getAdminUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Admin username already exists"));
        }

        // 1. Create and save University
        University uni = University.builder()
                .name(request.getName())
                .domain(request.getDomain())
                .logoUrl(request.getLogoUrl())
                .build();
        uni = universityRepository.save(uni);

        // 2. Create University Admin user
        User admin = User.builder()
                .username(request.getAdminUsername())
                .password(passwordEncoder.encode(request.getAdminPassword()))
                .fullName(request.getAdminFullName())
                .email(request.getAdminEmail())
                .role(Role.ADMIN) // University admin
                .approved(true)
                .university(uni)
                .build();
        userRepository.save(admin);

        return ResponseEntity.ok(uni);
    }

    @GetMapping("/universities")
    public ResponseEntity<List<University>> listUniversities() {
        return ResponseEntity.ok(universityRepository.findAll());
    }

    @PutMapping("/universities/{id}")
    public ResponseEntity<?> updateUniversity(@PathVariable Long id, @RequestBody UniversityUpdateRequest request) {
        Optional<University> uniOpt = universityRepository.findById(id);
        if (uniOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        University uni = uniOpt.get();

        // Check conflicts
        Optional<University> existingName = universityRepository.findByName(request.getName());
        if (existingName.isPresent() && !existingName.get().getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "University name already taken"));
        }
        Optional<University> existingDomain = universityRepository.findByDomain(request.getDomain());
        if (existingDomain.isPresent() && !existingDomain.get().getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Subdomain already taken"));
        }

        uni.setName(request.getName());
        uni.setDomain(request.getDomain());
        if (request.getLogoUrl() != null) {
            uni.setLogoUrl(request.getLogoUrl());
        }
        universityRepository.save(uni);
        return ResponseEntity.ok(uni);
    }

    @PostMapping("/universities/{id}/reset-admin")
    public ResponseEntity<?> resetAdminPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Optional<University> uniOpt = universityRepository.findById(id);
        if (uniOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String username = body.get("adminUsername");
        String password = body.get("adminPassword");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Admin username and password are required"));
        }

        // Find or create admin user for this university
        Optional<User> adminOpt = userRepository.findByUsername(username);
        User admin;
        if (adminOpt.isPresent()) {
            admin = adminOpt.get();
            if (!admin.getRole().equals(Role.ADMIN) || admin.getUniversity() == null || !admin.getUniversity().getId().equals(id)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username belongs to another user role or university"));
            }
            admin.setPassword(passwordEncoder.encode(password));
        } else {
            admin = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .fullName("University Admin")
                    .email("admin@" + uniOpt.get().getDomain())
                    .role(Role.ADMIN)
                    .approved(true)
                    .university(uniOpt.get())
                    .build();
        }
        userRepository.save(admin);
        return ResponseEntity.ok(Map.of("message", "University admin account successfully updated"));
    }

    @DeleteMapping("/universities/{id}")
    @Transactional
    public ResponseEntity<?> deleteUniversity(@PathVariable Long id) {
        Optional<University> uniOpt = universityRepository.findById(id);
        if (uniOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        University uni = uniOpt.get();

        // 1. Delete Resource Reactions
        List<ResourceReaction> reactions = resourceReactionRepository.findAll().stream()
                .filter(rr -> {
                    Optional<User> u = userRepository.findByUsername(rr.getUsername());
                    if (u.isPresent() && u.get().getUniversity() != null && u.get().getUniversity().getId().equals(id)) {
                        return true;
                    }
                    Optional<Resource> r = resourceRepository.findById(rr.getResourceId());
                    return r.isPresent() && r.get().getUniversity() != null && r.get().getUniversity().getId().equals(id);
                })
                .toList();
        resourceReactionRepository.deleteAll(reactions);

        // 2. Delete Resources
        List<Resource> resources = resourceRepository.findAll().stream()
                .filter(r -> r.getUniversity() != null && r.getUniversity().getId().equals(id))
                .toList();
        resourceRepository.deleteAll(resources);

        // 3. Delete Exam Submissions
        List<ExamSubmission> submissions = examSubmissionRepository.findAll().stream()
                .filter(es -> (es.getStudent() != null && es.getStudent().getUniversity() != null && es.getStudent().getUniversity().getId().equals(id)) ||
                              (es.getExam() != null && es.getExam().getUniversity() != null && es.getExam().getUniversity().getId().equals(id)))
                .toList();
        examSubmissionRepository.deleteAll(submissions);

        // 4. Delete Exam Questions
        List<ExamQuestion> questions = examQuestionRepository.findAll().stream()
                .filter(eq -> eq.getExam() != null && eq.getExam().getUniversity() != null && eq.getExam().getUniversity().getId().equals(id))
                .toList();
        examQuestionRepository.deleteAll(questions);

        // 5. Delete Exams
        List<Exam> exams = examRepository.findAll().stream()
                .filter(e -> e.getUniversity() != null && e.getUniversity().getId().equals(id))
                .toList();
        examRepository.deleteAll(exams);

        // 6. Delete GradeBook entries
        List<GradeBook> grades = gradeBookRepository.findAll().stream()
                .filter(g -> g.getStudent() != null && g.getStudent().getUniversity() != null && g.getStudent().getUniversity().getId().equals(id))
                .toList();
        gradeBookRepository.deleteAll(grades);

        // 7. Delete ProfileChangeRequests
        List<ProfileChangeRequest> profileRequests = profileChangeRequestRepository.findAll().stream()
                .filter(p -> p.getUser() != null && p.getUser().getUniversity() != null && p.getUser().getUniversity().getId().equals(id))
                .toList();
        profileChangeRequestRepository.deleteAll(profileRequests);

        // 8. Delete PromotionHistory
        List<PromotionHistory> promotions = promotionHistoryRepository.findAll().stream()
                .filter(p -> p.getStudentClass() != null && p.getStudentClass().getUniversity() != null && p.getStudentClass().getUniversity().getId().equals(id))
                .toList();
        promotionHistoryRepository.deleteAll(promotions);

        // 9. Delete ClassCourseAssignments
        List<ClassCourseAssignment> assignments = classCourseAssignmentRepository.findAll().stream()
                .filter(cca -> (cca.getCourse() != null && cca.getCourse().getUniversity() != null && cca.getCourse().getUniversity().getId().equals(id)) ||
                               (cca.getStudentClass() != null && cca.getStudentClass().getUniversity() != null && cca.getStudentClass().getUniversity().getId().equals(id)) ||
                               (cca.getTeacher() != null && cca.getTeacher().getUniversity() != null && cca.getTeacher().getUniversity().getId().equals(id)))
                .toList();
        classCourseAssignmentRepository.deleteAll(assignments);

        // 10. Delete ClassTests
        List<ClassTest> tests = classTestRepository.findAll().stream()
                .filter(t -> t.getUniversity() != null && t.getUniversity().getId().equals(id))
                .toList();
        classTestRepository.deleteAll(tests);

        // 11. Delete ScheduleItems
        List<ScheduleItem> scheduleItems = scheduleItemRepository.findAll().stream()
                .filter(s -> s.getUniversity() != null && s.getUniversity().getId().equals(id))
                .toList();
        scheduleItemRepository.deleteAll(scheduleItems);

        // 12. Delete Announcements
        List<Announcement> announcements = announcementRepository.findAll().stream()
                .filter(a -> a.getUniversity() != null && a.getUniversity().getId().equals(id))
                .toList();
        announcementRepository.deleteAll(announcements);

        // 13. Delete Users
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getUniversity() != null && u.getUniversity().getId().equals(id))
                .toList();
        userRepository.deleteAll(users);

        // 14. Delete StudentClasses
        List<StudentClass> classes = studentClassRepository.findAll().stream()
                .filter(sc -> sc.getUniversity() != null && sc.getUniversity().getId().equals(id))
                .toList();
        studentClassRepository.deleteAll(classes);

        // 15. Delete Courses
        List<Course> courses = courseRepository.findAll().stream()
                .filter(c -> c.getUniversity() != null && c.getUniversity().getId().equals(id))
                .toList();
        courseRepository.deleteAll(courses);

        // 16. Delete SystemMetadata
        List<SystemMetadata> metadata = systemMetadataRepository.findAll().stream()
                .filter(sm -> sm.getUniversity() != null && sm.getUniversity().getId().equals(id))
                .toList();
        systemMetadataRepository.deleteAll(metadata);

        // 17. Finally, delete the University
        universityRepository.delete(uni);

        return ResponseEntity.ok(Map.of("message", "University and all associated data deleted successfully"));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MasterProfileUpdateRequest {
        private String fullName;
        private String email;
        private String password;
    }

    @PostMapping("/profile/update")
    @Transactional
    public ResponseEntity<?> updateMasterProfile(@RequestBody MasterProfileUpdateRequest request, java.security.Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        Optional<SystemAdmin> sysAdminOpt = systemAdminRepository.findByUsername(principal.getName());
        if (sysAdminOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Master account not found"));
        }
        SystemAdmin sysAdmin = sysAdminOpt.get();

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            sysAdmin.setFullName(request.getFullName().trim());
        }
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            sysAdmin.setEmail(request.getEmail().trim());
        }
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            sysAdmin.setPassword(passwordEncoder.encode(request.getPassword().trim()));
        }

        systemAdminRepository.save(sysAdmin);
        return ResponseEntity.ok(Map.of(
                "message", "Master profile updated successfully!",
                "fullName", sysAdmin.getFullName(),
                "email", sysAdmin.getEmail()
        ));
    }

    @GetMapping("/bugs")
    public ResponseEntity<?> listBugs() {
        return ResponseEntity.ok(bugReportRepository.findAllByOrderByCreatedAtDesc());
    }

    @PostMapping("/bugs/{id}/status")
    public ResponseEntity<?> updateBugStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Optional<BugReport> bugOpt = bugReportRepository.findById(id);
        if (bugOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String status = body.get("status");
        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Status is required"));
        }
        BugReport bug = bugOpt.get();
        bug.setStatus(status.trim().toUpperCase());
        bugReportRepository.save(bug);
        return ResponseEntity.ok(bug);
    }

    @GetMapping("/users/emails")
    public ResponseEntity<?> listUserEmails() {
        List<Map<String, Object>> result = userRepository.findAll().stream()
                .map(u -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", u.getId());
                    m.put("fullName", u.getFullName() != null ? u.getFullName() : "");
                    m.put("email", u.getEmail() != null ? u.getEmail() : "");
                    m.put("username", u.getUsername());
                    m.put("role", u.getRole() != null ? u.getRole().toString() : "USER");
                    m.put("universityName", u.getUniversity() != null ? u.getUniversity().getName() : "LearnX");
                    return m;
                })
                .filter(m -> m.get("email") != null && !((String) m.get("email")).trim().isEmpty())
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/send-email")
    public ResponseEntity<?> sendBroadcastEmail(@RequestBody Map<String, Object> body) {
        String subject = (String) body.get("subject");
        String content = (String) body.get("content");
        List<String> recipientEmails = (List<String>) body.get("recipientEmails");

        if (subject == null || subject.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Subject is required"));
        }
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content is required"));
        }

        List<String> finalRecipients;
        if (recipientEmails == null || recipientEmails.isEmpty()) {
            finalRecipients = userRepository.findAll().stream()
                    .map(User::getEmail)
                    .filter(e -> e != null && !e.trim().isEmpty())
                    .distinct()
                    .toList();
        } else {
            finalRecipients = recipientEmails.stream()
                    .filter(e -> e != null && !e.trim().isEmpty())
                    .distinct()
                    .toList();
        }

        if (finalRecipients.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No recipients found to send email to"));
        }

        int successCount = 0;
        int failCount = 0;
        List<Map<String, String>> failedRecipients = new java.util.ArrayList<>();

        for (String email : finalRecipients) {
            try {
                org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
                String fromEmail = env.getProperty("spring.mail.username");
                if (fromEmail != null && !fromEmail.isEmpty()) {
                    message.setFrom(fromEmail);
                }
                message.setTo(email);
                message.setSubject(subject);
                message.setText(content + "\n\n---\nSent via LearnX Master Broadcast System.");
                mailSender.send(message);
                successCount++;
            } catch (Exception ex) {
                System.err.println("Failed to send broadcast email to " + email + ": " + ex.getMessage());
                failCount++;
                Map<String, String> failMap = new java.util.HashMap<>();
                failMap.put("email", email);
                failMap.put("error", ex.getMessage());
                failedRecipients.add(failMap);
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Broadcast complete",
                "totalSent", finalRecipients.size(),
                "successCount", successCount,
                "failCount", failCount,
                "failedRecipients", failedRecipients
        ));
    }
}
