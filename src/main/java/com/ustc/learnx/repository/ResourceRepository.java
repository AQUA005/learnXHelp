package com.ustc.learnx.repository;

import com.ustc.learnx.entity.Resource;
import com.ustc.learnx.entity.StudentClass;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Associations are lazy by default, so the listing queries name what they need
 * with {@link EntityGraph}. That loads the uploader and class alongside the
 * resources in a single query rather than one per row during serialization.
 */
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    @Override
    @EntityGraph(attributePaths = {"uploadedBy", "studentClass", "university"})
    List<Resource> findAll();


    @EntityGraph(attributePaths = {"uploadedBy", "studentClass", "university"})
    List<Resource> findByApproved(boolean approved);

    @EntityGraph(attributePaths = {"uploadedBy", "studentClass", "university"})
    List<Resource> findByCourseNameAndApproved(String courseName, boolean approved);

    @EntityGraph(attributePaths = {"uploadedBy", "studentClass", "university"})
    List<Resource> findByStudentClassAndApproved(StudentClass studentClass, boolean approved);

    @EntityGraph(attributePaths = {"uploadedBy", "studentClass", "university"})
    List<Resource> findByUploadedBy(com.ustc.learnx.entity.User uploadedBy);

    /** Used by endpoints that return a single resource to the client. */
    @EntityGraph(attributePaths = {"uploadedBy", "studentClass", "university"})
    Optional<Resource> findWithDetailsById(Long id);
}
