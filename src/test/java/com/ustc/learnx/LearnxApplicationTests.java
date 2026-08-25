package com.ustc.learnx;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the application context against the in-memory test database, which
 * also proves the Flyway migrations apply and that Hibernate can validate the
 * entity mappings against the schema they produce.
 */
@SpringBootTest
@ActiveProfiles("test")
class LearnxApplicationTests {

    @Test
    void contextLoads() {
    }
}
