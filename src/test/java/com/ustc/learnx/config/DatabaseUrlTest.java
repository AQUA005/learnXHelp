package com.ustc.learnx.config;

import com.ustc.learnx.config.DatabaseUrl.JdbcSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hosting platforms publish the database as a single URL that is not a JDBC URL.
 * Getting this wrong means a deployment that will not start, so the shapes those
 * platforms actually emit are pinned here.
 */
class DatabaseUrlTest {

    @Test
    void convertsTheUrlRenderPublishes() {
        Optional<JdbcSettings> settings = DatabaseUrl.from(Map.of(
                "DATABASE_URL", "postgresql://learnx:s3cret@dpg-abc123-a.oregon-postgres.render.com/learnx_db"));

        assertThat(settings).isPresent();
        assertThat(settings.get().url())
                .isEqualTo("jdbc:postgresql://dpg-abc123-a.oregon-postgres.render.com:5432/learnx_db");
        assertThat(settings.get().username()).isEqualTo("learnx");
        assertThat(settings.get().password()).isEqualTo("s3cret");
    }

    /** Heroku and some others use the shorter scheme. */
    @Test
    void acceptsThePostgresScheme() {
        Optional<JdbcSettings> settings = DatabaseUrl.from(Map.of(
                "DATABASE_URL", "postgres://user:pass@db.example.com:6543/appdb"));

        assertThat(settings).isPresent();
        assertThat(settings.get().url()).isEqualTo("jdbc:postgresql://db.example.com:6543/appdb");
    }

    /** Managed databases usually require TLS, which arrives in the query string. */
    @Test
    void keepsConnectionOptions() {
        Optional<JdbcSettings> settings = DatabaseUrl.from(Map.of(
                "DATABASE_URL", "postgresql://u:p@host.example.com/db?sslmode=require"));

        assertThat(settings.orElseThrow().url())
                .isEqualTo("jdbc:postgresql://host.example.com:5432/db?sslmode=require");
    }

    /** A password with reserved characters must survive the round trip. */
    @Test
    void decodesAnEscapedPassword() {
        Optional<JdbcSettings> settings = DatabaseUrl.from(Map.of(
                "DATABASE_URL", "postgresql://user:p%40ss%2Fword@host.example.com/db"));

        assertThat(settings.orElseThrow().password()).isEqualTo("p@ss/word");
    }

    /** An explicitly configured datasource stays in charge. */
    @Test
    void leavesAnExplicitDatasourceAlone() {
        Optional<JdbcSettings> settings = DatabaseUrl.from(Map.of(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://chosen.example.com:5432/mine",
                "DATABASE_URL", "postgresql://u:p@ignored.example.com/other"));

        assertThat(settings).isEmpty();
    }

    @Test
    void passesThroughAUrlThatIsAlreadyJdbc() {
        Optional<JdbcSettings> settings = DatabaseUrl.from(Map.of(
                "DATABASE_URL", "jdbc:postgresql://host.example.com:5432/db"));

        assertThat(settings.orElseThrow().url()).isEqualTo("jdbc:postgresql://host.example.com:5432/db");
    }

    @Test
    void doesNothingWhenNoDatabaseUrlIsPublished() {
        assertThat(DatabaseUrl.from(Map.of())).isEmpty();
    }

    /** Only PostgreSQL is handled; anything else is left rather than guessed at. */
    @Test
    void ignoresAnotherDatabaseScheme() {
        assertThat(DatabaseUrl.from(Map.of("DATABASE_URL", "mysql://u:p@host/db"))).isEmpty();
    }

    @Test
    void ignoresSomethingThatIsNotAUrl() {
        assertThat(DatabaseUrl.from(Map.of("DATABASE_URL", "not a url at all"))).isEmpty();
    }
}
