package com.example.demo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1) // Run before DataInitializer to ensure table exists and is updated
@RequiredArgsConstructor
public class SchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        try {
            System.out.println("LearnX: Adjusting schema for base64 profile picture uploads...");
            // Alter column type to TEXT in H2 to allow long base64 image strings
            jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN profile_pic_url TEXT");
            System.out.println("LearnX: Successfully altered users.profile_pic_url to TEXT");
        } catch (Exception e) {
            // Log it - it might fail if column is already TEXT or table structure differs, which is fine
            System.out.println("LearnX Schema adjustment status: " + e.getMessage());
        }
    }
}
