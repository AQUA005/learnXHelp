package com.ustc.learnx.repository;

import com.ustc.learnx.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ustc.learnx.entity.StudentClass;
import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
    List<Resource> findByApproved(boolean approved);
    List<Resource> findByCourseNameAndApproved(String courseName, boolean approved);
    List<Resource> findByStudentClassAndApproved(StudentClass studentClass, boolean approved);
    List<Resource> findByUploadedBy(com.ustc.learnx.entity.User uploadedBy);
}
