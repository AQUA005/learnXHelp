package com.ustc.learnx.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks down who may call what.
 *
 * <p>Each case asserts the HTTP status a given role receives from a given
 * endpoint. A regression that widens access - a dropped PreAuthorize, a stray
 * permitAll - fails here rather than in production.
 *
 * <p>These assertions are about authorization only, so request bodies are
 * omitted. That is deliberate: a 400 would mean the request got past the
 * security check, which is why no case expects one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationMatrixTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private com.ustc.learnx.repository.UserRepository userRepository;

    @Autowired
    private com.ustc.learnx.repository.UniversityRepository universityRepository;

    @Autowired
    private com.ustc.learnx.repository.StudentClassRepository studentClassRepository;

    /**
     * Gives each impersonated principal a real account.
     *
     * <p>A mock principal alone is not enough: the services resolve the caller
     * from the database, so without a row every request would be refused for
     * lack of an account rather than by the rule under test.
     */
    @org.junit.jupiter.api.BeforeEach
    void seedAccounts() {
        if (userRepository.findByUsername("teacher").isPresent()) {
            return;
        }
        var university = universityRepository.findAll().stream().findFirst().orElseThrow();
        var studentClass = studentClassRepository.save(com.ustc.learnx.entity.StudentClass.builder()
                .batch("Batch 21").department("CSE").section("Section A")
                .university(university).build());

        record Account(String username, com.ustc.learnx.entity.User.Role role, boolean inClass) {
        }
        for (Account account : List.of(
                new Account("student", com.ustc.learnx.entity.User.Role.STUDENT, true),
                new Account("cr", com.ustc.learnx.entity.User.Role.CR, true),
                new Account("teacher", com.ustc.learnx.entity.User.Role.TEACHER, false),
                new Account("admin", com.ustc.learnx.entity.User.Role.ADMIN, false))) {
            userRepository.save(com.ustc.learnx.entity.User.builder()
                    .username(account.username())
                    .password("irrelevant")
                    .fullName(account.username())
                    .email(account.username() + "@ustc.test")
                    .role(account.role())
                    .approved(true)
                    .studentClass(account.inClass() ? studentClass : null)
                    .university(university)
                    .build());
        }
    }

    // -----------------------------------------------------------------
    // Anonymous callers
    // -----------------------------------------------------------------

    /**
     * Endpoints that used to be permitAll. Every one of them let an
     * unauthenticated caller read or destroy institutional data.
     */
    @ParameterizedTest(name = "anonymous {0} {1} is rejected")
    @CsvSource({
            "GET,    /api/admin/pending",
            "POST,   /api/admin/approve/1",
            "DELETE, /api/admin/reject/1",
            "GET,    /api/admin/teachers",
            "GET,    /api/admin/students",
            "GET,    /api/admin/users",
            "POST,   /api/admin/users/1/reset-password",
            "GET,    /api/master/universities",
            "POST,   /api/master/universities/1/reset-admin",
            "DELETE, /api/master/universities/1",
            "GET,    /api/master/users/emails",
            "POST,   /api/master/send-email",
            "GET,    /api/dashboard/all-grades",
            "POST,   /api/dashboard/grades",
            "GET,    /api/mail/users",
            "GET,    /api/mail/status",
            "POST,   /api/mail/test",
            "POST,   /api/mail/send",
            "GET,    /api/schedule/audit-logs",
            "GET,    /api/resources/pending",
            "POST,   /api/bugs/report",
            "GET,    /api/auth/current-user",
            "GET,    /h2-console/"
    })
    void anonymousIsRejected(String method, String path) throws Exception {
        // A valid CSRF token is supplied so that the CSRF filter, which runs
        // first and would answer 403, cannot mask the authentication result.
        mvc.perform(request(method, path).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Without a CSRF token an anonymous write is stopped even earlier, by the
     * CSRF filter. Either way it never reaches a handler.
     */
    @ParameterizedTest(name = "anonymous {0} {1} without a CSRF token is rejected")
    @CsvSource({
            "POST,   /api/admin/approve/1",
            "DELETE, /api/admin/reject/1",
            "POST,   /api/master/send-email",
            "POST,   /api/dashboard/grades"
    })
    void anonymousWriteWithoutCsrfIsRejected(String method, String path) throws Exception {
        mvc.perform(request(method, path)).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "public asset {0} is served")
    @CsvSource({"/", "/index.html"})
    void publicAssetsRemainReachable(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    // -----------------------------------------------------------------
    // Signed-in students
    // -----------------------------------------------------------------

    /** A student must not reach anything reserved for staff. */
    @ParameterizedTest(name = "student is denied {0} {1}")
    @CsvSource({
            "GET,    /api/admin/pending",
            "POST,   /api/admin/approve/1",
            "DELETE, /api/admin/reject/1",
            "GET,    /api/master/users/emails",
            "POST,   /api/master/send-email",
            "GET,    /api/dashboard/all-grades",
            "POST,   /api/dashboard/grades",
            "DELETE, /api/dashboard/grades/1",
            "GET,    /api/mail/users",
            "GET,    /api/mail/status",
            "POST,   /api/mail/test",
            "POST,   /api/mail/send",
            "POST,   /api/metadata",
            "DELETE, /api/metadata/1",
            "GET,    /api/schedule/audit-logs",
            "POST,   /api/schedule/routine",
            "POST,   /api/schedule/ct",
            "GET,    /api/resources/pending",
            "POST,   /api/resources/1/approve",
            "POST,   /api/exams/create",
            "GET,    /api/exams/1/submissions",
            "POST,   /api/announcements",
            "GET,    /api/admin/users",
            "POST,   /api/admin/users/1/reset-password",
            "DELETE, /api/announcements/1"
    })
    void studentIsDeniedStaffEndpoints(String method, String path) throws Exception {
        mvc.perform(request(method, path).with(user("student").roles("STUDENT")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------
    // Role hierarchy
    // -----------------------------------------------------------------

    /** A class representative may run their class, but is not staff. */
    @ParameterizedTest(name = "cr is denied {0} {1}")
    @CsvSource({
            "GET, /api/admin/pending",
            "GET, /api/dashboard/all-grades",
            "GET, /api/master/users/emails",
            "GET, /api/schedule/audit-logs"
    })
    void classRepresentativeIsDeniedStaffEndpoints(String method, String path) throws Exception {
        mvc.perform(request(method, path).with(user("cr").roles("CR")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /** A teacher may grade and moderate, but may not administer the university. */
    @ParameterizedTest(name = "teacher is denied {0} {1}")
    @CsvSource({
            "GET, /api/admin/pending",
            "GET, /api/admin/teachers",
            "GET, /api/admin/users",
            "POST, /api/admin/users/1/reset-password",
            "GET, /api/master/users/emails",
            "GET, /api/schedule/audit-logs"
    })
    void teacherIsDeniedAdminEndpoints(String method, String path) throws Exception {
        mvc.perform(request(method, path).with(user("teacher").roles("TEACHER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /** A university administrator holds no platform-level powers. */
    @ParameterizedTest(name = "admin is denied {0} {1}")
    @CsvSource({
            "GET,    /api/master/universities",
            "GET,    /api/master/users/emails",
            "POST,   /api/master/send-email",
            "DELETE, /api/master/universities/1"
    })
    void adminIsDeniedPlatformEndpoints(String method, String path) throws Exception {
        mvc.perform(request(method, path).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /**
     * The hierarchy grants downward, so a teacher satisfies a CR requirement.
     * Reaching the handler at all proves the security check passed.
     */
    @ParameterizedTest(name = "teacher reaches {0} {1}")
    @CsvSource({
            "GET, /api/resources/pending",
            "GET, /api/dashboard/all-grades"
    })
    void teacherReachesModerationEndpoints(String method, String path) throws Exception {
        mvc.perform(request(method, path).with(user("teacher").roles("TEACHER")).with(csrf()))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------
    // CSRF
    // -----------------------------------------------------------------

    /**
     * Because sessions are carried in a cookie, a state-changing request that
     * arrives without a CSRF token must be refused. Otherwise any other site
     * could make this request on a logged-in user's behalf.
     */
    @Test
    void stateChangingRequestWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/announcements")
                        .with(user("cr").roles("CR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"global\":false}"))
                .andExpect(status().isForbidden());
    }

    /**
     * A body that binds and validates against every write endpoint covered here.
     *
     * <p>Spring resolves and validates the request body before method security
     * runs, so an invalid body answers 400 and the authorization rule is never
     * reached. To keep these cases about authorization alone, this supplies
     * every primitive Jackson requires and every field Bean Validation demands
     * across these controllers. Unknown properties are ignored, so one body
     * serves all of them.
     */
    private static final String BINDABLE_BODY = """
            {"global":false,"guest":false,"published":false,"durationMinutes":1,\
            "points":1,"marksObtained":0,"maxMarks":0,"classAverage":0,\
            "classHighest":0,"percentile":0,"alreadySubmitted":false,\
            "title":"t","content":"c","courseName":"c","type":"DEPARTMENT","value":"v",\
            "dayOfWeek":"MONDAY","startTime":"10:00:00","endTime":"11:00:00",\
            "dateTime":"2026-09-01T10:00:00","studentUsername":"student",\
            "assessmentName":"CT 1","to":"student@ustc.test","subject":"s","body":"b"}""";

    /**
     * Exams carry their times as ISO date-time strings, where a schedule item
     * uses a plain time of day, so the two cannot share one body.
     */
    private static final String EXAM_BODY = """
            {"title":"t","description":"d","durationMinutes":1,\
            "startTime":"2026-09-01T10:00:00","endTime":"2026-09-01T11:00:00",\
            "questions":[{"questionText":"q","questionType":"MCQ","points":1,\
            "correctAnswer":"A"}]}""";

    private static MockHttpServletRequestBuilder request(String method, String path) {
        String target = path.trim();
        String body = target.startsWith("/api/exams") ? EXAM_BODY : BINDABLE_BODY;
        return switch (method.trim().toUpperCase()) {
            case "GET" -> get(target);
            case "POST" -> post(target).contentType(MediaType.APPLICATION_JSON).content(body);
            case "PUT" -> put(target).contentType(MediaType.APPLICATION_JSON).content(body);
            case "DELETE" -> delete(target);
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
    }
}
