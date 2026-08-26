package com.ustc.learnx.service;

import com.ustc.learnx.entity.StudentClass;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.StudentClassRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puts an approved student into the class group their details describe.
 *
 * <p>This is what makes the rest of the application work for them: the routine,
 * the notes library, announcements and class tests are all scoped to a class, so
 * a student with none sees empty screens forever and cannot be told why.
 *
 * <p>It lives here because there are two approval endpoints —
 * {@code /api/admin/approve/{id}} and {@code /api/approvals/approve/{id}} — and
 * only the second one used to do this. The administration screen calls the
 * first, so in practice every student approved through the interface was left
 * without a class.
 */
@Service
@RequiredArgsConstructor
public class ClassPlacementService {

    private static final Logger log = LoggerFactory.getLogger(ClassPlacementService.class);

    private final StudentClassRepository studentClassRepository;

    /**
     * Assigns {@code user} to a class, creating it if this is the first member.
     *
     * <p>Does nothing for staff, or when the account is missing any of the three
     * details that identify a class.
     *
     * @return the class they were placed in, or null if they were not placed
     */
    @Transactional
    public StudentClass place(User user) {
        if (user.getRole() != User.Role.STUDENT && user.getRole() != User.Role.CR) {
            return null;
        }

        String batch = user.getBatch();
        String department = user.getDepartment();
        String section = user.getSection();
        if (isBlank(batch) || isBlank(department) || isBlank(section)) {
            log.warn("Approved {} without a batch, department or section, so they join no class",
                    user.getUsername());
            return null;
        }

        University university = user.getUniversity();
        if (university == null) {
            log.warn("Approved {} with no university, so they join no class", user.getUsername());
            return null;
        }

        // Scoped to the university. Matching on the three strings alone would
        // collapse two universities' identically named classes into one row.
        StudentClass studentClass = studentClassRepository
                .findByUniversityAndBatchAndDepartmentAndSection(university, batch, department, section)
                .orElseGet(() -> studentClassRepository.save(StudentClass.builder()
                        .batch(batch)
                        .department(department)
                        .section(section)
                        .university(university)
                        .build()));

        user.setStudentClass(studentClass);
        return studentClass;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
