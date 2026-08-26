package com.ustc.learnx.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

/**
 * Translates a hosting provider's database URL into JDBC settings.
 *
 * <p>Render, Heroku and similar platforms publish the database as a single
 * {@code DATABASE_URL} of the form
 * {@code postgresql://user:password@host:5432/database}. That is not a JDBC URL,
 * and Spring cannot use it as one, so a deployment configured only with what the
 * platform provides fails to start with an unhelpful driver error.
 *
 * <p>This converts it. An explicit {@code SPRING_DATASOURCE_URL} always wins, so
 * anyone who has already configured the datasource by hand is unaffected.
 */
public final class DatabaseUrl {

    private DatabaseUrl() {
    }

    /** The three properties a JDBC datasource needs. */
    public record JdbcSettings(String url, String username, String password) {
    }

    /**
     * Converts a platform database URL if one is present and the datasource has
     * not already been configured explicitly.
     *
     * @param environment the environment variables to read
     * @return the settings to apply, or empty if there is nothing to do
     */
    public static Optional<JdbcSettings> from(Map<String, String> environment) {
        if (hasText(environment.get("SPRING_DATASOURCE_URL"))
                || hasText(environment.get("JDBC_DATABASE_URL"))) {
            return Optional.empty();
        }

        String raw = clean(firstPresent(environment, "DATABASE_URL", "POSTGRES_URL", "POSTGRESQL_URL"));
        if (!hasText(raw)) {
            return Optional.empty();
        }
        // Already a JDBC URL: nothing to translate.
        if (raw.startsWith("jdbc:")) {
            return Optional.of(new JdbcSettings(raw,
                    environment.get("SPRING_DATASOURCE_USERNAME"),
                    environment.get("SPRING_DATASOURCE_PASSWORD")));
        }

        return parse(raw);
    }

    private static Optional<JdbcSettings> parse(String raw) {
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }

        String scheme = uri.getScheme();
        if (scheme == null || !scheme.startsWith("postgres")) {
            // Only PostgreSQL is supported; anything else is left alone rather
            // than guessed at.
            return Optional.empty();
        }

        String host = uri.getHost();
        if (host == null) {
            return Optional.empty();
        }
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");

        String username = null;
        String password = null;
        // getUserInfo has already resolved any percent-escapes. Decoding again
        // would corrupt the credentials: a second pass reads '+' as a space,
        // which is the rule for form data, not for a URI. A password containing
        // a plus sign would then be silently wrong and authentication would
        // fail with nothing to suggest why.
        String userInfo = uri.getUserInfo();
        if (hasText(userInfo)) {
            int separator = userInfo.indexOf(':');
            if (separator >= 0) {
                username = userInfo.substring(0, separator);
                password = userInfo.substring(separator + 1);
            } else {
                username = userInfo;
            }
        }

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://")
                .append(host).append(':').append(port).append('/').append(database);
        // Managed databases generally require TLS, and the query string is where
        // a provider puts sslmode.
        if (hasText(uri.getQuery())) {
            jdbc.append('?').append(uri.getQuery());
        }

        return Optional.of(new JdbcSettings(jdbc.toString(), username, password));
    }

    private static String firstPresent(Map<String, String> environment, String... names) {
        for (String name : names) {
            String value = environment.get(name);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Reports whether the platform published a database URL at all.
     *
     * <p>Lets a caller tell "no database was configured" apart from "a database
     * was configured but could not be used", which are the same empty result
     * from {@link #from(Map)} but mean very different things in production.
     */
    public static boolean isPublished(Map<String, String> environment) {
        return hasText(firstPresent(environment, "DATABASE_URL", "POSTGRES_URL", "POSTGRESQL_URL"));
    }

    /**
     * Removes what a copy and paste from a provider's dashboard tends to bring
     * with it: surrounding whitespace, a trailing newline, and the quotes from a
     * {@code psql '...'} example. Without this the value is not a URL, and the
     * application quietly starts on its local H2 fallback instead.
     */
    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        boolean quoted = trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                        || (trimmed.startsWith("'") && trimmed.endsWith("'")));
        return quoted ? trimmed.substring(1, trimmed.length() - 1).trim() : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
