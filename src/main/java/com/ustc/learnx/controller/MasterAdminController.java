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

    /** Appended to every broadcast, so a recipient can see where it came from. */
    private static final String SIGNATURE = "\n\n---\nSent from LearnX.";

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
            String logoUrl, boolean published, String adminEmail, long userCount) {

        static UniversityResponse of(University u, String adminEmail, long userCount) {
            return new UniversityResponse(u.getId(), u.getName(), u.getDomain(), u.getSlug(),
                    u.getDescription(), u.getContactEmail(), u.getContactPhone(), u.getWebsite(),
                    u.getAddress(), u.getLogoUrl(), u.isPublished(), adminEmail, userCount);
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
        // Sizes for every tenant in one query. Asking per university turned
        // this screen into one count per row.
        Map<Long, Long> sizes = new java.util.HashMap<>();
        for (Object[] row : userRepository.countGroupedByUniversity()) {
            sizes.put((Long) row[0], (Long) row[1]);
        }
        return ResponseEntity.ok(universityRepository.findAll().stream()
                .map(u -> withAdmin(u, sizes.getOrDefault(u.getId(), 0L)))
                .toList());
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

    /**
     * One person at one university, as the platform console shows them.
     *
     * <p>Read-only, and deliberately thin: the platform owner needs to see who
     * is on a campus and in what numbers, not to act on them. Approving,
     * rejecting and reassigning accounts stay with that university's own
     * administrator, who is the one who knows whether a face belongs there.
     */
    public record TenantUser(Long id, String fullName, String email, String role,
                             boolean approved, String department, String batch, String section) {

        static TenantUser of(User u) {
            return new TenantUser(u.getId(), u.getFullName(), u.getEmail(),
                    u.getRole() == null ? null : u.getRole().name(), u.isApproved(),
                    u.getDepartment(), u.getBatch(), u.getSection());
        }
    }

    /** A university's roll, with the totals the console shows above it. */
    public record TenantUsers(long total, Map<String, Long> byRole, List<TenantUser> users) {
    }

    @GetMapping("/universities/{id}/users")
    public ResponseEntity<TenantUsers> listUniversityUsers(@PathVariable Long id) {
        List<User> people = userRepository.findByUniversity_Id(requireUniversity(id).getId());

        // Every role is present in the map even at zero, so the console can
        // show "0 teachers" rather than leaving the tab out entirely.
        Map<String, Long> byRole = new java.util.LinkedHashMap<>();
        for (Role role : Role.values()) {
            byRole.put(role.name(), 0L);
        }
        for (User person : people) {
            if (person.getRole() != null) {
                byRole.merge(person.getRole().name(), 1L, Long::sum);
            }
        }

        List<TenantUser> users = people.stream()
                .sorted(java.util.Comparator
                        .comparing((User u) -> u.getRole() == null ? Integer.MAX_VALUE : u.getRole().ordinal())
                        .reversed()
                        .thenComparing(u -> u.getFullName() == null ? "" : u.getFullName(),
                                String.CASE_INSENSITIVE_ORDER))
                .map(TenantUser::of)
                .toList();

        return ResponseEntity.ok(new TenantUsers(people.size(), byRole, users));
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
        return withAdmin(university, userRepository.countByUniversity_Id(university.getId()));
    }

    private UniversityResponse withAdmin(University university, long userCount) {
        String adminEmail = userRepository.findByUniversityAndRole(university, Role.ADMIN)
                .stream().findFirst().map(User::getEmail).orElse(null);
        return UniversityResponse.of(university, adminEmail, userCount);
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

    /**
     * The platform owner's own account.
     *
     * <p>Separate from {@code /api/profile/update}, which routes a changed
     * email through an administrator for approval — a platform owner has no
     * administrator above them, so that path would leave the change pending
     * forever. Everything else is held to the same rules as any other account:
     * the password policy applies, and the address has to be free.
     */
    @PostMapping("/profile/update")
    @Transactional
    public ResponseEntity<?> updateMasterProfile(@RequestBody MasterProfileUpdateRequest request) {
        User sysAdmin = currentUserService.requireCurrentUser();

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            String fullName = request.getFullName().trim();
            if (fullName.length() > 255) {
                throw new ValidationException("Name is too long");
            }
            sysAdmin.setFullName(fullName);
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
            // Email is the sign-in credential and is globally unique, so taking
            // one that belongs to somebody else would lock them both out.
            userRepository.findByEmail(email)
                    .filter(other -> !other.getId().equals(sysAdmin.getId()))
                    .ifPresent(other -> {
                        throw new ValidationException("That address is already in use");
                    });
            sysAdmin.setEmail(email);
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            String password = request.getPassword();
            String policyError = com.ustc.learnx.common.PasswordPolicy.validate(password);
            if (policyError != null) {
                throw new ValidationException(policyError);
            }
            sysAdmin.setPassword(passwordEncoder.encode(password));
        }

        userRepository.save(sysAdmin);
        return ResponseEntity.ok(Map.of(
                "message", "Your profile has been updated.",
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

    /**
     * Who a broadcast reaches.
     *
     * <p>Both filters are optional: no university means every campus, and no
     * role means everyone at the ones chosen. There is deliberately no list of
     * addresses — the endpoint used to accept one, which made the application's
     * SMTP credentials a way to send arbitrary mail to arbitrary strangers.
     * Recipients are resolved from the account table, so the only people who
     * can be emailed are people who have accounts.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BroadcastRequest {
        private String subject;
        private String content;
        private Long universityId;
        private String role;
    }

    /**
     * How many accounts an audience holds.
     *
     * <p>Shown beside the send button. Sending to everyone on the platform and
     * sending to one class representative are the same two clicks, and the
     * only thing that distinguishes them is this number.
     */
    @GetMapping("/audience")
    public ResponseEntity<?> audienceSize(@RequestParam(required = false) Long universityId,
                                          @RequestParam(required = false) String role) {
        List<String> recipients = resolveAudience(universityId, role);
        return ResponseEntity.ok(Map.of("count", recipients.size()));
    }

    @PostMapping("/send-email")
    public ResponseEntity<?> sendBroadcastEmail(@RequestBody BroadcastRequest request) {
        String subject = required(request.getSubject(), "A subject is required");
        String content = required(request.getContent(), "A message is required");

        List<String> recipients = resolveAudience(request.getUniversityId(), request.getRole());
        if (recipients.isEmpty()) {
            throw new ValidationException("Nobody matches that audience");
        }

        int successCount = 0;
        int failCount = 0;
        String fromEmail = env.getProperty("learnx.mail.from");

        for (String email : recipients) {
            try {
                org.springframework.mail.SimpleMailMessage message =
                        new org.springframework.mail.SimpleMailMessage();
                if (fromEmail != null && !fromEmail.isEmpty()) {
                    message.setFrom(fromEmail);
                }
                message.setTo(email);
                message.setSubject(subject);
                message.setText(content + SIGNATURE);
                mailSender.send(message);
                successCount++;
            } catch (Exception ex) {
                // One unreachable address must not stop the rest of the send.
                // The address is not logged: the list is the recipient list.
                failCount++;
            }
        }

        auditService.record("PLATFORM", "BROADCAST",
                currentUserService.requireCurrentUser().getUsername(),
                "Emailed " + recipients.size() + " recipients: '" + subject + "'");

        return ResponseEntity.ok(Map.of(
                "message", "Broadcast complete",
                "totalSent", recipients.size(),
                "successCount", successCount,
                "failCount", failCount));
    }

    /**
     * The addresses an audience resolves to.
     *
     * <p>A university id that names nothing is rejected rather than quietly
     * treated as "everybody", which is the difference between a message to one
     * campus and a message to the whole platform.
     */
    private List<String> resolveAudience(Long universityId, String role) {
        if (universityId != null) {
            requireUniversity(universityId);
        }
        return userRepository.findAudienceEmails(universityId, parseRole(role))
                .stream().distinct().toList();
    }

    /** A blank role means every role; anything unrecognised is an error. */
    private static Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(role.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("There is no role called '" + role + "'");
        }
    }
}
