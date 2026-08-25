package com.ustc.learnx.repository;

import com.ustc.learnx.entity.SystemMetadata;
import com.ustc.learnx.entity.University;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SystemMetadataRepository extends JpaRepository<SystemMetadata, Long> {
    @Override
    @EntityGraph(attributePaths = {"university"})
    List<SystemMetadata> findAll();

    @EntityGraph(attributePaths = {"university"})
    List<SystemMetadata> findByType(String type);
    @EntityGraph(attributePaths = {"university"})
    List<SystemMetadata> findByTypeAndUniversity(String type, University university);
}
