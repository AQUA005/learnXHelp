package com.ustc.learnx.config;

import com.ustc.learnx.common.PasswordPolicy;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.UniversityRepository;
import com.ustc.learnx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Creates the first administrator so a new deployment can be signed into.
 *
 * <p>Migrations seed the university and the reference data but deliberately
 * create no accounts, and the demo accounts only exist under the dev profile.
 * That leaves a fresh deployment with an empty {@code users} table: nobody can
 * sign in, and although anyone may sign up, every new account needs an
 * administrator to approve it. Without this the deployment would be unusable.
 *
 * <p>Runs only when the table is empty, so it cannot alter or overwrite an
 * existing account, and is a no-op on every subsequent start.
 */
@Component
@Profile("!dev")
@RequiredArgsConstructor
public class AdministratorBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministratorBootstrap.class);

    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${learnx.bootstrap.admin-username:}")
    private String username;

    @Value("${learnx.bootstrap.admin-password:}")
    private String password;

    @Value("${learnx.bootstrap.admin-email:}")
    private String email;

    @Value("${learnx.bootstrap.admin-name:LearnX Administrator}")
    private String fullName;

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (userRepository.count() > 0) {
            // Somebody can already sign in; never touch existing accounts.
            return;
        }

        if (username.isBlank() || password.isBlank()) {
            log.warn("""
                    No accounts exist and no bootstrap administrator is configured, \
                    so nobody can sign in. Set LEARNX_ADMIN_USERNAME, \
                    LEARNX_ADMIN_PASSWORD and LEARNX_ADMIN_EMAIL, then restart.""");
            return;
        }

        String policyError = PasswordPolicy.validate(password);
        if (policyError != null) {
            log.error("The bootstrap administrator password was rejected: {}. "
                    + "No account has been created.", policyError);
            return;
        }

        Optional<University> university = universityRepository.findAll().stream().findFirst();
        if (university.isEmpty()) {
            log.error("No university exists, so the administrator cannot be attached to one. "
                    + "Did the migrations run?");
            return;
        }

        String address = email.isBlank() ? username + "@learnx.local" : email.trim();

        userRepository.save(User.builder()
                .username(username.trim())
                .password(passwordEncoder.encode(password))
                .fullName(fullName.isBlank() ? "LearnX Administrator" : fullName.trim())
                .email(address)
                .role(User.Role.ADMIN)
                .approved(true)
                .university(university.get())
                .build());

        log.info("Created the first administrator '{}'. Sign in and change this password, "
                + "then remove LEARNX_ADMIN_PASSWORD from the environment.", username.trim());
    }
}
