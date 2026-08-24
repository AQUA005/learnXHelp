package com.ustc.learnx.repository;

import com.ustc.learnx.entity.SystemMetadata;
import com.ustc.learnx.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SystemMetadataRepository extends JpaRepository<SystemMetadata, Long> {
    List<SystemMetadata> findByType(String type);
    List<SystemMetadata> findByTypeAndUniversity(String type, University university);
}
