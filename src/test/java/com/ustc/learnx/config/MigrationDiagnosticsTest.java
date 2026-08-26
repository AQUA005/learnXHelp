package com.ustc.learnx.config;

import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two failures a first deployment actually hits.
 *
 * <p>Both arrive wrapped several layers deep inside Spring's bean-creation
 * exceptions, so the detection has to walk the chain rather than read the top
 * message.
 */
class MigrationDiagnosticsTest {

    /** The wording Flyway uses against a database an older version left behind. */
    @Test
    void recognisesASchemaWithNoHistoryTable() {
        Exception buried = new RuntimeException("Error creating bean with name 'flywayInitializer'",
                new FlywayException("""
                        Found non-empty schema(s) "public" but no schema history table. \
                        Use baseline() or set baselineOnMigrate to true to initialize \
                        the schema history table."""));

        assertThat(MigrationDiagnostics.mentionsMissingHistoryTable(buried)).isTrue();
        assertThat(MigrationDiagnostics.mentionsAuthenticationFailure(buried)).isFalse();
    }

    /** PostgreSQL reports rejected credentials as SQLSTATE 28P01. */
    @Test
    void recognisesARejectedPassword() {
        Exception buried = new RuntimeException("Error creating bean with name 'entityManagerFactory'",
                new RuntimeException("Unable to obtain connection from database",
                        new SQLException("password authentication failed for user \"neondb_owner\"", "28P01")));

        assertThat(MigrationDiagnostics.mentionsAuthenticationFailure(buried)).isTrue();
        assertThat(MigrationDiagnostics.mentionsMissingHistoryTable(buried)).isFalse();
    }

    /** Flyway quotes the state into its own message rather than nesting the SQLException. */
    @Test
    void recognisesARejectedPasswordReportedAsText() {
        Exception buried = new FlywayException("""
                Unable to obtain connection from database: password authentication failed
                SQL State  : 28P01
                Error Code : 0""");

        assertThat(MigrationDiagnostics.mentionsAuthenticationFailure(buried)).isTrue();
    }

    /** Anything else is left to report itself. */
    @Test
    void doesNotClaimUnrelatedFailures() {
        Exception unrelated = new RuntimeException("Connection refused",
                new SQLException("could not connect to server", "08001"));

        assertThat(MigrationDiagnostics.mentionsAuthenticationFailure(unrelated)).isFalse();
        assertThat(MigrationDiagnostics.mentionsMissingHistoryTable(unrelated)).isFalse();
    }

    @Test
    void toleratesAnExceptionWithNoMessage() {
        assertThat(MigrationDiagnostics.mentionsAuthenticationFailure(new RuntimeException())).isFalse();
        assertThat(MigrationDiagnostics.mentionsMissingHistoryTable(new RuntimeException())).isFalse();
    }
}
