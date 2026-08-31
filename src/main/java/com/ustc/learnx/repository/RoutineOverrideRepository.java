package com.ustc.learnx.repository;

import com.ustc.learnx.entity.RoutineOverride;
import com.ustc.learnx.entity.StudentClass;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RoutineOverrideRepository extends JpaRepository<RoutineOverride, Long> {

    @EntityGraph(attributePaths = {"studentClass", "university"})
    List<RoutineOverride> findByStudentClassAndOnDateBetweenOrderByOnDateAscStartMinuteAsc(
            StudentClass studentClass, LocalDate from, LocalDate to);

    void deleteByStudentClassAndOnDateBefore(StudentClass studentClass, LocalDate before);
}
