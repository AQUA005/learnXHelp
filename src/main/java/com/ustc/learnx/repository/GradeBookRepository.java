package com.ustc.learnx.repository;

import com.ustc.learnx.entity.GradeBook;
import com.ustc.learnx.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GradeBookRepository extends JpaRepository<GradeBook, Long> {
    @Override
    @EntityGraph(attributePaths = {"student", "student.studentClass", "student.university"})
    List<GradeBook> findAll();

    @EntityGraph(attributePaths = {"student", "student.studentClass", "student.university"})
    List<GradeBook> findByStudent(User student);
    @EntityGraph(attributePaths = {"student", "student.studentClass", "student.university"})
    List<GradeBook> findByCourseNameAndAssessmentName(String courseName, String assessmentName);
}
