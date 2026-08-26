package com.ustc.learnx.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks what the migrations actually produce.
 *
 * <p>Booting this context already proves a great deal: Flyway applies every
 * script, and Hibernate runs with {@code ddl-auto=validate}, so a mismatch
 * between an entity and the schema fails before any test body runs. These cases
 * cover the parts validation cannot see — seed data, indexes and constraints.
 *
 * <p>These are assertions about a <em>freshly migrated</em> database, so this
 * class takes one of its own rather than the database the rest of the suite
 * shares. Sharing made the outcome depend on execution order: a test class that
 * seeds accounts, running first, made "the migrations create no accounts" fail.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:learnx-migrations;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
class MigrationSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyMigrationApplied() {
        // Flyway creates its history table and columns in lower case, as it does
        // on PostgreSQL, so they are quoted here; unquoted identifiers fold to
        // upper case under H2.
        // Flyway also records a null-version row for creating the schema itself,
        // which is not a migration.
        List<String> versions = jdbc.queryForList(
                "SELECT \"version\" FROM \"flyway_schema_history\" "
                        + "WHERE \"success\" = TRUE AND \"version\" IS NOT NULL "
                        + "ORDER BY \"installed_rank\"",
                String.class);
        // Update this list when adding a migration, so a script that fails to
        // apply cannot pass unnoticed.
        assertThat(versions).containsExactly("1", "2", "3", "4");
    }

    /** Tables the application cannot function without. */
    @ParameterizedTest(name = "table {0} exists")
    @ValueSource(strings = {
            "universities", "student_classes", "users", "courses",
            "class_course_assignments", "system_metadata", "schedule_items",
            "class_tests", "exams", "exam_questions", "exam_submissions",
            "gradebooks", "resources", "resource_reactions", "announcements",
            "audit_logs", "bug_reports", "profile_change_requests", "promotion_history",
            "platform_settings"
    })
    void tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE UPPER(table_name) = UPPER(?) AND table_schema = 'PUBLIC'",
                Integer.class, table);
        assertThat(count).isEqualTo(1);
    }

    /** The old blob column is gone; files live on disk now. */
    @Test
    void resourcesNoLongerStoreFileBytes() {
        assertThat(columnsOf("resources"))
                .doesNotContain("FILE_DATA")
                .contains("STORAGE_KEY", "FILE_SIZE", "SHA256");
    }

    /** Platform administrators are ordinary rows in users, not a second table. */
    @Test
    void systemAdminsTableIsGone() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE UPPER(table_name) = 'SYSTEM_ADMINS' AND table_schema = 'PUBLIC'",
                Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void usersRoleAcceptsSystemAdmin() {
        assertThat(columnsOf("users")).contains("ROLE", "UNIVERSITY_ID");
        // A platform owner has no university, so the column must be nullable.
        String nullable = jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE UPPER(table_name) = 'USERS' AND UPPER(column_name) = 'UNIVERSITY_ID'",
                String.class);
        assertThat(nullable).isEqualToIgnoringCase("YES");
    }

    // --- V4: the multi-tenant platform ---

    @Test
    void universitiesCarryAPublicProfile() {
        assertThat(columnsOf("universities")).contains(
                "SLUG", "DESCRIPTION", "CONTACT_EMAIL", "CONTACT_PHONE", "WEBSITE",
                "ADDRESS", "LOGO_KEY", "PUBLISHED", "CREATED_AT", "UPDATED_AT");
    }

    /**
     * The seeded university must be published, or applying V4 empties the public
     * homepage of the deployment it is applied to.
     */
    @Test
    void theSeededUniversityIsPublishedAndHasASlug() {
        Integer published = jdbc.queryForObject(
                "SELECT COUNT(*) FROM universities WHERE published = TRUE AND slug IS NOT NULL",
                Integer.class);
        assertThat(published).isEqualTo(1);

        String slug = jdbc.queryForObject(
                "SELECT slug FROM universities WHERE domain = 'ustc.ac.bd'", String.class);
        // Dot-free, so it is safe in a URL path segment.
        assertThat(slug).isEqualTo("ustc-ac-bd").doesNotContain(".");
    }

    @Test
    void platformBrandingIsASingleSeededRow() {
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM platform_settings", Integer.class);
        assertThat(rows).isEqualTo(1);

        String siteName = jdbc.queryForObject(
                "SELECT site_name FROM platform_settings WHERE id = 1", String.class);
        assertThat(siteName).isEqualTo("LearnX");
    }

    /** The check constraint is what makes "single row" true rather than merely intended. */
    @Test
    void aSecondPlatformSettingsRowIsRefused() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO platform_settings (id, site_name) VALUES (2, 'Impostor')"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    /**
     * Without this column an administrator reading the audit trail sees every
     * university's entries, not their own.
     */
    @Test
    void auditEntriesKnowWhichUniversityTheyBelongTo() {
        assertThat(columnsOf("audit_logs")).contains("UNIVERSITY_ID");
    }

    @Test
    void aClassBelongsToExactlyOneUniversity() {
        assertThat(isNullable("student_classes", "university_id")).isEqualToIgnoringCase("NO");
    }

    /**
     * Two universities can each run a "CSE / Batch 21 / Section A". Before this
     * they would have shared one row, and with it their routine and notes.
     */
    @Test
    void aClassIdentityIsUniqueWithinAUniversityOnly() {
        Long universityId = jdbc.queryForObject(
                "SELECT id FROM universities WHERE domain = 'ustc.ac.bd'", Long.class);
        jdbc.update("INSERT INTO student_classes (batch, department, section, university_id) "
                + "VALUES ('Batch 99', 'DUPLICATE', 'Section A', ?)", universityId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO student_classes (batch, department, section, university_id) "
                        + "VALUES ('Batch 99', 'DUPLICATE', 'Section A', ?)", universityId))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    /** A metadata row with no university appears in every tenant's dropdowns. */
    @Test
    void referenceDataAlwaysBelongsToAUniversity() {
        assertThat(isNullable("system_metadata", "university_id")).isEqualToIgnoringCase("NO");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO system_metadata (type, meta_value, university_id) "
                        + "VALUES ('NOT_A_REAL_TYPE', 'x', 1)"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    private String isNullable(String table, String column) {
        return jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE UPPER(table_name) = UPPER(?) AND UPPER(column_name) = UPPER(?)",
                String.class, table, column);
    }

    /** V2 provides the single university and the signup dropdown values. */
    @Test
    void referenceDataIsSeeded() {
        Integer universities = jdbc.queryForObject("SELECT COUNT(*) FROM universities", Integer.class);
        assertThat(universities).isEqualTo(1);

        Integer departments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM system_metadata WHERE type = 'DEPARTMENT'", Integer.class);
        assertThat(departments).isGreaterThan(0);

        // Metadata must be attached to the university, or the signup form
        // filters it out.
        Integer orphaned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM system_metadata WHERE university_id IS NULL", Integer.class);
        assertThat(orphaned).isZero();
    }

    /** No demo accounts outside the dev profile. */
    @Test
    void noAccountsAreSeededByMigrations() {
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(users).isZero();
    }

    /** A student may react to a resource once. */
    @Test
    void resourceReactionsAreUniquePerUser() {
        jdbc.update("INSERT INTO resource_reactions (resource_id, username, reaction_type) "
                + "VALUES (9001, 'someone', 'LIKE')");
        try {
            assertThat(catchInsertOfDuplicateReaction()).isNotNull();
        } finally {
            jdbc.update("DELETE FROM resource_reactions WHERE resource_id = 9001");
        }
    }

    private Exception catchInsertOfDuplicateReaction() {
        try {
            jdbc.update("INSERT INTO resource_reactions (resource_id, username, reaction_type) "
                    + "VALUES (9001, 'someone', 'DISLIKE')");
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList(
                "SELECT UPPER(column_name) FROM information_schema.columns "
                        + "WHERE UPPER(table_name) = UPPER(?)",
                String.class, table);
    }
}
