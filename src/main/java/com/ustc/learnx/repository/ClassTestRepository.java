package com.ustc.learnx.repository;

import com.ustc.learnx.entity.ClassTest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ustc.learnx.entity.StudentClass;
import java.time.LocalDateTime;
import java.util.List;

public interface ClassTestRepository extends JpaRepository<ClassTest, Long> {
    @Override
    @EntityGraph(attributePaths = {"studentClass", "university"})
    List<ClassTest> findAll();

    @EntityGraph(attributePaths = {"studentClass", "university"})
    List<ClassTest> findByDateTimeAfterOrderByDateTimeAsc(LocalDateTime dateTime);
    @EntityGraph(attributePaths = {"studentClass", "university"})
    List<ClassTest> findAllByOrderByDateTimeAsc();
    @EntityGraph(attributePaths = {"studentClass", "university"})
    List<ClassTest> findByStudentClassOrderByDateTimeAsc(StudentClass studentClass);
}
