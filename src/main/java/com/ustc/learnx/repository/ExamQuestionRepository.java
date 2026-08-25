package com.ustc.learnx.repository;

import com.ustc.learnx.entity.ExamQuestion;
import com.ustc.learnx.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {
    List<ExamQuestion> findByExam(Exam exam);
    void deleteByExam(Exam exam);
}
