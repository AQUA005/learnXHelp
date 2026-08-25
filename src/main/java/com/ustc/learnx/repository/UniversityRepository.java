package com.ustc.learnx.repository;

import com.ustc.learnx.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UniversityRepository extends JpaRepository<University, Long> {
    Optional<University> findByDomain(String domain);
    Optional<University> findByName(String name);
    boolean existsByName(String name);
    boolean existsByDomain(String domain);
}
