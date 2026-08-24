package com.ustc.learnx.repository;

import com.ustc.learnx.entity.ExamSubmission;
import com.ustc.learnx.entity.Exam;
import com.ustc.learnx.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Long> {
    List<ExamSubmission> findByExam(Exam exam);
    List<ExamSubmission> findByStudent(User student);
    Optional<ExamSubmission> findByExamAndStudent(Exam exam, User student);
    void deleteByExam(Exam exam);
}
