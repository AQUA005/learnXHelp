package com.ustc.learnx.config;

import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.UniversityRepository;
import com.ustc.learnx.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recovering a deployment that has no platform owner.
 *
 * <p>An earlier version of the bootstrap created its account as an
 * {@code ADMIN} bound to the first university. Because the bootstrap only ran
 * against an empty table, correcting it fixed new deployments and left existing
 * ones with no {@code SYSTEM_ADMIN} at all — and therefore no way to add a
 * university, since that is a platform-owner operation and nothing outside the
 * dev profile can produce the role.
 *
 * <p>Promoting an account is an escalation, so what matters as much as the
 * repair working is that it stays asleep everywhere else. Its own database,
 * because it changes roles and the rest of the suite anchors fixtures to
 * accounts at "the first university".
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:learnx-bootstrap;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
class AdministratorBootstrapTest {

    private static final String OWNER_EMAIL = "owner@learnx.test";

    @Autowired private UserRepository userRepository;
    @Autowired private UniversityRepository universityRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /** The bootstrap as configured by an operator, without the Spring context. */
    private AdministratorBootstrap bootstrapConfiguredFor(String email) {
        AdministratorBootstrap bootstrap =
                new AdministratorBootstrap(userRepository, universityRepository, passwordEncoder);
        ReflectionTestUtils.setField(bootstrap, "username", "owner");
        ReflectionTestUtils.setField(bootstrap, "password", "");
        ReflectionTestUtils.setField(bootstrap, "email", email);
        ReflectionTestUtils.setField(bootstrap, "fullName", "LearnX Administrator");
        return bootstrap;
    }

    private void run(AdministratorBootstrap bootstrap) {
        bootstrap.run(new DefaultApplicationArguments());
    }

    private User saveAccount(String email, User.Role role, University university) {
        return userRepository.save(User.builder()
                .username(email.substring(0, email.indexOf('@')))
                .password(passwordEncoder.encode("Str0ng-Passw0rd!"))
                .fullName("LearnX Administrator")
                .email(email)
                .role(role)
                .approved(true)
                .university(university)
                .build());
    }

    private University anyUniversity() {
        return universityRepository.findAll().getFirst();
    }

    @Test
    void promotesTheConfiguredAccountWhenTheDeploymentHasNoOwner() {
        University university = anyUniversity();
        Long id = saveAccount(OWNER_EMAIL, User.Role.ADMIN, university).getId();

        run(bootstrapConfiguredFor(OWNER_EMAIL));

        User promoted = userRepository.findById(id).orElseThrow();
        assertThat(promoted.getRole()).isEqualTo(User.Role.SYSTEM_ADMIN);
        // The point of the repair: while this points at a university, every
        // tenant-scoped screen answers as though they administered only it.
        assertThat(promoted.getUniversity()).isNull();
    }

    @Test
    void leavesEverybodyAloneOncePlatformOwnerExists() {
        University university = anyUniversity();
        saveAccount("existing-owner@learnx.test", User.Role.SYSTEM_ADMIN, null);
        Long id = saveAccount(OWNER_EMAIL, User.Role.ADMIN, university).getId();

        run(bootstrapConfiguredFor(OWNER_EMAIL));

        User untouched = userRepository.findById(id).orElseThrow();
        assertThat(untouched.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(untouched.getUniversity()).isNotNull();
    }

    @Test
    void promotesNobodyWhenTheConfiguredAddressMatchesNoAccount() {
        University university = anyUniversity();
        Long id = saveAccount(OWNER_EMAIL, User.Role.ADMIN, university).getId();

        run(bootstrapConfiguredFor("nobody@learnx.test"));

        User untouched = userRepository.findById(id).orElseThrow();
        assertThat(untouched.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(userRepository.existsByRole(User.Role.SYSTEM_ADMIN)).isFalse();
    }

    @Test
    void promotesNobodyWhenNoAddressIsConfigured() {
        University university = anyUniversity();
        Long id = saveAccount(OWNER_EMAIL, User.Role.ADMIN, university).getId();

        run(bootstrapConfiguredFor(""));

        assertThat(userRepository.findById(id).orElseThrow().getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(userRepository.existsByRole(User.Role.SYSTEM_ADMIN)).isFalse();
    }

    /** A student who happens to be named in the environment is still promoted. */
    @Test
    void promotesWhateverRoleTheConfiguredAddressHolds() {
        University university = anyUniversity();
        Long id = saveAccount(OWNER_EMAIL, User.Role.STUDENT, university).getId();

        run(bootstrapConfiguredFor(OWNER_EMAIL));

        assertThat(userRepository.findById(id).orElseThrow().getRole())
                .isEqualTo(User.Role.SYSTEM_ADMIN);
    }
}
