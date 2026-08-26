package com.ustc.learnx.security;

import com.ustc.learnx.entity.Course;
import com.ustc.learnx.entity.StudentClass;
import com.ustc.learnx.entity.SystemMetadata;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.entity.User.Role;
import com.ustc.learnx.repository.CourseRepository;
import com.ustc.learnx.repository.StudentClassRepository;
import com.ustc.learnx.repository.SystemMetadataRepository;
import com.ustc.learnx.repository.UniversityRepository;
import com.ustc.learnx.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An administrator of one university cannot reach another university's rows.
 *
 * <p>Holding the right role is not the same as owning the row. Every endpoint
 * under {@code /api/admin} that takes an id in its path used to load that id and
 * act on it with no ownership check, so any administrator could pass any id.
 * {@code assign-cr} was the sharpest edge: it resolved the target by username
 * across the whole platform and then moved that account into the caller's class.
 *
 * <p>This class takes a database of its own. The rest of the suite anchors its
 * fixtures to {@code universityRepository.findAll().stream().findFirst()}, so
 * inserting a second university into the shared database would make those
 * classes depend on execution order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:learnx-tenants;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
class TenantIsolationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UniversityRepository universityRepository;
    @Autowired private StudentClassRepository studentClassRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SystemMetadataRepository systemMetadataRepository;

    /** Rows belonging to the university the caller is <em>not</em> an administrator of. */
    private StudentClass otherClass;
    private Course otherCourse;
    private SystemMetadata otherMetadata;
    private User otherStudent;
    private User otherTeacher;

    @BeforeEach
    void seedTwoUniversities() {
        University own = universityRepository.findAll().stream().findFirst().orElseThrow();
        University other = universityRepository.findByName("Rival Institute of Technology")
                .orElseGet(() -> universityRepository.save(University.builder()
                        .name("Rival Institute of Technology")
                        .domain("rival.test")
                        .slug("rival-test")
                        .published(true)
                        .build()));

        if (userRepository.findByUsername("own-admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("own-admin").password("x").fullName("Own Admin")
                    .email("own-admin@own.test").role(Role.ADMIN).approved(true)
                    .university(own).build());
        }

        otherClass = studentClassRepository.findByUniversityAndBatchAndDepartmentAndSection(
                        other, "Batch 21", "CSE", "Section A")
                .orElseGet(() -> studentClassRepository.save(StudentClass.builder()
                        .batch("Batch 21").department("CSE").section("Section A")
                        .university(other).build()));

        otherCourse = courseRepository.findByUniversity(other).stream().findFirst()
                .orElseGet(() -> courseRepository.save(Course.builder()
                        .code("RIV-101").name("Rival Computing").credits(3.0)
                        .semester("1st Year 1st Semester").department("CSE")
                        .university(other).build()));

        otherMetadata = systemMetadataRepository.findByTypeAndUniversity("DEPARTMENT", other)
                .stream().findFirst()
                .orElseGet(() -> systemMetadataRepository.save(SystemMetadata.builder()
                        .type("DEPARTMENT").value("Rival CSE").university(other).build()));

        otherStudent = userRepository.findByUsername("rival-student")
                .orElseGet(() -> userRepository.save(User.builder()
                        .username("rival-student").password("x").fullName("Rival Student")
                        .email("rival-student@rival.test").role(Role.STUDENT).approved(true)
                        .university(other).studentClass(otherClass).build()));

        otherTeacher = userRepository.findByUsername("rival-teacher")
                .orElseGet(() -> userRepository.save(User.builder()
                        .username("rival-teacher").password("x").fullName("Rival Teacher")
                        .email("rival-teacher@rival.test").role(Role.TEACHER).approved(true)
                        .university(other).build()));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor ownAdmin() {
        return user("own-admin").roles("ADMIN");
    }

    /**
     * The control for every case below. Without it a 403 raised for some other
     * reason — an administrator with no university of their own, say — would let
     * the rest of this class pass while proving nothing.
     */
    @Test
    void canStillReachItsOwnUniversitysClass() throws Exception {
        University own = universityRepository.findAll().stream().findFirst().orElseThrow();
        StudentClass ownClass = studentClassRepository
                .findByUniversityAndBatchAndDepartmentAndSection(own, "Batch 22", "EEE", "Section B")
                .orElseGet(() -> studentClassRepository.save(StudentClass.builder()
                        .batch("Batch 22").department("EEE").section("Section B")
                        .university(own).build()));

        mvc.perform(get("/api/admin/classes/" + ownClass.getId() + "/assignments").with(ownAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void cannotReadAnotherUniversitysClassAssignments() throws Exception {
        mvc.perform(get("/api/admin/classes/" + otherClass.getId() + "/assignments").with(ownAdmin()))
                .andExpect(status().isForbidden());
    }

    /** The consolidated class view carries a roster, so it must be scoped too. */
    @Test
    void cannotReadAnotherUniversitysClassDetail() throws Exception {
        mvc.perform(get("/api/admin/classes/" + otherClass.getId()).with(ownAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotPromoteAnotherUniversitysClass() throws Exception {
        mvc.perform(post("/api/admin/classes/" + otherClass.getId() + "/promote")
                        .with(ownAdmin()).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/classes/" + otherClass.getId() + "/rollback-promotion")
                        .with(ownAdmin()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotDeleteAnotherUniversitysCourse() throws Exception {
        mvc.perform(delete("/api/admin/courses/" + otherCourse.getId()).with(ownAdmin()).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(courseRepository.findById(otherCourse.getId())).isPresent();
    }

    @Test
    void cannotDeleteAnotherUniversitysMetadata() throws Exception {
        mvc.perform(delete("/api/admin/metadata/" + otherMetadata.getId()).with(ownAdmin()).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(systemMetadataRepository.findById(otherMetadata.getId())).isPresent();
    }

    @Test
    void cannotDeleteAnotherUniversitysTeacher() throws Exception {
        mvc.perform(delete("/api/admin/teachers/" + otherTeacher.getId()).with(ownAdmin()).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findById(otherTeacher.getId())).isPresent();
    }

    /**
     * The one that mattered most: the target used to be resolved by username
     * across every tenant, so this call would have moved a rival university's
     * student into the caller's class and promoted them to CR.
     */
    @Test
    void cannotMakeAnotherUniversitysStudentClassRepresentative() throws Exception {
        mvc.perform(post("/api/admin/classes/" + otherClass.getId() + "/assign-cr")
                        .with(ownAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"rival-student\"}"))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(otherStudent.getId()).orElseThrow().getRole())
                .isEqualTo(Role.STUDENT);
    }

    @Test
    void cannotAssignACourseWithinAnotherUniversitysClass() throws Exception {
        mvc.perform(post("/api/admin/classes/" + otherClass.getId() + "/assign-course")
                        .with(ownAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + otherCourse.getId()
                                + ",\"teacherId\":" + otherTeacher.getId() + "}"))
                .andExpect(status().isForbidden());
    }
}
