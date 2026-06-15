package com.example.demo.repository;

import com.example.demo.entity.ResourceReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceReactionRepository extends JpaRepository<ResourceReaction, Long> {
    List<ResourceReaction> findByResourceId(Long resourceId);
    Optional<ResourceReaction> findByResourceIdAndUsername(Long resourceId, String username);
}