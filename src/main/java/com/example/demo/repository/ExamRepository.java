package com.example.demo.repository;

import com.example.demo.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.StudentClass;
import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByPublished(boolean published);
    List<Exam> findByStudentClassAndPublished(StudentClass studentClass, boolean published);
}
