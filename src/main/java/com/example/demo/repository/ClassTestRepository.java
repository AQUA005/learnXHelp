package com.example.demo.repository;

import com.example.demo.entity.ClassTest;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.StudentClass;
import java.time.LocalDateTime;
import java.util.List;

public interface ClassTestRepository extends JpaRepository<ClassTest, Long> {
    List<ClassTest> findByDateTimeAfterOrderByDateTimeAsc(LocalDateTime dateTime);
    List<ClassTest> findAllByOrderByDateTimeAsc();
    List<ClassTest> findByStudentClassOrderByDateTimeAsc(StudentClass studentClass);
}
