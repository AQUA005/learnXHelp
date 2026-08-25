package com.ustc.learnx.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.entity.ClassCourseAssignment;
import com.ustc.learnx.entity.Course;
import com.ustc.learnx.entity.PromotionHistory;
import com.ustc.learnx.entity.StudentClass;
import com.ustc.learnx.entity.SystemMetadata;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.ClassCourseAssignmentRepository;
import com.ustc.learnx.repository.CourseRepository;
import com.ustc.learnx.repository.PromotionHistoryRepository;
import com.ustc.learnx.repository.StudentClassRepository;
import com.ustc.learnx.repository.SystemMetadataRepository;
import com.ustc.learnx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Moves a whole class on to the next semester, and undoes it.
 *
 * <p>The order of semesters comes from the university's own configured list
 * rather than a hardcoded one. The hardcoded list read
 * {@code "1st Year 1st Semester"} while accounts and the signup form use
 * {@code "1st Semester"}, so no student's semester was ever found in it and
 * promoting a class silently reset every one of them to the first entry.
 */
@Service
@RequiredArgsConstructor
public class PromotionService {

    private final StudentClassRepository studentClassRepository;
    private final UserRepository userRepository;
    private final SystemMetadataRepository systemMetadataRepository;
    private final ClassCourseAssignmentRepository classCourseAssignmentRepository;
    private final PromotionHistoryRepository promotionHistoryRepository;
    private final CourseRepository courseRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    /** What a class's course and teacher assignments looked like before a promotion. */
    public record AssignmentSnapshot(
            Long courseId, String courseCode, String courseName,
            Long teacherId, String teacherUsername, String teacherName) {
    }

    public record PromotionResult(String fromSemester, String toSemester, int studentsMoved) {
    }

    @Transactional
    public PromotionResult promote(Long classId) {
        StudentClass studentClass = requireClass(classId);
        List<User> students = userRepository.findByStudentClass(studentClass);
        if (students.isEmpty()) {
            throw new ValidationException("No students are enrolled in this class group");
        }

        List<String> ladder = semesterLadder(studentClass);
        String currentSemester = currentSemesterOf(students, ladder);

        int index = ladder.indexOf(currentSemester);
        if (index < 0) {
            throw new ValidationException(
                    "The class is on '" + currentSemester + "', which is not one of the configured semesters");
        }
        if (index == ladder.size() - 1) {
            throw new ValidationException("The class is already in the final semester");
        }
        String nextSemester = ladder.get(index + 1);

        // Record what the assignments were, so the move can be undone.
        List<ClassCourseAssignment> assignments =
                classCourseAssignmentRepository.findByStudentClass(studentClass);
        List<AssignmentSnapshot> snapshots = assignments.stream()
                .map(a -> new AssignmentSnapshot(
                        a.getCourse().getId(), a.getCourse().getCode(), a.getCourse().getName(),
                        a.getTeacher().getId(), a.getTeacher().getUsername(), a.getTeacher().getFullName()))
                .toList();

        promotionHistoryRepository.save(PromotionHistory.builder()
                .studentClass(studentClass)
                .fromSemester(currentSemester)
                .toSemester(nextSemester)
                .timestamp(LocalDateTime.now())
                .savedAssignmentsJson(writeSnapshots(snapshots))
                .build());

        for (User student : students) {
            student.setSemester(nextSemester);
        }
        userRepository.saveAll(students);

        // Next semester's courses are chosen afresh.
        classCourseAssignmentRepository.deleteByStudentClass(studentClass);

        return new PromotionResult(currentSemester, nextSemester, students.size());
    }

    @Transactional
    public PromotionResult rollback(Long classId) {
        StudentClass studentClass = requireClass(classId);

        List<PromotionHistory> history =
                promotionHistoryRepository.findByStudentClassOrderByTimestampDesc(studentClass);
        if (history.isEmpty()) {
            throw new ValidationException("This class group has no promotion to undo");
        }
        PromotionHistory latest = history.get(0);

        List<User> students = userRepository.findByStudentClass(studentClass);
        for (User student : students) {
            student.setSemester(latest.getFromSemester());
        }
        userRepository.saveAll(students);

        classCourseAssignmentRepository.deleteByStudentClass(studentClass);

        List<AssignmentSnapshot> snapshots = readSnapshots(latest.getSavedAssignmentsJson());
        List<ClassCourseAssignment> restored = new ArrayList<>();
        for (AssignmentSnapshot snapshot : snapshots) {
            Optional<Course> course = courseRepository.findById(snapshot.courseId());
            Optional<User> teacher = userRepository.findById(snapshot.teacherId());
            // A course or teacher deleted since the promotion is skipped rather
            // than failing the whole rollback.
            if (course.isPresent() && teacher.isPresent()) {
                restored.add(ClassCourseAssignment.builder()
                        .studentClass(studentClass)
                        .course(course.get())
                        .teacher(teacher.get())
                        .build());
            }
        }
        classCourseAssignmentRepository.saveAll(restored);

        // Consumed, so the same promotion cannot be undone twice.
        promotionHistoryRepository.delete(latest);

        return new PromotionResult(latest.getToSemester(), latest.getFromSemester(), students.size());
    }

    private StudentClass requireClass(Long classId) {
        StudentClass studentClass = studentClassRepository.findById(classId)
                .orElseThrow(() -> new NotFoundException("Class group not found"));
        currentUserService.assertSameUniversity(studentClass.getUniversity());
        return studentClass;
    }

    /**
     * The configured semesters, in order.
     *
     * <p>Ordering follows the leading number in each entry, so the list is
     * correct however the administrator happened to enter it.
     */
    private List<String> semesterLadder(StudentClass studentClass) {
        List<SystemMetadata> configured = studentClass.getUniversity() == null
                ? systemMetadataRepository.findByType("SEMESTER")
                : systemMetadataRepository.findByTypeAndUniversity("SEMESTER", studentClass.getUniversity());

        if (configured.isEmpty()) {
            throw new ValidationException(
                    "No semesters are configured for this university, so a class cannot be promoted");
        }
        return configured.stream()
                .map(SystemMetadata::getValue)
                .distinct()
                .sorted(Comparator.comparingInt(PromotionService::leadingNumber)
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /**
     * The semester the class is on: whichever value most of its students hold.
     *
     * <p>Taking the first student's value meant one mis-set record could decide
     * the move for the whole cohort.
     */
    private static String currentSemesterOf(List<User> students, List<String> ladder) {
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (User student : students) {
            if (student.getSemester() != null && !student.getSemester().isBlank()) {
                tally.merge(student.getSemester(), 1, Integer::sum);
            }
        }
        if (tally.isEmpty()) {
            throw new ValidationException("No student in this class has a semester recorded");
        }
        return tally.entrySet().stream()
                // Ties break towards the earlier semester, so nobody is skipped ahead.
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(e -> -ladder.indexOf(e.getKey())))
                .map(Map.Entry::getKey)
                .orElseThrow();
    }

    /** The number a semester name starts with, or a large value if it has none. */
    private static int leadingNumber(String value) {
        int i = 0;
        while (i < value.length() && Character.isDigit(value.charAt(i))) {
            i++;
        }
        if (i == 0) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(value.substring(0, i));
    }

    private String writeSnapshots(List<AssignmentSnapshot> snapshots) {
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (Exception e) {
            throw new IllegalStateException("Could not record the assignment snapshot", e);
        }
    }

    private List<AssignmentSnapshot> readSnapshots(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AssignmentSnapshot>>() {
            });
        } catch (Exception e) {
            throw new ValidationException("The saved assignment snapshot could not be read");
        }
    }
}
