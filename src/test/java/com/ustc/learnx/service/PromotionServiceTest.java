package com.ustc.learnx.service;

import com.ustc.learnx.common.ValidationException;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Promotion moves a whole cohort, so the cases that decide where they land are
 * pinned here.
 *
 * <p>The bug this covers: the semester order was hardcoded as
 * "1st Year 1st Semester" while accounts hold "1st Semester", so no student's
 * semester was ever found in the list and promoting a class reset every one of
 * them to the first entry instead of advancing them.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PromotionServiceTest {

    @Autowired private PromotionService promotionService;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentClassRepository studentClassRepository;
    @Autowired private UniversityRepository universityRepository;

    private StudentClass studentClass;
    private University university;

    @BeforeEach
    void setUp() {
        university = universityRepository.findAll().stream().findFirst().orElseThrow();
        studentClass = studentClassRepository.save(StudentClass.builder()
                .batch("Batch 21").department("CSE").section("Section A")
                .university(university).build());
    }

    private User student(String username, String semester) {
        return userRepository.save(User.builder()
                .username(username).password("x").fullName(username)
                .email(username + "@ustc.test")
                .role(User.Role.STUDENT).approved(true).semester(semester)
                .studentClass(studentClass).university(university).build());
    }

    private String semesterOf(String username) {
        return userRepository.findByUsername(username).orElseThrow().getSemester();
    }

    /** The case that was broken: students advance by one, not back to the start. */
    @Test
    @WithMockUser(username = "promo-admin", roles = "ADMIN")
    void promotionAdvancesStudentsByOneSemester() {
        adminAccount();
        student("s1", "3rd Semester");
        student("s2", "3rd Semester");

        PromotionService.PromotionResult result = promotionService.promote(studentClass.getId());

        assertThat(result.fromSemester()).isEqualTo("3rd Semester");
        assertThat(result.toSemester()).isEqualTo("4th Semester");
        assertThat(result.studentsMoved()).isEqualTo(2);
        assertThat(semesterOf("s1")).isEqualTo("4th Semester");
        assertThat(semesterOf("s2")).isEqualTo("4th Semester");
    }

    /** Ordering follows the leading number, not the order rows were inserted. */
    @Test
    @WithMockUser(username = "promo-admin", roles = "ADMIN")
    void promotionFollowsNumericSemesterOrder() {
        adminAccount();
        student("s1", "8th Semester");

        assertThatThrownBy(() -> promotionService.promote(studentClass.getId()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("final semester");
    }

    /** One mis-set record must not decide the move for the whole cohort. */
    @Test
    @WithMockUser(username = "promo-admin", roles = "ADMIN")
    void theSemesterMostStudentsShareDecidesTheMove() {
        adminAccount();
        student("s1", "2nd Semester");
        student("s2", "2nd Semester");
        student("s3", "5th Semester");

        PromotionService.PromotionResult result = promotionService.promote(studentClass.getId());

        assertThat(result.fromSemester()).isEqualTo("2nd Semester");
        assertThat(result.toSemester()).isEqualTo("3rd Semester");
        // Everyone is brought onto the same semester, including the outlier.
        assertThat(semesterOf("s3")).isEqualTo("3rd Semester");
    }

    @Test
    @WithMockUser(username = "promo-admin", roles = "ADMIN")
    void promotionIsRefusedForAnEmptyClass() {
        adminAccount();

        assertThatThrownBy(() -> promotionService.promote(studentClass.getId()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No students");
    }

    @Test
    @WithMockUser(username = "promo-admin", roles = "ADMIN")
    void aSemesterOutsideTheConfiguredListIsReported() {
        adminAccount();
        student("s1", "Michaelmas Term");

        assertThatThrownBy(() -> promotionService.promote(studentClass.getId()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not one of the configured semesters");
    }

    /** Undoing a promotion puts the cohort back where it started. */
    @Test
    @WithMockUser(username = "promo-admin", roles = "ADMIN")
    void rollbackRestoresThePreviousSemester() {
        adminAccount();
        student("s1", "3rd Semester");

        promotionService.promote(studentClass.getId());
        assertThat(semesterOf("s1")).isEqualTo("4th Semester");

        PromotionService.PromotionResult result = promotionService.rollback(studentClass.getId());

        assertThat(result.toSemester()).isEqualTo("3rd Semester");
        assertThat(semesterOf("s1")).isEqualTo("3rd Semester");
    }

    /** A promotion can only be undone once. */
    @Test
    @WithMockUser(username = "promo-admin", roles = "ADMIN")
    void thereIsNothingToRollBackTwice() {
        adminAccount();
        student("s1", "3rd Semester");

        promotionService.promote(studentClass.getId());
        promotionService.rollback(studentClass.getId());

        assertThatThrownBy(() -> promotionService.rollback(studentClass.getId()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no promotion to undo");
    }

    @Test
    @WithMockUser(username = "promo-admin", roles = "ADMIN")
    void rollbackIsRefusedWhenNothingWasPromoted() {
        adminAccount();
        student("s1", "3rd Semester");

        assertThatThrownBy(() -> promotionService.rollback(studentClass.getId()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no promotion to undo");
    }

    /** The administrator performing the promotion needs an account of their own. */
    private void adminAccount() {
        if (userRepository.findByUsername("promo-admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("promo-admin").password("x").fullName("Promo Admin")
                    .email("promo-admin@ustc.test")
                    .role(User.Role.ADMIN).approved(true).university(university).build());
        }
    }

    /** Sanity check that the fixture matches the configured semester list. */
    @Test
    @WithMockUser(username = "promo-admin", roles = "ADMIN")
    void configuredSemestersAreTheOnesAccountsActuallyUse() {
        adminAccount();
        student("s1", "1st Semester");

        PromotionService.PromotionResult result = promotionService.promote(studentClass.getId());

        assertThat(List.of(result.fromSemester(), result.toSemester()))
                .containsExactly("1st Semester", "2nd Semester");
    }
}
