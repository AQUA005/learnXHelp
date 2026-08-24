package com.ustc.learnx.repository;

import com.ustc.learnx.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ustc.learnx.entity.StudentClass;
import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByPublished(boolean published);
    List<Exam> findByStudentClassAndPublished(StudentClass studentClass, boolean published);
    List<Exam> findByTeacher(com.ustc.learnx.entity.User teacher);
}
