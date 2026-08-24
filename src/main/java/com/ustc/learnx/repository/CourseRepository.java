package com.ustc.learnx.repository;

import com.ustc.learnx.entity.Course;
import com.ustc.learnx.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByUniversity(University university);
    List<Course> findByUniversityAndDepartment(University university, String department);
    List<Course> findByUniversityAndDepartmentAndSemester(University university, String department, String semester);
    java.util.Optional<Course> findByCodeAndUniversity(String code, University university);
}
