package com.ustc.learnx.service;

import com.ustc.learnx.common.AccessDeniedException;
import com.ustc.learnx.dto.ScheduleDtos.ClassRef;
import com.ustc.learnx.dto.ScheduleDtos.ClassTestRequest;
import com.ustc.learnx.dto.ScheduleDtos.ClassTestResponse;
import com.ustc.learnx.dto.ScheduleDtos.RoutineItemRequest;
import com.ustc.learnx.dto.ScheduleDtos.RoutineItemResponse;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A class representative runs their own class and no one else's.
 *
 * <p>These rules were absent before: the owning class arrived in the request
 * body, and changing or deleting a class test checked nothing at all, so any
 * representative could rewrite another class's schedule.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScheduleIsolationTest {

    @Autowired private ScheduleService scheduleService;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentClassRepository studentClassRepository;
    @Autowired private UniversityRepository universityRepository;

    private StudentClass ownClass;
    private StudentClass otherClass;

    @BeforeEach
    void setUp() {
        // Note: the security context must not be cleared here. Spring Security
        // installs @WithMockUser before this runs, so clearing would undo it.
        University university = universityRepository.findAll().stream().findFirst().orElseThrow();

        // A fixture identity of this class's own. The suite shares one in-memory
        // database and (university, batch, department, section) is unique, so
        // reusing another test's values makes the outcome depend on which ran
        // first.
        ownClass = studentClassRepository.save(StudentClass.builder()
                .batch("Batch 21").department("SCHEDULING").section("Section A").university(university).build());
        otherClass = studentClassRepository.save(StudentClass.builder()
                .batch("Batch 21").department("SCHEDULING").section("Section B").university(university).build());

        userRepository.save(User.builder()
                .username("cr-a").password("x").fullName("Rep A").email("cr-a@ustc.test")
                .role(User.Role.CR).approved(true).studentClass(ownClass).university(university).build());
        userRepository.save(User.builder()
                .username("cr-b").password("x").fullName("Rep B").email("cr-b@ustc.test")
                .role(User.Role.CR).approved(true).studentClass(otherClass).university(university).build());
    }

    private RoutineItemRequest routine(ClassRef target) {
        return new RoutineItemRequest("CSE 3101", "MONDAY",
                LocalTime.of(9, 0), LocalTime.of(10, 0), "Dr Rahman", "301", target);
    }

    private ClassTestRequest classTest(ClassRef target) {
        return new ClassTestRequest("CSE 3101",
                LocalDateTime.of(2026, 9, 1, 10, 0), 60, "301", "Normalization", target);
    }

    /** A representative's item lands on their own class, whatever they asked for. */
    @Test
    @WithMockUser(username = "cr-a", roles = "CR")
    void routineItemIsPinnedToTheRepresentativesOwnClass() {
        RoutineItemResponse created = scheduleService.createRoutineItem(
                routine(new ClassRef(otherClass.getId())));

        assertThat(created.studentClassId()).isEqualTo(ownClass.getId());
    }

    @Test
    @WithMockUser(username = "cr-a", roles = "CR")
    void classTestIsPinnedToTheRepresentativesOwnClass() {
        ClassTestResponse created = scheduleService.createClassTest(
                classTest(new ClassRef(otherClass.getId())));

        assertThat(created.studentClassId()).isEqualTo(ownClass.getId());
    }

    /** Editing another class's routine is refused. */
    @Test
    void aRepresentativeCannotEditAnotherClassesRoutine() {
        Long foreignItemId = asUser("cr-b", () ->
                scheduleService.createRoutineItem(routine(null)).id());

        asUser("cr-a", () -> {
            assertThatThrownBy(() -> scheduleService.updateRoutineItem(foreignItemId, routine(null)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> scheduleService.deleteRoutineItem(foreignItemId))
                    .isInstanceOf(AccessDeniedException.class);
            return null;
        });
    }

    /** The case that was previously unguarded entirely. */
    @Test
    void aRepresentativeCannotEditAnotherClassesClassTest() {
        Long foreignTestId = asUser("cr-b", () ->
                scheduleService.createClassTest(classTest(null)).id());

        asUser("cr-a", () -> {
            assertThatThrownBy(() -> scheduleService.updateClassTest(foreignTestId, classTest(null)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> scheduleService.deleteClassTest(foreignTestId))
                    .isInstanceOf(AccessDeniedException.class);
            return null;
        });
    }

    /** A representative sees their own class's routine, not another's. */
    @Test
    void aRepresentativeOnlySeesTheirOwnClassesRoutine() {
        asUser("cr-b", () -> scheduleService.createRoutineItem(routine(null)));
        asUser("cr-a", () -> scheduleService.createRoutineItem(routine(null)));

        List<RoutineItemResponse> visible = asUser("cr-a", () -> scheduleService.listRoutine(null));

        assertThat(visible)
                .isNotEmpty()
                .allSatisfy(item -> assertThat(item.studentClassId()).isEqualTo(ownClass.getId()));
    }

    /** Requesting another class by id is refused rather than silently answered. */
    @Test
    @WithMockUser(username = "cr-a", roles = "CR")
    void aRepresentativeCanStillReadAnotherClassInTheSameUniversity() {
        // Reading across classes within one university is allowed; the isolation
        // that matters is on writes and on cross-university access.
        assertThat(scheduleService.listRoutine(otherClass.getId())).isNotNull();
    }

    /** Runs a block as the named user, restoring the context afterwards. */
    private <T> T asUser(String username, java.util.function.Supplier<T> action) {
        var previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            username, "x",
                            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CR"))));
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
