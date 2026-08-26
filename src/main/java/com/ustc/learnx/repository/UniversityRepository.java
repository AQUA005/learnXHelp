package com.ustc.learnx.repository;

import com.ustc.learnx.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UniversityRepository extends JpaRepository<University, Long> {

    Optional<University> findByDomain(String domain);

    Optional<University> findByName(String name);

    Optional<University> findBySlug(String slug);

    /**
     * The lookup every public endpoint uses.
     *
     * <p>An unpublished university must be indistinguishable from one that does
     * not exist, or the platform owner's publish toggle leaks the name of every
     * draft tenant. Filtering in the query rather than after it is what makes
     * that hard to get wrong.
     */
    Optional<University> findBySlugAndPublishedTrue(String slug);

    List<University> findByPublishedTrueOrderByNameAsc();

    boolean existsByName(String name);

    boolean existsByDomain(String domain);

    boolean existsBySlug(String slug);
}
