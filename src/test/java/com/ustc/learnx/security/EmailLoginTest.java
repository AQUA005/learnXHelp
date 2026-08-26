package com.ustc.learnx.security;

import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.UniversityRepository;
import com.ustc.learnx.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signing in by email, and signing up into a named university.
 *
 * <p>People sign in with their email address; the username is generated from it
 * and never typed. The username is still accepted, because it remains the
 * security principal and a browser holding an older bundle still posts it.
 *
 * <p>Its own database: signing up creates accounts, and the classes that share
 * the suite's database assert on which accounts exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:learnx-email-login;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
class EmailLoginTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UniversityRepository universityRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private University published;

    @BeforeEach
    void seed() {
        published = universityRepository.findAll().stream().findFirst().orElseThrow();

        if (userRepository.findByUsername("known").isEmpty()) {
            userRepository.save(User.builder()
                    .username("known").password(passwordEncoder.encode("password1"))
                    .fullName("Known Person").email("known.person@ustc.test")
                    .role(User.Role.STUDENT).approved(true).university(published).build());
        }
        if (userRepository.findByUsername("unapproved").isEmpty()) {
            userRepository.save(User.builder()
                    .username("unapproved").password(passwordEncoder.encode("password1"))
                    .fullName("Waiting Person").email("waiting@ustc.test")
                    .role(User.Role.STUDENT).approved(false).university(published).build());
        }
    }

    private static String credentials(String field, String value) {
        return "{\"" + field + "\":\"" + value + "\",\"password\":\"password1\"}";
    }

    @Test
    void signsInWithAnEmailAddress() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("email", "known.person@ustc.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("known"))
                .andExpect(jsonPath("$.university.slug").value(published.getSlug()));
    }

    /** Case is not part of an address, and the unique constraint is case-sensitive. */
    @Test
    void theAddressIsNotCaseSensitive() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("email", "Known.Person@USTC.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("known"));
    }

    /** A browser holding an older bundle still posts a username. */
    @Test
    void theUsernameIsStillAccepted() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("username", "known")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("known"));
    }

    @Test
    void anUnknownAddressAnswersLikeAWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("email", "nobody@ustc.test")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"known.person@ustc.test\",\"password\":\"wrong-one1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void anUnapprovedAccountIsToldItIsWaiting() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("email", "waiting@ustc.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("pending administrator approval")));
    }

    // --- Signing up ---

    private String signup(String slug, String email) {
        return """
                {"universitySlug":"%s","password":"password1","fullName":"New Person",
                 "email":"%s","role":"STUDENT","idNo":"77","department":"CSE",
                 "batch":"Batch 21","semester":"1st Year 1st Semester","section":"Section A"}"""
                .formatted(slug, email);
    }

    @Test
    void signingUpJoinsTheNamedUniversityAndGetsAGeneratedUsername() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signup(published.getSlug(), "Fresh.Face@Example.TEST")))
                .andExpect(status().isOk());

        User created = userRepository.findByEmail("fresh.face@example.test").orElseThrow();
        assertThat(created.getUsername()).isEqualTo("fresh.face");
        assertThat(created.getUniversity().getId()).isEqualTo(published.getId());
        assertThat(created.isApproved()).isFalse();
    }

    /**
     * The generated username is globally unique, so a second person whose
     * address has the same local part is given a suffix rather than a failure.
     */
    @Test
    void aSecondPersonWithTheSameLocalPartGetsASuffix() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signup(published.getSlug(), "twin@one.test")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signup(published.getSlug(), "twin@two.test")))
                .andExpect(status().isOk());

        assertThat(userRepository.findByEmail("twin@one.test").orElseThrow().getUsername())
                .isEqualTo("twin");
        assertThat(userRepository.findByEmail("twin@two.test").orElseThrow().getUsername())
                .isEqualTo("twin2");
    }

    /**
     * The publish flag is what opens a university for registration. If an
     * unlisted one accepted sign-ups, the platform owner's gate would only be
     * hiding it from the homepage.
     */
    @Test
    void anUnpublishedUniversityIsNotOpenForRegistration() throws Exception {
        University draft = universityRepository.save(University.builder()
                .name("Draft College").domain("draft.test").slug("draft-test")
                .published(false).build());

        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signup(draft.getSlug(), "hopeful@draft.test")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("not open for registration")));

        assertThat(userRepository.findByEmail("hopeful@draft.test")).isEmpty();
    }

    @Test
    void anUnknownUniversityIsRefused() throws Exception {
        mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signup("no-such-place", "lost@nowhere.test")))
                .andExpect(status().isBadRequest());
    }
}
