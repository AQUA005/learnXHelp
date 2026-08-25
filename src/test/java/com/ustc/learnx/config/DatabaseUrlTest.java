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

    /**
     * A plus sign in a password is a plus sign.
     *
     * <p>It once became a space, because the credentials were decoded a second
     * time after the URI had already resolved them, and that second pass applied
     * the rule for form data rather than for a URI. The only symptom was
     * "password authentication failed", with nothing to suggest the application
     * had altered the password.
     */
    @Test
    void keepsAPlusSignInThePassword() {
        Optional<JdbcSettings> settings = DatabaseUrl.from(Map.of(
                "DATABASE_URL", "postgresql://user:ab+cd+ef@host.example.com/db"));

        assertThat(settings.orElseThrow().password()).isEqualTo("ab+cd+ef");
    }

    /** Nor may a percent-escape be resolved twice. */
    @Test
    void doesNotDecodeAPasswordTwice() {
        // %2520 is an escaped '%20'. One pass gives '%20'; a second would give a space.
        Optional<JdbcSettings> settings = DatabaseUrl.from(Map.of(
                "DATABASE_URL", "postgresql://user:pa%2520ss@host.example.com/db"));

        assertThat(settings.orElseThrow().password()).isEqualTo("pa%20ss");
    }

    /** The username is subject to the same rule. */
    @Test
    void keepsAPlusSignInTheUsername() {
        Optional<JdbcSettings> settings = DatabaseUrl.from(Map.of(
                "DATABASE_URL", "postgresql://od+d:pw@host.example.com/db"));

        assertThat(settings.orElseThrow().username()).isEqualTo("od+d");
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

    /**
     * The exact shapes the free providers hand out. These are what a first
     * deployment will actually paste in, so a change here breaks real users.
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            "Neon | postgresql://u:p@ep-cool-a1-pooler.eu-central-1.aws.neon.tech/learnx?sslmode=require | jdbc:postgresql://ep-cool-a1-pooler.eu-central-1.aws.neon.tech:5432/learnx?sslmode=require",
            "Neon channel binding | postgresql://u:p@ep-x.aws.neon.tech/db?sslmode=require&channel_binding=require | jdbc:postgresql://ep-x.aws.neon.tech:5432/db?sslmode=require&channel_binding=require",
            "Supabase pooler | postgresql://postgres.abcdefgh:pw@aws-0-eu-central-1.pooler.supabase.com:6543/postgres | jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:6543/postgres",
            "Aiven | postgres://avnadmin:pw@pg-learnx.a.aivencloud.com:12345/defaultdb?sslmode=require | jdbc:postgresql://pg-learnx.a.aivencloud.com:12345/defaultdb?sslmode=require"
    })
    void convertsWhatEachProviderHandsOut(String provider, String published, String expected) {
        assertThat(DatabaseUrl.from(Map.of("DATABASE_URL", published)).orElseThrow().url())
                .as(provider)
                .isEqualTo(expected);
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
