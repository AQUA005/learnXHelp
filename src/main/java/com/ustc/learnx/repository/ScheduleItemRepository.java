package com.ustc.learnx.repository;

import com.ustc.learnx.entity.ScheduleItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ustc.learnx.entity.StudentClass;
import java.util.List;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {
    @Override
    @EntityGraph(attributePaths = {"studentClass", "university"})
    List<ScheduleItem> findAll();

    @EntityGraph(attributePaths = {"studentClass", "university"})
    List<ScheduleItem> findByDayOfWeekOrderByStartTimeAsc(String dayOfWeek);
    @EntityGraph(attributePaths = {"studentClass", "university"})
    List<ScheduleItem> findByStudentClass(StudentClass studentClass);
    @EntityGraph(attributePaths = {"studentClass", "university"})
    List<ScheduleItem> findByStudentClassAndDayOfWeekOrderByStartTimeAsc(StudentClass studentClass, String dayOfWeek);
    @EntityGraph(attributePaths = {"studentClass", "university"})
    List<ScheduleItem> findByUniversity(com.ustc.learnx.entity.University university);
}
