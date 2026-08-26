package com.ustc.learnx;

import com.ustc.learnx.config.DatabaseUrl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Optional;

@SpringBootApplication
public class LearnxApplication {

	public static void main(String[] args) {
		applyPlatformDatabaseUrl();
		SpringApplication.run(LearnxApplication.class, args);
	}

	/**
	 * Accepts the database URL in the form hosting platforms publish it.
	 *
	 * <p>Applied before the context starts, so the datasource sees ordinary
	 * Spring properties. Set as system properties rather than overwriting the
	 * environment, which keeps an explicit {@code SPRING_DATASOURCE_URL} in
	 * charge if one is configured.
	 */
	private static void applyPlatformDatabaseUrl() {
		Optional<DatabaseUrl.JdbcSettings> settings = DatabaseUrl.from(System.getenv());
		if (settings.isEmpty() && DatabaseUrl.isPublished(System.getenv())
				&& !hasExplicitDatasource()) {
			// The value is there but is not a URL this can use. Starting anyway
			// would fall back to the local H2 file: the application would come
			// up looking healthy, with none of the real data in it.
			throw new IllegalStateException("""

					------------------------------------------------------------------
					DATABASE_URL is set but could not be read as a database URL.

					The expected shape is:
					  postgresql://USER:PASSWORD@HOST/DATABASE?sslmode=require

					Check that the whole string was pasted, on one line, with no
					quotes around it and nothing lost at a line break.

					Refusing to start rather than falling back to the local file
					database, which would look healthy and hold none of your data.
					------------------------------------------------------------------""");
		}
		settings.ifPresent(jdbc -> {
			System.setProperty("spring.datasource.url", jdbc.url());
			if (jdbc.username() != null) {
				System.setProperty("spring.datasource.username", jdbc.username());
			}
			if (jdbc.password() != null) {
				System.setProperty("spring.datasource.password", jdbc.password());
			}
			// The password is not logged; the host is enough to confirm the
			// right database was picked up.
			System.out.println("Using the database published by the platform: " + jdbc.url());
		});
	}

	/** An explicitly configured datasource is in charge and DATABASE_URL is ignored. */
	private static boolean hasExplicitDatasource() {
		return System.getenv("SPRING_DATASOURCE_URL") != null
				|| System.getenv("JDBC_DATABASE_URL") != null;
	}

}
