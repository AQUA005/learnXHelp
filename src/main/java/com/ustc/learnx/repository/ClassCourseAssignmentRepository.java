package com.ustc.learnx.repository;

import com.ustc.learnx.entity.ClassCourseAssignment;
import com.ustc.learnx.entity.StudentClass;
import com.ustc.learnx.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClassCourseAssignmentRepository extends JpaRepository<ClassCourseAssignment, Long> {
    List<ClassCourseAssignment> findByStudentClass(StudentClass studentClass);
    List<ClassCourseAssignment> findByTeacher(User teacher);
    void deleteByStudentClass(StudentClass studentClass);
    void deleteByTeacher(User teacher);
}
