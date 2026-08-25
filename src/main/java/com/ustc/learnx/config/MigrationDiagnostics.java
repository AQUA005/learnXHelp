package com.ustc.learnx.config;

import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explains why a migration failed, in terms of what to do about it.
 *
 * <p>Flyway's own message for the commonest first-deployment failure reads:
 *
 * <pre>
 * Found non-empty schema(s) "public" but no schema history table.
 * Use baseline() or set baselineOnMigrate to true...
 * </pre>
 *
 * <p>That is accurate but misleading here. It means the database already holds
 * tables from the version of LearnX that let Hibernate build the schema, and the
 * remedy it suggests is the wrong one: baselining marks the existing tables as
 * version 1, so the migration that creates them correctly is skipped and the
 * later ones are applied to a structure they were never written for.
 *
 * <p>The failure is left to propagate — starting against a schema that does not
 * match the entities would fail later and less clearly — but an operator reading
 * the log now gets the actual cause and the way out.
 */
@Configuration
public class MigrationDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(MigrationDiagnostics.class);

    @Bean
    public FlywayMigrationStrategy explainMigrationFailures() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayException e) {
                if (mentionsMissingHistoryTable(e)) {
                    log.error("""

                            ------------------------------------------------------------------
                            The database already contains tables that LearnX did not create.

                            This happens when it was used by an earlier version, which let
                            Hibernate build the schema. This version owns the schema through
                            the migrations in db/migration and will not touch tables it does
                            not recognise.

                            To fix it, point DATABASE_URL at an empty database. The migrations
                            will then create everything and seed the university.

                            Do NOT set spring.flyway.baseline-on-migrate. It would skip the
                            migration that creates the tables correctly and apply the later
                            ones to the old structure, which does not match.

                            DEPLOYING.md covers how to check whether the old database holds
                            anything worth keeping, and how to empty it if you want to reuse
                            it.
                            ------------------------------------------------------------------""");
                }
                throw e;
            }
        };
    }

    /** Matches the wording Flyway uses when a schema has tables but no history. */
    private static boolean mentionsMissingHistoryTable(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null
                    && message.contains("non-empty schema")
                    && message.contains("no schema history table")) {
                return true;
            }
        }
        return false;
    }
}
