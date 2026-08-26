package com.ustc.learnx.controller;

import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.entity.*;
import com.ustc.learnx.service.UniversityProvisioningService;
import com.ustc.learnx.entity.User.Role;
import com.ustc.learnx.repository.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Platform-owner operations: creating and deleting universities, resetting
 * tenant administrator passwords, reading bug reports and broadcasting mail.
 *
 * <p>Restricted to SYSTEM_ADMIN. Every endpoint here crosses tenant boundaries,
 * so none of it is reachable by a university administrator.
 */
@RestController
@RequestMapping("/api/master")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
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
    private final BugReportRepository bugReportRepository;
    private final org.springframework.mail.javamail.JavaMailSender mailSender;
    private final org.springframework.core.env.Environment env;
    private final com.ustc.learnx.repository.PlatformSettingsRepository platformSettingsRepository;
    private final com.ustc.learnx.service.BrandingService brandingService;
    private final com.ustc.learnx.service.UniversityProvisioningService universityProvisioningService;
    private final com.ustc.learnx.service.CurrentUserService currentUserService;
    private final com.ustc.learnx.service.AuditService auditService;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UniversityRegistrationRequest {
        private String name;
        private String domain;
        private String description;
        private String contactEmail;
        private String adminFullName;
        private String adminEmail;
        private String adminPassword;
    }

    /**
     * The editable parts of a university.
     *
     * <p>No {@code slug}: it is the public URL key and is fixed at creation, so
     * links people have shared keep working. No {@code logoUrl} either — a logo
     * arrives through the upload endpoint and its address is computed by the
     * server, because the Content-Security-Policy will not load a remote one.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UniversityUpdateRequest {
        private String name;
        private String domain;
        private String description;
        private String contactEmail;
        private String contactPhone;
        private String website;
        private String address;
    }

    /** A university as the platform console shows it. */
    public record UniversityResponse(
            Long id, String name, String domain, String slug, String description,
            String contactEmail, String contactPhone, String website, String address,
            String logoUrl, boolean published, String adminEmail) {

        static UniversityResponse of(University u, String adminEmail) {
            return new UniversityResponse(u.getId(), u.getName(), u.getDomain(), u.getSlug(),
                    u.getDescription(), u.getContactEmail(), u.getContactPhone(), u.getWebsite(),
                    u.getAddress(), u.getLogoUrl(), u.isPublished(), adminEmail);
        }
    }

    @PostMapping("/universities")
    public ResponseEntity<?> registerUniversity(@RequestBody UniversityRegistrationRequest request) {
        University created = universityProvisioningService.create(
                new UniversityProvisioningService.NewUniversity(
                        request.getName(), request.getDomain(), request.getDescription(),
                        request.getContactEmail(), request.getAdminFullName(),
                        request.getAdminEmail(), request.getAdminPassword()),
                currentUserService.requireCurrentUser().getUsername());
        return ResponseEntity.ok(withAdmin(created));
    }

    @GetMapping("/universities")
    public ResponseEntity<List<UniversityResponse>> listUniversities() {
        return ResponseEntity.ok(universityRepository.findAll().stream()
                .map(this::withAdmin).toList());
    }

    @GetMapping("/universities/{id}")
    public ResponseEntity<UniversityResponse> getUniversity(@PathVariable Long id) {
        return ResponseEntity.ok(withAdmin(requireUniversity(id)));
    }

    @PutMapping("/universities/{id}")
    public ResponseEntity<?> updateUniversity(@PathVariable Long id, @RequestBody UniversityUpdateRequest request) {
        University uni = requireUniversity(id);

        // A blank name or domain would otherwise be written over a good one:
        // both were previously set unconditionally from the request.
        String name = required(request.getName(), "A university name is required");
        String domain = required(request.getDomain(), "A domain is required")
                .toLowerCase(java.util.Locale.ROOT);

        universityRepository.findByName(name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ValidationException("A university with that name is already registered");
                });
        universityRepository.findByDomain(domain)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ValidationException("That domain is already taken");
                });

        uni.setName(name);
        uni.setDomain(domain);
        uni.setDescription(trimmed(request.getDescription()));
        uni.setContactEmail(trimmed(request.getContactEmail()));
        uni.setContactPhone(trimmed(request.getContactPhone()));
        uni.setWebsite(trimmed(request.getWebsite()));
        uni.setAddress(trimmed(request.getAddress()));

        University saved = universityRepository.save(uni);
        auditService.record("UNIVERSITY", "UPDATE",
                currentUserService.requireCurrentUser().getUsername(),
                "Updated '" + saved.getName() + "'");
        return ResponseEntity.ok(withAdmin(saved));
    }

    /**
     * Listing a university publicly, or taking it back off the homepage.
     *
     * <p>Separate from the profile update so that publishing is one deliberate,
     * auditable action, and so that saving a half-finished profile can never
     * make a university public as a side effect.
     */
    @PutMapping("/universities/{id}/publish")
    public ResponseEntity<?> setPublished(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean published = Boolean.TRUE.equals(body.get("published"));
        University saved = universityProvisioningService.setPublished(
                requireUniversity(id), published,
                currentUserService.requireCurrentUser().getUsername());
        return ResponseEntity.ok(withAdmin(saved));
    }

    @PostMapping("/universities/{id}/logo")
    public ResponseEntity<?> uploadUniversityLogo(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(withAdmin(
                brandingService.storeUniversityLogo(requireUniversity(id), body.get("dataUrl"))));
    }

    @DeleteMapping("/universities/{id}/logo")
    public ResponseEntity<?> removeUniversityLogo(@PathVariable Long id) {
        return ResponseEntity.ok(withAdmin(
                brandingService.removeUniversityLogo(requireUniversity(id))));
    }

    // --- LearnX's own branding ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandingUpdateRequest {
        private String siteName;
        private String tagline;
        private String supportEmail;
    }

    @GetMapping("/branding")
    public ResponseEntity<?> getBranding() {
        return ResponseEntity.ok(brandingService.settings());
    }

    @PutMapping("/branding")
    public ResponseEntity<?> updateBranding(@RequestBody BrandingUpdateRequest request) {
        var settings = brandingService.settings();
        settings.setSiteName(required(request.getSiteName(), "A site name is required"));
        settings.setTagline(trimmed(request.getTagline()));
        settings.setSupportEmail(trimmed(request.getSupportEmail()));
        settings.setUpdatedAt(java.time.LocalDateTime.now());
        return ResponseEntity.ok(platformSettingsRepository.save(settings));
    }

    @PostMapping("/branding/logo")
    public ResponseEntity<?> uploadPlatformLogo(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(brandingService.storePlatformLogo(body.get("dataUrl")));
    }

    @PostMapping("/branding/icon")
    public ResponseEntity<?> uploadPlatformIcon(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(brandingService.storePlatformIcon(body.get("dataUrl")));
    }

    // --- Shared ---

    private University requireUniversity(Long id) {
        return universityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No university with id " + id));
    }

    /** The console lists each university alongside who administers it. */
    private UniversityResponse withAdmin(University university) {
        String adminEmail = userRepository.findByUniversityAndRole(university, Role.ADMIN)
                .stream().findFirst().map(User::getEmail).orElse(null);
        return UniversityResponse.of(university, adminEmail);
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Gives a university's administrator a new password, so a tenant that has
     * locked itself out can be let back in.
     *
     * <p>Keyed by email, which is what people sign in with. The password is held
     * to the same policy as any other: it was not, so the account with the most
     * power in a tenant could be given a weaker one than its students.
     */
    @PostMapping("/universities/{id}/reset-admin")
    public ResponseEntity<?> resetAdminPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        University uni = requireUniversity(id);

        String email = required(body.get("adminEmail"), "An administrator email is required")
                .toLowerCase(java.util.Locale.ROOT);
        String password = required(body.get("adminPassword"), "A new password is required");

        String policyError = com.ustc.learnx.common.PasswordPolicy.validate(password);
        if (policyError != null) {
            throw new ValidationException(policyError);
        }

        User admin = userRepository.findByEmail(email).orElse(null);
        if (admin == null) {
            // No account with that address: create one for this university.
            admin = User.builder()
                    .username(email.substring(0, email.indexOf('@')).replaceAll("[^a-z0-9.]", ""))
                    .fullName("University Administrator")
                    .email(email)
                    .role(Role.ADMIN)
                    .approved(true)
                    .university(uni)
                    .build();
        } else if (admin.getRole() != Role.ADMIN
                || admin.getUniversity() == null
                || !admin.getUniversity().getId().equals(id)) {
            throw new ValidationException(
                    "That address belongs to a different account or a different university");
        }

        admin.setPassword(passwordEncoder.encode(password));
        userRepository.save(admin);

        auditService.record("UNIVERSITY", "RESET_ADMIN",
                currentUserService.requireCurrentUser().getUsername(),
                "Reset the administrator password for '" + uni.getName() + "'");

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

        // Written last, and deliberately: this is irreversible, and until now it
        // left no record at all that it had happened.
        auditService.record("UNIVERSITY", "DELETE",
                currentUserService.requireCurrentUser().getUsername(),
                "Deleted '" + uni.getName() + "' and all of its data");

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
        User sysAdmin = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new com.ustc.learnx.common.NotFoundException("Master account not found"));

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            sysAdmin.setFullName(request.getFullName().trim());
        }
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            sysAdmin.setEmail(request.getEmail().trim());
        }
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            sysAdmin.setPassword(passwordEncoder.encode(request.getPassword().trim()));
        }

        userRepository.save(sysAdmin);
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
                String fromEmail = env.getProperty("learnx.mail.from");
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
