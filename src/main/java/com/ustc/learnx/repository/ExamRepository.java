package com.ustc.learnx.repository;

import com.ustc.learnx.entity.Exam;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ustc.learnx.entity.StudentClass;
import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    @Override
    @EntityGraph(attributePaths = {"teacher", "studentClass", "university"})
    List<Exam> findAll();

    @EntityGraph(attributePaths = {"teacher", "studentClass", "university"})
    List<Exam> findByPublished(boolean published);
    @EntityGraph(attributePaths = {"teacher", "studentClass", "university"})
    List<Exam> findByStudentClassAndPublished(StudentClass studentClass, boolean published);
    @EntityGraph(attributePaths = {"teacher", "studentClass", "university"})
    List<Exam> findByTeacher(com.ustc.learnx.entity.User teacher);
}
