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

        // 2. Do not seed default university or demo accounts in production.
        System.out.println("Data seeding completed. Ready for production.");
    }
}
