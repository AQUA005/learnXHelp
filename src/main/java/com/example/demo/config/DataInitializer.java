package com.example.demo.config;

import com.example.demo.entity.*;
import com.example.demo.entity.User.Role;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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
        
        // 4. Seed Test Users (Teacher, Student, CR)
        if (defaultUni != null) {
            if (userRepository.findByUsername("teacher").isEmpty()) {
                System.out.println("Seeding test teacher account...");
                User testTeacher = User.builder()
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
                userRepository.save(testTeacher);
            }

            if (userRepository.findByUsername("student").isEmpty()) {
                System.out.println("Seeding test student account...");
                User testStudent = User.builder()
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
                        .build();
                
                final University finalUni = defaultUni;
                StudentClass sc = studentClassRepository.findByBatchAndDepartmentAndSection("Batch 21", "Computer Science & Engineering", "Section A")
                        .orElseGet(() -> studentClassRepository.save(StudentClass.builder()
                                .batch("Batch 21")
                                .department("Computer Science & Engineering")
                                .section("Section A")
                                .university(finalUni)
                                .build()));
                testStudent.setStudentClass(sc);
                userRepository.save(testStudent);
            }

            if (userRepository.findByUsername("cr").isEmpty()) {
                System.out.println("Seeding test CR account...");
                User testCR = User.builder()
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
                        .build();
                
                final University finalUni = defaultUni;
                StudentClass sc = studentClassRepository.findByBatchAndDepartmentAndSection("Batch 21", "Computer Science & Engineering", "Section A")
                        .orElseGet(() -> studentClassRepository.save(StudentClass.builder()
                                .batch("Batch 21")
                                .department("Computer Science & Engineering")
                                .section("Section A")
                                .university(finalUni)
                                .build()));
                testCR.setStudentClass(sc);
                userRepository.save(testCR);
            }
        }

        System.out.println("Data seeding completed. Ready for production.");
    }
}
