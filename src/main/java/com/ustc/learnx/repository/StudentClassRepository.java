package com.ustc.learnx.repository;

import com.ustc.learnx.entity.StudentClass;
import com.ustc.learnx.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface StudentClassRepository extends JpaRepository<StudentClass, Long> {

    /**
     * The class group for a batch/department/section within one university.
     *
     * <p>Always prefer this over a tenant-blind lookup. Two universities can
     * each run a "CSE / Batch 21 / Section A", and matching on those three
     * strings alone would hand both of them the same class row — and with it
     * each other's routine, announcements and notes.
     */
    Optional<StudentClass> findByUniversityAndBatchAndDepartmentAndSection(
            University university, String batch, String department, String section);

    List<StudentClass> findByUniversity(University university);
}
