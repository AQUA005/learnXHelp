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

}
