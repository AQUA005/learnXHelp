package com.ustc.learnx.repository;

import com.ustc.learnx.entity.RoutineSource;
import com.ustc.learnx.entity.University;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoutineSourceRepository extends JpaRepository<RoutineSource, Long> {

    @EntityGraph(attributePaths = "university")
    List<RoutineSource> findByUniversityOrderByDepartmentAsc(University university);

    @EntityGraph(attributePaths = "university")
    Optional<RoutineSource> findByUniversityAndDepartment(University university, String department);
}
