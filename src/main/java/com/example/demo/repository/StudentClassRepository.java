package com.example.demo.repository;

import com.example.demo.entity.StudentClass;
import com.example.demo.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface StudentClassRepository extends JpaRepository<StudentClass, Long> {
    Optional<StudentClass> findByBatchAndDepartmentAndSection(String batch, String department, String section);
    List<StudentClass> findByUniversity(University university);
}
