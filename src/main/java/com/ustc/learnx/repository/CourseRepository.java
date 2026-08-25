package com.ustc.learnx.repository;

import com.ustc.learnx.entity.Course;
import com.ustc.learnx.entity.University;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    @Override
    @EntityGraph(attributePaths = {"university"})
    List<Course> findAll();

    @EntityGraph(attributePaths = {"university"})
    List<Course> findByUniversity(University university);
    @EntityGraph(attributePaths = {"university"})
    List<Course> findByUniversityAndDepartment(University university, String department);
    @EntityGraph(attributePaths = {"university"})
    List<Course> findByUniversityAndDepartmentAndSemester(University university, String department, String semester);
    @EntityGraph(attributePaths = {"university"})
    java.util.Optional<Course> findByCodeAndUniversity(String code, University university);
}
