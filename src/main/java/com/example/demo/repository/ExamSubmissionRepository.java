package com.example.demo.repository;

import com.example.demo.entity.ExamSubmission;
import com.example.demo.entity.Exam;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Long> {
    List<ExamSubmission> findByExam(Exam exam);
    List<ExamSubmission> findByStudent(User student);
    Optional<ExamSubmission> findByExamAndStudent(Exam exam, User student);
}
