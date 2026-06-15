package com.example.demo.repository;

import com.example.demo.entity.ExamQuestion;
import com.example.demo.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {
    List<ExamQuestion> findByExam(Exam exam);
}
