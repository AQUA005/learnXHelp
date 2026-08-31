package com.ustc.learnx.repository;

import com.ustc.learnx.entity.User;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.StudentClass;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Whether anybody holds a role. Used to detect a deployment with no owner. */
    boolean existsByRole(User.Role role);
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

    /** How many accounts one university holds, without loading any of them. */
    long countByUniversity_Id(Long universityId);

    /**
     * Account totals for every university at once.
     *
     * <p>One grouped query rather than a count per row: the platform console
     * lists every university with its size, and asking per university turned a
     * single screen into one query per tenant.
     */
    @Query("""
            select u.university.id, count(u)
            from User u
            where u.university is not null
            group by u.university.id
            """)
    java.util.List<Object[]> countGroupedByUniversity();

    /**
     * The addresses a broadcast goes to.
     *
     * <p>Both filters are optional and applied in the query rather than in Java:
     * a null university means every campus, and a null role means everyone at
     * the ones selected. Only accounts that can actually be reached are
     * returned, so the count shown before sending is the count that is sent to.
     */
    @Query("""
            select u.email
            from User u
            where (:universityId is null or u.university.id = :universityId)
              and (:role is null or u.role = :role)
              and u.email is not null
              and u.email <> ''
            """)
    java.util.List<String> findAudienceEmails(@Param("universityId") Long universityId,
                                              @Param("role") User.Role role);
}
