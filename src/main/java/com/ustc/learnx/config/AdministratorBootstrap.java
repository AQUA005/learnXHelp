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
 * <p>Also repairs the one deployment state this cannot otherwise recover from.
 * An earlier version of this class created the bootstrap account as an
 * {@code ADMIN} of whichever university happened to be first. That was
 * corrected to {@code SYSTEM_ADMIN}, but only for a deployment starting with an
 * empty table — an existing installation kept the account it already had, and
 * since nothing outside the dev profile can mint a {@code SYSTEM_ADMIN}, it was
 * left with no platform owner and therefore no way to add a second university.
 * Where no platform owner exists at all, the configured address is promoted to
 * one.
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
            // Somebody can already sign in, so no account is created here. The
            // deployment may still have no owner, which is the one thing it
            // cannot fix from inside the application.
            promoteConfiguredOwnerIfNoneExists();
            return;
        }

        if (username.isBlank() || password.isBlank()) {
            log.warn("""
                    No accounts exist and no bootstrap administrator is configured, \
                    so nobody can sign in. Set LEARNX_ADMIN_USERNAME, \
                    LEARNX_ADMIN_PASSWORD and LEARNX_ADMIN_EMAIL, then restart.""");
            return;
        }

        // Required, not derived. People sign in with their email, so a fallback
        // of username + "@learnx.local" would create an account whose sign-in
        // address the operator never chose and cannot receive mail at.
        if (email.isBlank()) {
            log.error("LEARNX_ADMIN_EMAIL is not set. It is the address this account "
                    + "signs in with, so it cannot be guessed. No account has been created.");
            return;
        }

        String policyError = PasswordPolicy.validate(password);
        if (policyError != null) {
            log.error("The bootstrap administrator password was rejected: {}. "
                    + "No account has been created.", policyError);
            return;
        }

        String address = email.trim().toLowerCase(java.util.Locale.ROOT);

        userRepository.save(User.builder()
                .username(username.trim())
                .password(passwordEncoder.encode(password))
                .fullName(fullName.isBlank() ? "LearnX Administrator" : fullName.trim())
                .email(address)
                // The platform owner, not a university administrator, and
                // attached to no university. This used to create an ADMIN bound
                // to whichever university happened to be first, which left a
                // fresh deployment with no way to create a second one: adding a
                // university is a SYSTEM_ADMIN operation, and nothing outside
                // the dev profile could produce that role.
                .role(User.Role.SYSTEM_ADMIN)
                .approved(true)
                .university(null)
                .build());

        log.info("Created the platform owner. Sign in as '{}', change this password, "
                + "then remove LEARNX_ADMIN_PASSWORD from the environment.", address);
    }

    /**
     * Gives a deployment that has no platform owner one.
     *
     * <p>Deliberately narrow, because promoting an account is an escalation.
     * It happens only when there is no {@code SYSTEM_ADMIN} anywhere — in which
     * case the deployment is already unable to add a university, so there is no
     * working state to damage — and only to the address the operator has named
     * in the environment, which is the same address this class would have
     * created the owner at.
     *
     * <p>The password is not touched. The account can already be signed into,
     * and rewriting it on every restart from an environment variable would
     * undo whatever the owner had since changed it to.
     */
    private void promoteConfiguredOwnerIfNoneExists() {
        if (userRepository.existsByRole(User.Role.SYSTEM_ADMIN)) {
            return;
        }

        if (email.isBlank()) {
            log.warn("""
                    This deployment has no platform owner, so no university can be                     added. Set LEARNX_ADMIN_EMAIL to the address that should own it                     and restart.""");
            return;
        }

        String address = email.trim().toLowerCase(java.util.Locale.ROOT);
        Optional<User> candidate = userRepository.findByEmail(address);
        if (candidate.isEmpty()) {
            log.error("This deployment has no platform owner, and no account has the "
                    + "configured address '{}'. Nothing has been changed.", address);
            return;
        }

        User owner = candidate.get();
        User.Role was = owner.getRole();
        owner.setRole(User.Role.SYSTEM_ADMIN);
        // A platform owner sits above every university rather than inside one.
        // While this points at a university, every tenant-scoped screen resolves
        // that university and answers as though they administered only it.
        owner.setUniversity(null);
        userRepository.save(owner);

        log.warn("Promoted '{}' from {} to the platform owner, because this deployment "
                + "had none. Any university it administered now has no administrator; "
                + "set one from the platform console.", address, was);
    }
}
