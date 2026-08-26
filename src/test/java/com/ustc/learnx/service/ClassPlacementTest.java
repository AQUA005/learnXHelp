package com.ustc.learnx.service;

import com.ustc.learnx.entity.StudentClass;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.StudentClassRepository;
import com.ustc.learnx.repository.UniversityRepository;
import com.ustc.learnx.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Approving a student must put them in a class.
 *
 * <p>There are two approval endpoints. {@code /api/approvals/approve/{id}} did
 * this; {@code /api/admin/approve/{id}} only set the flag — and the
 * administration screen calls the second one. So in practice every student
 * approved through the interface belonged to no class, and the routine, notes,
 * announcements and class tests are all scoped to one. Their screens stayed
 * empty with nothing to explain why.
 *
 * <p>Its own database: it approves accounts and creates class groups, which the
 * classes sharing the suite's database make assertions about.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:learnx-placement;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
class ClassPlacementTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UniversityRepository universityRepository;
    @Autowired private StudentClassRepository studentClassRepository;

    private University university;

    @BeforeEach
    void seed() {
        university = universityRepository.findAll().stream().findFirst().orElseThrow();
        if (userRepository.findByUsername("placement-admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("placement-admin").password("x").fullName("Admin")
                    .email("placement-admin@ustc.test").role(User.Role.ADMIN).approved(true)
                    .university(university).build());
        }
    }

    private User pendingStudent(String username, String section) {
        return userRepository.save(User.builder()
                .username(username).password("x").fullName(username)
                .email(username + "@ustc.test")
                .role(User.Role.STUDENT).approved(false)
                .department("CSE").batch("Batch 21").section(section)
                .semester("1st Year 1st Semester")
                .university(university).build());
    }

    /** The endpoint the administration screen actually calls. */
    @Test
    void approvingThroughTheAdministrationScreenPlacesTheStudent() throws Exception {
        User student = pendingStudent("placed-one", "Section P");

        mvc.perform(post("/api/admin/approve/" + student.getId())
                        .with(user("placement-admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());

        User approved = userRepository.findById(student.getId()).orElseThrow();
        assertThat(approved.isApproved()).isTrue();
        assertThat(approved.getStudentClass()).isNotNull();

        // Re-read rather than walking the lazy association: the entity is
        // detached by the time the assertion runs.
        StudentClass placed = studentClassRepository
                .findById(approved.getStudentClass().getId()).orElseThrow();
        assertThat(placed.getSection()).isEqualTo("Section P");
        assertThat(placed.getBatch()).isEqualTo("Batch 21");
        assertThat(placed.getUniversity().getId()).isEqualTo(university.getId());
    }

    /** The second student joins the class the first one created, not a new one. */
    @Test
    void classmatesShareOneClassGroup() throws Exception {
        User first = pendingStudent("placed-two", "Section Q");
        User second = pendingStudent("placed-three", "Section Q");

        for (User student : new User[] { first, second }) {
            mvc.perform(post("/api/admin/approve/" + student.getId())
                            .with(user("placement-admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isOk());
        }

        StudentClass firstClass = userRepository.findById(first.getId()).orElseThrow().getStudentClass();
        StudentClass secondClass = userRepository.findById(second.getId()).orElseThrow().getStudentClass();

        assertThat(firstClass.getId()).isEqualTo(secondClass.getId());
        assertThat(studentClassRepository
                .findByUniversityAndBatchAndDepartmentAndSection(university, "Batch 21", "CSE", "Section Q"))
                .isPresent();
    }

    /** A teacher has no class to join, and approving one must not invent one. */
    @Test
    void staffAreApprovedWithoutAClass() throws Exception {
        User teacher = userRepository.save(User.builder()
                .username("placed-teacher").password("x").fullName("Teacher")
                .email("placed-teacher@ustc.test")
                .role(User.Role.TEACHER).approved(false)
                .department("CSE").designation("Lecturer")
                .university(university).build());

        mvc.perform(post("/api/admin/approve/" + teacher.getId())
                        .with(user("placement-admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());

        User approved = userRepository.findById(teacher.getId()).orElseThrow();
        assertThat(approved.isApproved()).isTrue();
        assertThat(approved.getStudentClass()).isNull();
    }

    /** An incomplete account is still approved, rather than the request failing. */
    @Test
    void aStudentMissingASectionIsApprovedButUnplaced() throws Exception {
        User student = userRepository.save(User.builder()
                .username("placed-partial").password("x").fullName("Partial")
                .email("placed-partial@ustc.test")
                .role(User.Role.STUDENT).approved(false)
                .department("CSE").batch("Batch 21")
                .university(university).build());

        mvc.perform(post("/api/admin/approve/" + student.getId())
                        .with(user("placement-admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());

        User approved = userRepository.findById(student.getId()).orElseThrow();
        assertThat(approved.isApproved()).isTrue();
        assertThat(approved.getStudentClass()).isNull();
    }
}
