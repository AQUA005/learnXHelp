package com.ustc.learnx.repository;

import com.ustc.learnx.entity.ResourceReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceReactionRepository extends JpaRepository<ResourceReaction, Long> {

    List<ResourceReaction> findByResourceId(Long resourceId);

    Optional<ResourceReaction> findByResourceIdAndUsername(Long resourceId, String username);

    void deleteByResourceId(Long resourceId);

    /**
     * Reaction totals for a set of resources, as {@code [resourceId, type, count]}.
     *
     * <p>One query for the whole listing. Counting these per resource meant a
     * query per row on every visit to the library.
     */
    @Query("""
            SELECT r.resourceId, r.reactionType, COUNT(r)
            FROM ResourceReaction r
            WHERE r.resourceId IN :resourceIds
            GROUP BY r.resourceId, r.reactionType
            """)
    List<Object[]> countByResourceIds(@Param("resourceIds") Collection<Long> resourceIds);

    /** The current user's own reactions across a listing, fetched in one query. */
    List<ResourceReaction> findByResourceIdInAndUsername(Collection<Long> resourceIds, String username);
}
