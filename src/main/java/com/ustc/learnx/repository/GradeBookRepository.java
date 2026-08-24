package com.ustc.learnx.repository;

import com.ustc.learnx.entity.GradeBook;
import com.ustc.learnx.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GradeBookRepository extends JpaRepository<GradeBook, Long> {
    List<GradeBook> findByStudent(User student);
    List<GradeBook> findByCourseNameAndAssessmentName(String courseName, String assessmentName);
}
