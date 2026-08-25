package com.ustc.learnx.repository;

import com.ustc.learnx.entity.User;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.StudentClass;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Override
    @EntityGraph(attributePaths = {"studentClass", "university"})
    java.util.List<User> findAll();

    /**
     * The signed-in user is the hub of most requests, and both associations are
     * read straight after loading, so they are fetched with it rather than
     * lazily on a detached instance.
     */
    @EntityGraph(attributePaths = {"studentClass", "university"})
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    @EntityGraph(attributePaths = {"studentClass", "university"})
    java.util.List<User> findByUniversityAndRole(University university, User.Role role);
    @EntityGraph(attributePaths = {"studentClass", "university"})
    java.util.List<User> findByStudentClass(StudentClass studentClass);

    /** Accounts awaiting administrator approval. */
    @EntityGraph(attributePaths = {"studentClass", "university"})
    java.util.List<User> findByApprovedFalse();

    /** Accounts awaiting approval within a single university. */
    @EntityGraph(attributePaths = {"studentClass", "university"})
    java.util.List<User> findByApprovedFalseAndUniversity_Id(Long universityId);

    @EntityGraph(attributePaths = {"studentClass", "university"})
    java.util.List<User> findByUniversity_Id(Long universityId);
}
