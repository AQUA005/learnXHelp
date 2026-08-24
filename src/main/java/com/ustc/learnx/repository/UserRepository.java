package com.ustc.learnx.repository;

import com.ustc.learnx.entity.User;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.StudentClass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    java.util.List<User> findByUniversityAndRole(University university, User.Role role);
    java.util.List<User> findByStudentClass(StudentClass studentClass);

    /** Accounts awaiting administrator approval. */
    java.util.List<User> findByApprovedFalse();

    /** Accounts awaiting approval within a single university. */
    java.util.List<User> findByApprovedFalseAndUniversity_Id(Long universityId);

    java.util.List<User> findByUniversity_Id(Long universityId);
}
