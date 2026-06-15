package com.example.demo.repository;

import com.example.demo.entity.ClassCourseAssignment;
import com.example.demo.entity.StudentClass;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClassCourseAssignmentRepository extends JpaRepository<ClassCourseAssignment, Long> {
    List<ClassCourseAssignment> findByStudentClass(StudentClass studentClass);
    List<ClassCourseAssignment> findByTeacher(User teacher);
    void deleteByStudentClass(StudentClass studentClass);
}
