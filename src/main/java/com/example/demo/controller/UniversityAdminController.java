package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.entity.User.Role;
import com.example.demo.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@AllArgsConstructor
public class UniversityAdminController {

    private final UniversityRepository universityRepository;
    private final UserRepository userRepository;
    private final StudentClassRepository studentClassRepository;
    private final CourseRepository courseRepository;
    private final ClassCourseAssignmentRepository classCourseAssignmentRepository;
    private final PromotionHistoryRepository promotionHistoryRepository;
    private final SystemMetadataRepository systemMetadataRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final ProfileChangeRequestRepository profileChangeRequestRepository;
    private final ExamRepository examRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceReactionRepository resourceReactionRepository;

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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignmentSnapshot {
        private Long courseId;
        private String courseCode;
        private String courseName;
        private Long teacherId;
        private String teacherUsername;
        private String teacherName;
    }

    private University getUniversityContext(String domainHeader, Principal principal) {
        if (domainHeader != null && !domainHeader.trim().isEmpty()) {
            return universityRepository.findByDomain(domainHeader).orElse(null);
        }
        if (principal != null) {
            Optional<User> user = userRepository.findByUsername(principal.getName());
            if (user.isPresent() && user.get().getUniversity() != null) {
                return user.get().getUniversity();
            }
        }
        List<University> all = universityRepository.findAll();
        return all.isEmpty() ? null : all.get(0);
    }

    // --- Academic Setup (Metadata) ---

    @PostMapping("/metadata")
    public ResponseEntity<?> addMetadata(@RequestHeader(value = "X-University-Domain", required = false) String domainHeader,
                                         @RequestBody MetadataRequest request, Principal principal) {
        University uni = getUniversityContext(domainHeader, principal);
        if (uni == null) return ResponseEntity.badRequest().body(Map.of("error", "University context not found"));

        SystemMetadata meta = SystemMetadata.builder()
                .type(request.getType().toUpperCase())
                .value(request.getValue())
                .university(uni)
                .build();
        systemMetadataRepository.save(meta);
        return ResponseEntity.ok(meta);
    }

    @GetMapping("/metadata")
    public ResponseEntity<?> getMetadata(@RequestHeader(value = "X-University-Domain", required = false) String domainHeader,
                                         @RequestParam String type, Principal principal) {
        University uni = getUniversityContext(domainHeader, principal);
        if (uni == null) return ResponseEntity.badRequest().body(Map.of("error", "University context not found"));

        List<SystemMetadata> list = systemMetadataRepository.findByTypeAndUniversity(type.toUpperCase(), uni);
        // Fallback to global metadata if none customized for this university yet (for semesters/designations)
        if (list.isEmpty() && (type.equalsIgnoreCase("SEMESTER") || type.equalsIgnoreCase("DESIGNATION") || type.equalsIgnoreCase("BATCH"))) {
            list = systemMetadataRepository.findByType(type.toUpperCase()).stream()
                    .filter(m -> m.getUniversity() == null)
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(list);
    }

    // --- Teachers Registry ---

    @GetMapping("/teachers")
    public ResponseEntity<?> getTeachers(@RequestHeader(value = "X-University-Domain", required = false) String domainHeader,
                                         Principal principal) {
        University uni = getUniversityContext(domainHeader, principal);
        if (uni == null) return ResponseEntity.badRequest().body(Map.of("error", "University context not found"));

        List<User> teachers = userRepository.findByUniversityAndRole(uni, Role.TEACHER);
        return ResponseEntity.ok(teachers);
    }

    @PostMapping("/teachers")
    public ResponseEntity<?> addTeacher(@RequestHeader(value = "X-University-Domain", required = false) String domainHeader,
                                        @RequestBody TeacherRegistrationRequest request, Principal principal) {
        University uni = getUniversityContext(domainHeader, principal);
        if (uni == null) return ResponseEntity.badRequest().body(Map.of("error", "University context not found"));

        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already taken"));
        }

        String designation = request.getDesignation();
        if (request.isGuest()) {
            designation = designation + " (Guest)";
        }

        User teacher = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword() != null ? request.getPassword() : "password"))
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

    // --- Predefined Courses Syllabus ---

    @PostMapping("/courses")
    public ResponseEntity<?> defineCourse(@RequestHeader(value = "X-University-Domain", required = false) String domainHeader,
                                          @RequestBody CourseDefinitionRequest request, Principal principal) {
        University uni = getUniversityContext(domainHeader, principal);
        if (uni == null) return ResponseEntity.badRequest().body(Map.of("error", "University context not found"));

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
    public ResponseEntity<?> getCourses(@RequestHeader(value = "X-University-Domain", required = false) String domainHeader,
                                        Principal principal) {
        University uni = getUniversityContext(domainHeader, principal);
        if (uni == null) return ResponseEntity.badRequest().body(Map.of("error", "University context not found"));

        return ResponseEntity.ok(courseRepository.findByUniversity(uni));
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {
        courseRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Course syllabus entry deleted"));
    }

    // --- Class Planner ---

    @PostMapping("/classes")
    public ResponseEntity<?> createClass(@RequestHeader(value = "X-University-Domain", required = false) String domainHeader,
                                         @RequestBody StudentClassRequest request, Principal principal) {
        University uni = getUniversityContext(domainHeader, principal);
        if (uni == null) return ResponseEntity.badRequest().body(Map.of("error", "University context not found"));

        // Set default semester if empty
        String semester = request.getSemester();
        if (semester == null || semester.trim().isEmpty()) {
            semester = "1st Year 1st Semester";
        }

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
    public ResponseEntity<?> getClasses(@RequestHeader(value = "X-University-Domain", required = false) String domainHeader,
                                        Principal principal) {
        University uni = getUniversityContext(domainHeader, principal);
        if (uni == null) return ResponseEntity.badRequest().body(Map.of("error", "University context not found"));

        List<StudentClass> list = studentClassRepository.findByUniversity(uni);
        
        // Enrich classes with CR info and semester name resolved from students
        List<Map<String, Object>> result = new ArrayList<>();
        for (StudentClass sc : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", sc.getId());
            map.put("department", sc.getDepartment());
            map.put("batch", sc.getBatch());
            map.put("section", sc.getSection());

            // Fetch students to determine semester and CR
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
            map.put("semester", semester);
            map.put("crUsername", cr != null ? cr.getUsername() : "None Assigned");
            map.put("crFullName", cr != null ? cr.getFullName() : "None Assigned");
            map.put("studentsCount", students.size());
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/classes/{classId}/assign-cr")
    @Transactional
    public ResponseEntity<?> assignClassCr(@PathVariable Long classId, @RequestBody Map<String, String> body) {
        Optional<StudentClass> classOpt = studentClassRepository.findById(classId);
        if (classOpt.isEmpty()) return ResponseEntity.notFound().build();

        String username = body.get("username");
        Optional<User> studentOpt = userRepository.findByUsername(username);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Student username not found"));
        }

        User student = studentOpt.get();
        // Remove CR role from previous CRs of this class
        List<User> classUsers = userRepository.findByStudentClass(classOpt.get());
        for (User u : classUsers) {
            if (u.getRole() == Role.CR) {
                u.setRole(Role.STUDENT);
                userRepository.save(u);
            }
        }

        // Set new CR
        student.setRole(Role.CR);
        student.setStudentClass(classOpt.get());
        // Sync department/batch/section from class to student
        student.setDepartment(classOpt.get().getDepartment());
        student.setBatch(classOpt.get().getBatch());
        student.setSection(classOpt.get().getSection());
        userRepository.save(student);

        return ResponseEntity.ok(Map.of("message", "CR assigned successfully", "crUsername", student.getUsername(), "crFullName", student.getFullName()));
    }

    @PostMapping("/classes/{classId}/assign-course")
    @Transactional
    public ResponseEntity<?> assignCourseTeacher(@PathVariable Long classId, @RequestBody CourseAssignmentRequest request) {
        Optional<StudentClass> classOpt = studentClassRepository.findById(classId);
        if (classOpt.isEmpty()) return ResponseEntity.notFound().build();

        Optional<Course> courseOpt = courseRepository.findById(request.getCourseId());
        if (courseOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Predefined course not found"));

        Optional<User> teacherOpt = userRepository.findById(request.getTeacherId());
        if (teacherOpt.isEmpty() || !teacherOpt.get().getRole().equals(Role.TEACHER)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Teacher not found"));
        }

        // Remove existing mapping for the same course in this class if it exists
        List<ClassCourseAssignment> assignments = classCourseAssignmentRepository.findByStudentClass(classOpt.get());
        for (ClassCourseAssignment cca : assignments) {
            if (cca.getCourse().getId().equals(request.getCourseId())) {
                classCourseAssignmentRepository.delete(cca);
            }
        }

        ClassCourseAssignment cca = ClassCourseAssignment.builder()
                .studentClass(classOpt.get())
                .course(courseOpt.get())
                .teacher(teacherOpt.get())
                .build();
        classCourseAssignmentRepository.save(cca);

        return ResponseEntity.ok(cca);
    }

    @GetMapping("/classes/{classId}/assignments")
    public ResponseEntity<?> getClassAssignments(@PathVariable Long classId) {
        Optional<StudentClass> classOpt = studentClassRepository.findById(classId);
        if (classOpt.isEmpty()) return ResponseEntity.notFound().build();

        List<ClassCourseAssignment> ccaList = classCourseAssignmentRepository.findByStudentClass(classOpt.get());
        
        List<Map<String, Object>> result = ccaList.stream().map(cca -> {
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

    private String getNextSemester(String current) {
        List<String> semesters = List.of(
                "1st Year 1st Semester", "1st Year 2nd Semester",
                "2nd Year 1st Semester", "2nd Year 2nd Semester",
                "3rd Year 1st Semester", "3rd Year 2nd Semester",
                "4th Year 1st Semester", "4th Year 2nd Semester"
        );
        int idx = semesters.indexOf(current);
        if (idx == -1) return "1st Year 1st Semester";
        if (idx == semesters.size() - 1) return current; // already at max
        return semesters.get(idx + 1);
    }

    private String getPreviousSemester(String current) {
        List<String> semesters = List.of(
                "1st Year 1st Semester", "1st Year 2nd Semester",
                "2nd Year 1st Semester", "2nd Year 2nd Semester",
                "3rd Year 1st Semester", "3rd Year 2nd Semester",
                "4th Year 1st Semester", "4th Year 2nd Semester"
        );
        int idx = semesters.indexOf(current);
        if (idx <= 0) return "1st Year 1st Semester";
        return semesters.get(idx - 1);
    }

    @PostMapping("/classes/{classId}/promote")
    @Transactional
    public ResponseEntity<?> promoteClass(@PathVariable Long classId) {
        Optional<StudentClass> classOpt = studentClassRepository.findById(classId);
        if (classOpt.isEmpty()) return ResponseEntity.notFound().build();

        StudentClass sc = classOpt.get();
        List<User> students = userRepository.findByStudentClass(sc);
        if (students.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No students enrolled in this class group"));
        }

        // Get current semester of class from any student
        String currentSemester = "1st Year 1st Semester";
        for (User s : students) {
            if (s.getSemester() != null) {
                currentSemester = s.getSemester();
                break;
            }
        }

        String nextSemester = getNextSemester(currentSemester);
        if (nextSemester.equals(currentSemester)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Class is already in the final semester"));
        }

        // 1. Create assignments snapshot
        List<ClassCourseAssignment> ccaList = classCourseAssignmentRepository.findByStudentClass(sc);
        List<AssignmentSnapshot> snapshots = ccaList.stream().map(cca -> new AssignmentSnapshot(
                cca.getCourse().getId(),
                cca.getCourse().getCode(),
                cca.getCourse().getName(),
                cca.getTeacher().getId(),
                cca.getTeacher().getUsername(),
                cca.getTeacher().getFullName()
        )).collect(Collectors.toList());

        String jsonStr = "";
        try {
            jsonStr = objectMapper.writeValueAsString(snapshots);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to compile assignment snapshot"));
        }

        // 2. Save promotion log
        PromotionHistory history = PromotionHistory.builder()
                .studentClass(sc)
                .fromSemester(currentSemester)
                .toSemester(nextSemester)
                .timestamp(LocalDateTime.now())
                .savedAssignmentsJson(jsonStr)
                .build();
        promotionHistoryRepository.save(history);

        // 3. Update all student semesters
        for (User s : students) {
            s.setSemester(nextSemester);
            userRepository.save(s);
        }

        // 4. Wipe active course/teacher assignments (so they register new ones for next semester)
        classCourseAssignmentRepository.deleteByStudentClass(sc);

        return ResponseEntity.ok(Map.of(
                "message", "Class promoted to " + nextSemester + " successfully!",
                "fromSemester", currentSemester,
                "toSemester", nextSemester
        ));
    }

    @PostMapping("/classes/{classId}/rollback-promotion")
    @Transactional
    public ResponseEntity<?> rollbackPromotion(@PathVariable Long classId) {
        Optional<StudentClass> classOpt = studentClassRepository.findById(classId);
        if (classOpt.isEmpty()) return ResponseEntity.notFound().build();

        StudentClass sc = classOpt.get();
        List<PromotionHistory> historyList = promotionHistoryRepository.findByStudentClassOrderByTimestampDesc(sc);
        if (historyList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No promotion history log found for this class group"));
        }

        PromotionHistory latestLog = historyList.get(0);

        // 1. Restore student semesters
        List<User> students = userRepository.findByStudentClass(sc);
        for (User s : students) {
            s.setSemester(latestLog.getFromSemester());
            userRepository.save(s);
        }

        // 2. Clear current assignments
        classCourseAssignmentRepository.deleteByStudentClass(sc);

        // 3. Restore assignments from JSON snapshot
        try {
            List<AssignmentSnapshot> snapshots = objectMapper.readValue(
                    latestLog.getSavedAssignmentsJson(),
                    new TypeReference<List<AssignmentSnapshot>>() {}
            );

            for (AssignmentSnapshot snap : snapshots) {
                Optional<Course> courseOpt = courseRepository.findById(snap.getCourseId());
                Optional<User> teacherOpt = userRepository.findById(snap.getTeacherId());
                if (courseOpt.isPresent() && teacherOpt.isPresent()) {
                    ClassCourseAssignment cca = ClassCourseAssignment.builder()
                            .studentClass(sc)
                            .course(courseOpt.get())
                            .teacher(teacherOpt.get())
                            .build();
                    classCourseAssignmentRepository.save(cca);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to unpack rollback snapshot metadata: " + e.getMessage()));
        }

        // 4. Delete the rollback log entry so they don't double undo it
        promotionHistoryRepository.delete(latestLog);

        return ResponseEntity.ok(Map.of(
                "message", "Successfully rolled back promotion to " + latestLog.getFromSemester() + "!",
                "restoredSemester", latestLog.getFromSemester()
        ));
    }

    @GetMapping("/students")
    public ResponseEntity<?> getStudents(@RequestHeader(value = "X-University-Domain", required = false) String domainHeader,
                                         Principal principal) {
        University uni = getUniversityContext(domainHeader, principal);
        if (uni == null) return ResponseEntity.badRequest().body(Map.of("error", "University context not found"));

        List<User> students = userRepository.findByUniversityAndRole(uni, Role.STUDENT);
        List<User> crList = userRepository.findByUniversityAndRole(uni, Role.CR);
        
        List<User> all = new ArrayList<>();
        all.addAll(students);
        all.addAll(crList);
        return ResponseEntity.ok(all);
    }

    // --- University Scoped Configuration & Logo/Name update ---

    @GetMapping("/university")
    public ResponseEntity<?> getUniversity(@RequestHeader(value = "X-University-Domain", required = false) String domainHeader,
                                           Principal principal) {
        University uni = getUniversityContext(domainHeader, principal);
        if (uni == null) return ResponseEntity.badRequest().body(Map.of("error", "University context not found"));
        return ResponseEntity.ok(uni);
    }

    @PutMapping("/university")
    public ResponseEntity<?> updateUniversity(@RequestHeader(value = "X-University-Domain", required = false) String domainHeader,
                                              @RequestBody Map<String, String> payload, Principal principal) {
        University uni = getUniversityContext(domainHeader, principal);
        if (uni == null) return ResponseEntity.badRequest().body(Map.of("error", "University context not found"));
        
        String name = payload.get("name");
        String logoUrl = payload.get("logoUrl");
        
        if (name != null && !name.trim().isEmpty()) {
            uni.setName(name);
        }
        if (logoUrl != null) {
            uni.setLogoUrl(logoUrl);
        }
        
        University saved = universityRepository.save(uni);
        return ResponseEntity.ok(saved);
    }

    // --- Metadata deletion ---

    @DeleteMapping("/metadata/{id}")
    public ResponseEntity<?> deleteMetadata(@PathVariable Long id) {
        systemMetadataRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Metadata option deleted successfully"));
    }

    @DeleteMapping("/teachers/{id}")
    @Transactional
    public ResponseEntity<?> deleteTeacher(@PathVariable Long id, Principal principal) {
        Optional<User> teacherOpt = userRepository.findById(id);
        if (teacherOpt.isEmpty() || !teacherOpt.get().getRole().equals(Role.TEACHER)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Teacher not found"));
        }
        User teacher = teacherOpt.get();

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
}
