package com.example.demo.repository;

import com.example.demo.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.StudentClass;
import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
    List<Resource> findByApproved(boolean approved);
    List<Resource> findByCourseNameAndApproved(String courseName, boolean approved);
    List<Resource> findByStudentClassAndApproved(StudentClass studentClass, boolean approved);
}
