package com.ustc.learnx.controller;

import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.entity.*;
import com.ustc.learnx.entity.User.Role;
import com.ustc.learnx.repository.*;
import com.ustc.learnx.service.CurrentUserService;
import com.ustc.learnx.service.PromotionService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * University administration: academic metadata, teachers, courses, classes,
 * CR assignment and batch promotion.
 *
 * <p>Restricted to ADMIN. Every operation is scoped to the caller's own
 * university, resolved from their account rather than from a request header.
 *
 * <p>Endpoints that take an id in the path assert that the row belongs to the
 * caller's university before touching it. They previously did not: an
 * administrator of one university could pass another university's class,
 * course, teacher or metadata id and have it acted on. {@code assign-cr} was
 * the worst of them — it looked the target up by username across the whole
 * platform, then moved that account into the caller's class.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@AllArgsConstructor
public class UniversityAdminController {

    private final UniversityRepository universityRepository;
    private final UserRepository userRepository;
    private final StudentClassRepository studentClassRepository;
    private final CourseRepository courseRepository;
    private final ClassCourseAssignmentRepository classCourseAssignmentRepository;
    private final SystemMetadataRepository systemMetadataRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfileChangeRequestRepository profileChangeRequestRepository;
    private final ExamRepository examRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceReactionRepository resourceReactionRepository;
    private final CurrentUserService currentUserService;
    private final PromotionService promotionService;
    private final com.ustc.learnx.service.ClassAdminService classAdminService;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataRequest {
        private String type; // "DEPARTMENT", "MAJOR", "BATCH", "SECTION", "SEMESTER", "DESIGNATION"
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeacherRegistrationRequest {
        private String username;
        private String password;
        private String fullName;
        private String email;
        private String department;
        private String designation;
        private boolean guest; // true if guest teacher
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseDefinitionRequest {
        private String code;
        private String name;
        private Double credits;
        private String semester;
        private String department;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentClassRequest {
        private String department;
        private String batch;
        private String section;
        private String semester;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseAssignmentRequest {
        private Long courseId;
        private Long teacherId;
    }

    /**
     * A class group as the administration screen lists it.
     *
     * <p>{@code crUsername} and {@code crFullName} are null when no class
     * representative is assigned. They used to be the string "None Assigned",
     * which forced the client to compare against an English phrase.
     */
    public record ClassSummary(
            Long id,
            String className,
            String department,
            String batch,
            String section,
            String semester,
            String crUsername,
            String crFullName,
            int studentsCount) {
    }

    // --- Lookups that assert tenancy ---

    /** The class with that id, or 403/404 if it is not the caller's to touch. */
    private StudentClass requireOwnClass(Long classId) {
        StudentClass sc = studentClassRepository.findById(classId)
                .orElseThrow(() -> new NotFoundException("No class group with id " + classId));
        currentUserService.assertSameUniversity(sc.getUniversity());
        return sc;
    }

    // --- Academic Setup (Metadata) ---

    @PostMapping("/metadata")
    public ResponseEntity<?> addMetadata(@RequestBody MetadataRequest request) {
        University uni = currentUserService.requireUniversity();

        SystemMetadata meta = SystemMetadata.builder()
                .type(request.getType().toUpperCase())
                .value(request.getValue())
                .university(uni)
                .build();
        systemMetadataRepository.save(meta);
        return ResponseEntity.ok(meta);
    }

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata(@RequestParam String type) {
        University uni = currentUserService.requireUniversity();

        // No fallback to platform-global rows: since V4 every metadata row has a
        // university, and a row belonging to none would have shown up in every
        // tenant's dropdowns.
        return ResponseEntity.ok(
                systemMetadataRepository.findByTypeAndUniversity(type.toUpperCase(), uni));
    }

    @DeleteMapping("/metadata/{id}")
    public ResponseEntity<?> deleteMetadata(@PathVariable Long id) {
        SystemMetadata meta = systemMetadataRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No metadata option with id " + id));
        currentUserService.assertSameUniversity(meta.getUniversity());
        systemMetadataRepository.delete(meta);
        return ResponseEntity.ok(Map.of("message", "Metadata option deleted successfully"));
    }

    // --- Teachers Registry ---

    @GetMapping("/teachers")
    public ResponseEntity<?> getTeachers() {
        University uni = currentUserService.requireUniversity();
        return ResponseEntity.ok(userRepository.findByUniversityAndRole(uni, Role.TEACHER));
    }

    @PostMapping("/teachers")
    public ResponseEntity<?> addTeacher(@RequestBody TeacherRegistrationRequest request) {
        University uni = currentUserService.requireUniversity();

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ValidationException("Username already taken");
        }

        // An administrator must choose a real password; there is no default.
        String teacherPasswordError = com.ustc.learnx.common.PasswordPolicy.validate(request.getPassword());
        if (teacherPasswordError != null) {
            throw new ValidationException(teacherPasswordError);
        }

        String designation = request.getDesignation();
        if (request.isGuest()) {
            designation = designation + " (Guest)";
        }

        User teacher = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .role(Role.TEACHER)
                .approved(true)
                .department(request.getDepartment())
                .designation(designation)
                .university(uni)
                .build();

        userRepository.save(teacher);
        return ResponseEntity.ok(teacher);
    }

    @DeleteMapping("/teachers/{id}")
    @Transactional
    public ResponseEntity<?> deleteTeacher(@PathVariable Long id) {
        User teacher = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No teacher with id " + id));
        if (teacher.getRole() != Role.TEACHER) {
            throw new ValidationException("That account is not a teacher");
        }
        currentUserService.assertSameUniversity(teacher.getUniversity());

        // 1. Delete associated profile change requests
        profileChangeRequestRepository.deleteByUser(teacher);

        // 2. Delete ClassCourseAssignments
        classCourseAssignmentRepository.deleteByTeacher(teacher);

        // 3. Delete ExamSubmissions and ExamQuestions for exams created by this teacher, then the exams
        List<Exam> exams = examRepository.findByTeacher(teacher);
        for (Exam exam : exams) {
            examSubmissionRepository.deleteByExam(exam);
            examQuestionRepository.deleteByExam(exam);
        }
        examRepository.deleteAll(exams);

        // 4. Delete Resources uploaded by this teacher
        List<Resource> resources = resourceRepository.findByUploadedBy(teacher);
        for (Resource res : resources) {
            resourceReactionRepository.deleteByResourceId(res.getId());
        }
        resourceRepository.deleteAll(resources);

        // 5. Delete the User record
        userRepository.delete(teacher);

        return ResponseEntity.ok(Map.of("message", "Teacher deleted successfully"));
    }

    // --- Predefined Courses Syllabus ---

    @PostMapping("/courses")
    public ResponseEntity<?> defineCourse(@RequestBody CourseDefinitionRequest request) {
        University uni = currentUserService.requireUniversity();

        Course course = Course.builder()
                .code(request.getCode())
                .name(request.getName())
                .credits(request.getCredits())
                .semester(request.getSemester())
                .department(request.getDepartment())
                .university(uni)
                .build();
        courseRepository.save(course);
        return ResponseEntity.ok(course);
    }

    @GetMapping("/courses")
    public ResponseEntity<?> getCourses() {
        University uni = currentUserService.requireUniversity();
        return ResponseEntity.ok(courseRepository.findByUniversity(uni));
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No course with id " + id));
        currentUserService.assertSameUniversity(course.getUniversity());
        courseRepository.delete(course);
        return ResponseEntity.ok(Map.of("message", "Course syllabus entry deleted"));
    }

    // --- Class Planner ---

    @PostMapping("/classes")
    public ResponseEntity<?> createClass(@RequestBody StudentClassRequest request) {
        University uni = currentUserService.requireUniversity();

        StudentClass sc = StudentClass.builder()
                .department(request.getDepartment())
                .batch(request.getBatch())
                .section(request.getSection())
                .university(uni)
                .build();
        studentClassRepository.save(sc);
        return ResponseEntity.ok(sc);
    }

    @GetMapping("/classes")
    public ResponseEntity<?> getClasses() {
        University uni = currentUserService.requireUniversity();

        List<ClassSummary> result = new ArrayList<>();
        for (StudentClass sc : studentClassRepository.findByUniversity(uni)) {
            // The class carries no semester of its own; it is read back off the
            // students in it.
            List<User> students = userRepository.findByStudentClass(sc);
            String semester = "1st Year 1st Semester";
            User cr = null;
            for (User s : students) {
                if (s.getSemester() != null) {
                    semester = s.getSemester();
                }
                if (s.getRole() == Role.CR) {
                    cr = s;
                }
            }
            result.add(new ClassSummary(
                    sc.getId(),
                    sc.getClassName(),
                    sc.getDepartment(),
                    sc.getBatch(),
                    sc.getSection(),
                    semester,
                    cr == null ? null : cr.getUsername(),
                    cr == null ? null : cr.getFullName(),
                    students.size()));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/classes/{classId}/assign-cr")
    @Transactional
    public ResponseEntity<?> assignClassCr(@PathVariable Long classId, @RequestBody Map<String, String> body) {
        StudentClass studentClass = requireOwnClass(classId);

        String username = body.get("username");
        User student = userRepository.findByUsername(username)
                .orElseThrow(() -> new ValidationException("Student username not found"));

        // Without this the lookup above spans the whole platform, so an
        // administrator could name another university's student and pull that
        // account into this class.
        currentUserService.assertSameUniversity(student.getUniversity());
        if (student.getRole() != Role.STUDENT && student.getRole() != Role.CR) {
            throw new ValidationException("Only a student can be made class representative");
        }

        // Remove CR role from previous CRs of this class
        for (User u : userRepository.findByStudentClass(studentClass)) {
            if (u.getRole() == Role.CR) {
                u.setRole(Role.STUDENT);
                userRepository.save(u);
            }
        }

        // Set new CR
        student.setRole(Role.CR);
        student.setStudentClass(studentClass);
        // Sync department/batch/section from class to student
        student.setDepartment(studentClass.getDepartment());
        student.setBatch(studentClass.getBatch());
        student.setSection(studentClass.getSection());
        userRepository.save(student);

        return ResponseEntity.ok(Map.of(
                "message", "CR assigned successfully",
                "crUsername", student.getUsername(),
                "crFullName", student.getFullName()));
    }

    @PostMapping("/classes/{classId}/assign-course")
    @Transactional
    public ResponseEntity<?> assignCourseTeacher(@PathVariable Long classId, @RequestBody CourseAssignmentRequest request) {
        StudentClass studentClass = requireOwnClass(classId);

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ValidationException("Predefined course not found"));
        currentUserService.assertSameUniversity(course.getUniversity());

        User teacher = userRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new ValidationException("Teacher not found"));
        if (teacher.getRole() != Role.TEACHER) {
            throw new ValidationException("Teacher not found");
        }
        currentUserService.assertSameUniversity(teacher.getUniversity());

        // Remove existing mapping for the same course in this class if it exists
        for (ClassCourseAssignment cca : classCourseAssignmentRepository.findByStudentClass(studentClass)) {
            if (cca.getCourse().getId().equals(request.getCourseId())) {
                classCourseAssignmentRepository.delete(cca);
            }
        }

        ClassCourseAssignment cca = ClassCourseAssignment.builder()
                .studentClass(studentClass)
                .course(course)
                .teacher(teacher)
                .build();
        classCourseAssignmentRepository.save(cca);

        return ResponseEntity.ok(cca);
    }

    /**
     * One class group in full: roster, courses, routine and promotion history.
     *
     * <p>The screen that shows this used to assemble it from three separate
     * endpoints.
     */
    @GetMapping("/classes/{classId}")
    public ResponseEntity<com.ustc.learnx.dto.ClassDetailDtos.ClassDetail> getClassDetail(
            @PathVariable Long classId) {
        return ResponseEntity.ok(classAdminService.detail(classId));
    }

    @GetMapping("/classes/{classId}/assignments")
    public ResponseEntity<?> getClassAssignments(@PathVariable Long classId) {
        StudentClass studentClass = requireOwnClass(classId);

        List<Map<String, Object>> result = classCourseAssignmentRepository.findByStudentClass(studentClass)
                .stream().map(cca -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", cca.getId());
                    map.put("courseId", cca.getCourse().getId());
                    map.put("courseCode", cca.getCourse().getCode());
                    map.put("courseName", cca.getCourse().getName());
                    map.put("courseCredits", cca.getCourse().getCredits());
                    map.put("teacherId", cca.getTeacher().getId());
                    map.put("teacherFullName", cca.getTeacher().getFullName());
                    map.put("teacherDesignation", cca.getTeacher().getDesignation());
                    return map;
                }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // --- Semester Promotion & Rollback ---

    @PostMapping("/classes/{classId}/promote")
    public ResponseEntity<?> promoteClass(@PathVariable Long classId) {
        requireOwnClass(classId);
        PromotionService.PromotionResult result = promotionService.promote(classId);
        return ResponseEntity.ok(Map.of(
                "message", "Class promoted to " + result.toSemester() + " successfully!",
                "fromSemester", result.fromSemester(),
                "toSemester", result.toSemester(),
                "studentsMoved", result.studentsMoved()));
    }

    @PostMapping("/classes/{classId}/rollback-promotion")
    public ResponseEntity<?> rollbackPromotion(@PathVariable Long classId) {
        requireOwnClass(classId);
        PromotionService.PromotionResult result = promotionService.rollback(classId);
        return ResponseEntity.ok(Map.of(
                "message", "Successfully rolled back promotion to " + result.toSemester() + "!",
                "restoredSemester", result.toSemester(),
                "studentsMoved", result.studentsMoved()));
    }

    @GetMapping("/students")
    public ResponseEntity<?> getStudents() {
        University uni = currentUserService.requireUniversity();

        List<User> all = new ArrayList<>(userRepository.findByUniversityAndRole(uni, Role.STUDENT));
        all.addAll(userRepository.findByUniversityAndRole(uni, Role.CR));
        return ResponseEntity.ok(all);
    }

    // --- University Scoped Configuration & Logo/Name update ---

    @GetMapping("/university")
    public ResponseEntity<?> getUniversity() {
        return ResponseEntity.ok(currentUserService.requireUniversity());
    }

    @PutMapping("/university")
    public ResponseEntity<?> updateUniversity(@RequestBody Map<String, String> payload) {
        University uni = currentUserService.requireUniversity();

        String name = payload.get("name");
        String logoUrl = payload.get("logoUrl");

        if (name != null && !name.trim().isEmpty()) {
            uni.setName(name);
        }
        if (logoUrl != null) {
            uni.setLogoUrl(logoUrl);
        }

        return ResponseEntity.ok(universityRepository.save(uni));
    }
}
