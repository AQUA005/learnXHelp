package com.ustc.learnx.service;

import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.dto.GradeDtos.AddGradeRequest;
import com.ustc.learnx.dto.GradeDtos.GradeResponse;
import com.ustc.learnx.dto.GradeDtos.PerformanceStat;
import com.ustc.learnx.entity.GradeBook;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.GradeBookRepository;
import com.ustc.learnx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The gradebook, and the comparison figures a student sees against it.
 *
 * <p>Every read and write is limited to the caller's university. Grades were
 * previously recorded and listed with no such limit, so a teacher could mark a
 * student at another institution and the class average a student saw was
 * computed across all of them.
 */
@Service
@RequiredArgsConstructor
public class GradeService {

    private final GradeBookRepository gradeBookRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    /** The caller's own results, each ranked against their cohort. */
    @Transactional(readOnly = true)
    public List<PerformanceStat> performanceForCurrentUser() {
        User user = currentUserService.requireCurrentUser();
        Long universityId = user.getUniversity() == null ? null : user.getUniversity().getId();

        List<PerformanceStat> stats = new ArrayList<>();
        for (GradeBook grade : gradeBookRepository.findByStudent(user)) {
            // Ranked only against students at the same university.
            List<GradeBook> cohort = universityId == null
                    ? gradeBookRepository.findByCourseNameAndAssessmentName(
                            grade.getCourseName(), grade.getAssessmentName())
                    : gradeBookRepository.findByCourseNameAndAssessmentNameAndStudent_University_Id(
                            grade.getCourseName(), grade.getAssessmentName(), universityId);

            stats.add(rank(grade, cohort));
        }
        return stats;
    }

    @Transactional(readOnly = true)
    public List<GradeResponse> listAllGrades() {
        User user = currentUserService.requireCurrentUser();

        List<GradeBook> grades = user.getRole() == User.Role.SYSTEM_ADMIN
                ? gradeBookRepository.findAll()
                : gradeBookRepository.findByStudent_University_Id(currentUserService.requireUniversityId());

        return grades.stream().map(GradeService::toResponse).toList();
    }

    @Transactional
    public GradeResponse addGrade(AddGradeRequest request) {
        if (request.maxMarks() <= 0) {
            throw new ValidationException("Total marks must be greater than zero");
        }
        if (request.marksObtained() > request.maxMarks()) {
            throw new ValidationException("Marks obtained cannot exceed the total marks");
        }

        User student = userRepository.findByUsername(request.studentUsername())
                .orElseThrow(() -> new NotFoundException("Student username not found"));
        // A teacher may only grade students at their own university.
        currentUserService.assertSameUniversity(student.getUniversity());

        GradeBook saved = gradeBookRepository.save(GradeBook.builder()
                .student(student)
                .courseName(request.courseName())
                .assessmentName(request.assessmentName())
                .marksObtained(request.marksObtained())
                .maxMarks(request.maxMarks())
                .build());

        return toResponse(saved);
    }

    @Transactional
    public void deleteGrade(Long id) {
        GradeBook grade = gradeBookRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Grade record not found"));
        currentUserService.assertSameUniversity(
                grade.getStudent() == null ? null : grade.getStudent().getUniversity());
        gradeBookRepository.delete(grade);
    }

    /** Average, highest and percentile for one grade against its cohort. */
    private static PerformanceStat rank(GradeBook grade, List<GradeBook> cohort) {
        double total = 0;
        double highest = 0;
        int atOrBelow = 0;

        for (GradeBook other : cohort) {
            total += other.getMarksObtained();
            highest = Math.max(highest, other.getMarksObtained());
            if (other.getMarksObtained() <= grade.getMarksObtained()) {
                atOrBelow++;
            }
        }

        int count = cohort.size();
        double average = count > 0 ? total / count : 0;
        double percentile = count > 0 ? ((double) atOrBelow / count) * 100 : 0;

        return new PerformanceStat(
                grade.getId(),
                grade.getCourseName(),
                grade.getAssessmentName(),
                grade.getMarksObtained(),
                grade.getMaxMarks(),
                round(average),
                round(highest),
                round(percentile));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static GradeResponse toResponse(GradeBook grade) {
        User student = grade.getStudent();
        return new GradeResponse(
                grade.getId(),
                student == null ? null : student.getUsername(),
                student == null ? null : student.getFullName(),
                grade.getCourseName(),
                grade.getAssessmentName(),
                grade.getMarksObtained(),
                grade.getMaxMarks());
    }
}
