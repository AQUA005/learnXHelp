package com.example.demo.config;

import com.example.demo.entity.*;
import com.example.demo.entity.User.Role;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final ClassTestRepository classTestRepository;
    private final GradeBookRepository gradeBookRepository;
    private final ResourceRepository resourceRepository;
    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemMetadataRepository systemMetadataRepository;
    private final StudentClassRepository studentClassRepository;
    private final UniversityRepository universityRepository;
    private final SystemAdminRepository systemAdminRepository;
    private final AnnouncementRepository announcementRepository;
    private final ClassCourseAssignmentRepository classCourseAssignmentRepository;
    private final CourseRepository courseRepository;

    @Override
    public void run(String... args) throws Exception {
        // 1. Seed Master System Admin (Site Owner)
        if (systemAdminRepository.count() == 0) {
            System.out.println("Seeding Master System Admin...");
            SystemAdmin master = SystemAdmin.builder()
                    .username("master")
                    .password(passwordEncoder.encode("password"))
                    .fullName("Master Admin")
                    .email("master@learnx.com")
                    .build();
            systemAdminRepository.save(master);
        }

        // 2. Seed Default University & default Admin
        University defaultUni = null;
        if (universityRepository.count() == 0) {
            System.out.println("Seeding Default University...");
            defaultUni = University.builder()
                    .name("LearnX")
                    .domain("university.edu")
                    .logoUrl("")
                    .build();
            defaultUni = universityRepository.save(defaultUni);

            if (userRepository.count() == 0) {
                System.out.println("Seeding default university admin...");
                User defaultAdmin = User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("password"))
                        .fullName("Admin")
                        .email("admin@learnx.help")
                        .role(Role.ADMIN)
                        .approved(true)
                        .university(defaultUni)
                        .build();
                userRepository.save(defaultAdmin);
            }
        } else {
            List<University> allUnis = universityRepository.findAll();
            if (!allUnis.isEmpty()) {
                defaultUni = allUnis.get(0);
                if ("LearnX University".equals(defaultUni.getName())) {
                    System.out.println("Migrating university name to LearnX...");
                    defaultUni.setName("LearnX");
                    defaultUni = universityRepository.save(defaultUni);
                }
            }
            userRepository.findByUsername("admin").ifPresent(admin -> {
                if ("University Admin".equals(admin.getFullName())) {
                    System.out.println("Migrating default admin name to Admin...");
                    admin.setFullName("Admin");
                    if ("admin@university.edu".equals(admin.getEmail())) {
                        admin.setEmail("admin@learnx.help");
                    }
                    userRepository.save(admin);
                }
            });
        }

        final University finalUni = defaultUni;

        // 3. Seed Default Metadata Options
        if (systemMetadataRepository.count() == 0 && defaultUni != null) {
            System.out.println("Seeding Default Metadata Options...");
            
            String[] departments = {"Computer Science & Engineering", "Electrical & Electronic Engineering", "Business Administration"};
            for (String val : departments) {
                systemMetadataRepository.save(SystemMetadata.builder().type("DEPARTMENT").value(val).university(defaultUni).build());
            }

            String[] semesters = {"1st Semester", "2nd Semester", "3rd Semester", "4th Semester", "5th Semester", "6th Semester", "7th Semester", "8th Semester"};
            for (String val : semesters) {
                systemMetadataRepository.save(SystemMetadata.builder().type("SEMESTER").value(val).university(defaultUni).build());
            }

            String[] batches = {"Batch 21", "Batch 22", "Batch 23", "Batch 24"};
            for (String val : batches) {
                systemMetadataRepository.save(SystemMetadata.builder().type("BATCH").value(val).university(defaultUni).build());
            }

            String[] designations = {"Lecturer", "Assistant Professor", "Associate Professor", "Professor"};
            for (String val : designations) {
                systemMetadataRepository.save(SystemMetadata.builder().type("DESIGNATION").value(val).university(defaultUni).build());
            }

            String[] sections = {"Section A", "Section B", "Section C"};
            for (String val : sections) {
                systemMetadataRepository.save(SystemMetadata.builder().type("SECTION").value(val).university(defaultUni).build());
            }
        }
        
        // 4. Seed Test Users (Teacher, Student, CR) & Class Group
        User testTeacher = null;
        User testStudent = null;
        User testCR = null;
        StudentClass defaultClass = null;

        if (defaultUni != null) {
            defaultClass = studentClassRepository.findByBatchAndDepartmentAndSection("Batch 21", "Computer Science & Engineering", "Section A")
                    .orElseGet(() -> studentClassRepository.save(StudentClass.builder()
                            .batch("Batch 21")
                            .department("Computer Science & Engineering")
                            .section("Section A")
                            .university(finalUni)
                            .build()));

            testTeacher = userRepository.findByUsername("teacher").orElse(null);
            if (testTeacher == null) {
                System.out.println("Seeding test teacher account...");
                testTeacher = User.builder()
                        .username("teacher")
                        .password(passwordEncoder.encode("password"))
                        .fullName("Test Teacher")
                        .email("teacher@learnx.help")
                        .role(Role.TEACHER)
                        .approved(true)
                        .designation("Lecturer")
                        .department("Computer Science & Engineering")
                        .university(defaultUni)
                        .build();
                testTeacher = userRepository.save(testTeacher);
            }

            testStudent = userRepository.findByUsername("student").orElse(null);
            if (testStudent == null) {
                System.out.println("Seeding test student account...");
                testStudent = User.builder()
                        .username("student")
                        .password(passwordEncoder.encode("password"))
                        .fullName("Test Student")
                        .email("student@learnx.help")
                        .role(Role.STUDENT)
                        .approved(true)
                        .semester("1st Semester")
                        .batch("Batch 21")
                        .section("Section A")
                        .department("Computer Science & Engineering")
                        .university(defaultUni)
                        .studentClass(defaultClass)
                        .build();
                testStudent = userRepository.save(testStudent);
            }

            testCR = userRepository.findByUsername("cr").orElse(null);
            if (testCR == null) {
                System.out.println("Seeding test CR account...");
                testCR = User.builder()
                        .username("cr")
                        .password(passwordEncoder.encode("password"))
                        .fullName("Test CR")
                        .email("cr@learnx.help")
                        .role(Role.CR)
                        .approved(true)
                        .semester("1st Semester")
                        .batch("Batch 21")
                        .section("Section A")
                        .department("Computer Science & Engineering")
                        .university(defaultUni)
                        .studentClass(defaultClass)
                        .build();
                testCR = userRepository.save(testCR);
            }
        }

        // 5. Seed Core Academic Courses & Teacher Assignments
        if (defaultUni != null && defaultClass != null && testTeacher != null) {
            System.out.println("Seeding courses and assignments...");
            
            Course dbSystems = courseRepository.findByCodeAndUniversity("CSE 3101", defaultUni)
                    .orElseGet(() -> courseRepository.save(Course.builder()
                            .code("CSE 3101")
                            .name("Database Systems")
                            .credits(3.0)
                            .semester("1st Semester")
                            .department("Computer Science & Engineering")
                            .university(finalUni)
                            .build()));

            Course softEng = courseRepository.findByCodeAndUniversity("CSE 3103", defaultUni)
                    .orElseGet(() -> courseRepository.save(Course.builder()
                            .code("CSE 3103")
                            .name("Software Engineering")
                            .credits(3.0)
                            .semester("1st Semester")
                            .department("Computer Science & Engineering")
                            .university(finalUni)
                            .build()));

            Course compilerDesign = courseRepository.findByCodeAndUniversity("CSE 3105", defaultUni)
                    .orElseGet(() -> courseRepository.save(Course.builder()
                            .code("CSE 3105")
                            .name("Compiler Design")
                            .credits(3.0)
                            .semester("1st Semester")
                            .department("Computer Science & Engineering")
                            .university(finalUni)
                            .build()));

            Course compNetworks = courseRepository.findByCodeAndUniversity("CSE 3107", defaultUni)
                    .orElseGet(() -> courseRepository.save(Course.builder()
                            .code("CSE 3107")
                            .name("Computer Networks")
                            .credits(3.0)
                            .semester("1st Semester")
                            .department("Computer Science & Engineering")
                            .university(finalUni)
                            .build()));

            if (classCourseAssignmentRepository.count() == 0) {
                classCourseAssignmentRepository.save(ClassCourseAssignment.builder().studentClass(defaultClass).course(dbSystems).teacher(testTeacher).build());
                classCourseAssignmentRepository.save(ClassCourseAssignment.builder().studentClass(defaultClass).course(softEng).teacher(testTeacher).build());
                classCourseAssignmentRepository.save(ClassCourseAssignment.builder().studentClass(defaultClass).course(compilerDesign).teacher(testTeacher).build());
                classCourseAssignmentRepository.save(ClassCourseAssignment.builder().studentClass(defaultClass).course(compNetworks).teacher(testTeacher).build());
            }

            // 6. Seed Routines (ScheduleItems)
            if (scheduleItemRepository.count() == 0) {
                System.out.println("Seeding schedule routine items...");
                scheduleItemRepository.save(ScheduleItem.builder()
                        .dayOfWeek("MONDAY")
                        .startTime(LocalTime.of(8, 30))
                        .endTime(LocalTime.of(10, 0))
                        .courseName("CSE 3101 - Database Systems")
                        .roomNo("302")
                        .teacherName("Test Teacher")
                        .studentClass(defaultClass)
                        .university(defaultUni)
                        .build());

                scheduleItemRepository.save(ScheduleItem.builder()
                        .dayOfWeek("MONDAY")
                        .startTime(LocalTime.of(10, 15))
                        .endTime(LocalTime.of(11, 45))
                        .courseName("CSE 3103 - Software Engineering")
                        .roomNo("302")
                        .teacherName("Test Teacher")
                        .studentClass(defaultClass)
                        .university(defaultUni)
                        .build());

                scheduleItemRepository.save(ScheduleItem.builder()
                        .dayOfWeek("WEDNESDAY")
                        .startTime(LocalTime.of(8, 30))
                        .endTime(LocalTime.of(10, 0))
                        .courseName("CSE 3105 - Compiler Design")
                        .roomNo("402")
                        .teacherName("Test Teacher")
                        .studentClass(defaultClass)
                        .university(defaultUni)
                        .build());

                scheduleItemRepository.save(ScheduleItem.builder()
                        .dayOfWeek("WEDNESDAY")
                        .startTime(LocalTime.of(10, 15))
                        .endTime(LocalTime.of(11, 45))
                        .courseName("CSE 3107 - Computer Networks")
                        .roomNo("402")
                        .teacherName("Test Teacher")
                        .studentClass(defaultClass)
                        .university(defaultUni)
                        .build());
            }

            // 7. Seed Class Tests (CTs)
            if (classTestRepository.count() == 0) {
                System.out.println("Seeding class tests...");
                classTestRepository.save(ClassTest.builder()
                        .courseName("CSE 3101 - Database Systems")
                        .dateTime(LocalDateTime.of(LocalDate.now().plusDays(2), LocalTime.of(12, 0)))
                        .durationMinutes(45)
                        .roomNo("302")
                        .topic("Normalization & ER Diagram")
                        .createdBy("teacher")
                        .studentClass(defaultClass)
                        .university(defaultUni)
                        .build());

                classTestRepository.save(ClassTest.builder()
                        .courseName("CSE 3105 - Compiler Design")
                        .dateTime(LocalDateTime.of(LocalDate.now().plusDays(4), LocalTime.of(13, 0)))
                        .durationMinutes(45)
                        .roomNo("402")
                        .topic("First & Follow Sets Parsing")
                        .createdBy("teacher")
                        .studentClass(defaultClass)
                        .university(defaultUni)
                        .build());
            }

            // 8. Seed Announcements
            if (announcementRepository.count() == 0) {
                System.out.println("Seeding announcements...");
                announcementRepository.save(Announcement.builder()
                        .title("Welcome to USTC LearnX Portal")
                        .content("We are thrilled to launch the new LearnX academic coordination and learning assistant portal. You can find schedules, library, quizzes, and grade metrics here.")
                        .createdBy("admin")
                        .createdByRole("ADMIN")
                        .studentClass(null) // Global announcement
                        .university(defaultUni)
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .build());

                announcementRepository.save(Announcement.builder()
                        .title("Syllabus for Database CT 1")
                        .content("The first class test for Database Systems will cover Normalization up to 3NF. Please study the lecture notes in the Library vault.")
                        .createdBy("teacher")
                        .createdByRole("TEACHER")
                        .studentClass(defaultClass)
                        .university(defaultUni)
                        .createdAt(LocalDateTime.now())
                        .build());
            }

            // 9. Seed Library Resources
            if (resourceRepository.count() == 0) {
                System.out.println("Seeding library resources...");
                resourceRepository.save(Resource.builder()
                        .title("Compiler Design Lecture Notes - LL(1) Parsing")
                        .courseName("CSE 3105 - Compiler Design")
                        .fileName("LL1_Parsing_Notes.pdf")
                        .contentType("application/pdf")
                        .fileData(new byte[10]) // stub data
                        .uploadedBy(testTeacher)
                        .approved(true)
                        .examTags("CT1")
                        .driveLink("https://drive.google.com/drive/folders/1aBcDeFgHiJkLmNoPqRsTuVwXyZ")
                        .studentClass(defaultClass)
                        .university(defaultUni)
                        .build());

                resourceRepository.save(Resource.builder()
                        .title("Database Normalization & Dependency Guide")
                        .courseName("CSE 3101 - Database Systems")
                        .fileName("Normalization_Guide.pdf")
                        .contentType("application/pdf")
                        .fileData(new byte[10])
                        .uploadedBy(testTeacher)
                        .approved(true)
                        .examTags("CT1")
                        .driveLink("https://drive.google.com/drive/folders/2bCdEfGhIjKlMnOpQrStUvWxYz")
                        .studentClass(defaultClass)
                        .university(defaultUni)
                        .build());
            }

            // 10. Seed Quizzes/Exams & Questions
            if (examRepository.count() == 0) {
                System.out.println("Seeding exam quizzes...");
                Exam dbQuiz = examRepository.save(Exam.builder()
                        .title("Database Systems - Quick Quiz 1")
                        .description("Test your knowledge of relations, normal forms, and SQL syntax basics.")
                        .teacher(testTeacher)
                        .durationMinutes(15)
                        .startTime(LocalDateTime.now().minusDays(1))
                        .endTime(LocalDateTime.now().plusDays(5))
                        .published(true)
                        .studentClass(defaultClass)
                        .university(defaultUni)
                        .build());

                examQuestionRepository.save(ExamQuestion.builder()
                        .exam(dbQuiz)
                        .questionText("Which normal form removes partial dependency?")
                        .questionType(ExamQuestion.QuestionType.MCQ)
                        .points(5)
                        .options("1NF;2NF;3NF;BCNF")
                        .correctAnswer("1") // 2NF, 0-indexed: option index 1
                        .build());

                examQuestionRepository.save(ExamQuestion.builder()
                        .exam(dbQuiz)
                        .questionText("What does SQL stand for?")
                        .questionType(ExamQuestion.QuestionType.MCQ)
                        .points(5)
                        .options("Structured Query Language;Strong Query Language;Simple Query Language;None of the above")
                        .correctAnswer("0") // Structured Query Language, 0-indexed
                        .build());
            }

            // 11. Seed Student Grades
            if (gradeBookRepository.count() == 0 && testStudent != null) {
                System.out.println("Seeding student grades...");
                gradeBookRepository.save(GradeBook.builder()
                        .student(testStudent)
                        .courseName("CSE 3101 - Database Systems")
                        .assessmentName("CT 1")
                        .marksObtained(15.5)
                        .maxMarks(20.0)
                        .build());

                gradeBookRepository.save(GradeBook.builder()
                        .student(testStudent)
                        .courseName("CSE 3105 - Compiler Design")
                        .assessmentName("CT 2")
                        .marksObtained(18.0)
                        .maxMarks(20.0)
                        .build());
            }
        }

        System.out.println("Data seeding completed. Ready for production.");
    }
}
