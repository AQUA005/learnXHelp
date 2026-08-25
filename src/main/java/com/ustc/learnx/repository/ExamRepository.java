package com.ustc.learnx.repository;

import com.ustc.learnx.entity.Exam;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ustc.learnx.entity.StudentClass;
import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    /**
     * Exams a class can sit: those set for that class, plus university-wide ones
     * that name no class.
     */
    @EntityGraph(attributePaths = {"teacher", "studentClass", "university"})
    @org.springframework.data.jpa.repository.Query("""
            SELECT e FROM Exam e
            WHERE e.published = true
              AND e.university = :university
              AND (e.studentClass = :studentClass OR e.studentClass IS NULL)
            """)
    List<Exam> findVisibleToClass(
            @org.springframework.data.repository.query.Param("university") com.ustc.learnx.entity.University university,
            @org.springframework.data.repository.query.Param("studentClass") StudentClass studentClass);

    @EntityGraph(attributePaths = {"teacher", "studentClass", "university"})
    List<Exam> findByUniversity(com.ustc.learnx.entity.University university);

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
