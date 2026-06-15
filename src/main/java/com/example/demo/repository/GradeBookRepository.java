package com.example.demo.repository;

import com.example.demo.entity.GradeBook;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GradeBookRepository extends JpaRepository<GradeBook, Long> {
    List<GradeBook> findByStudent(User student);
    List<GradeBook> findByCourseNameAndAssessmentName(String courseName, String assessmentName);
}
