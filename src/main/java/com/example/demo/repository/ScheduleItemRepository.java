package com.example.demo.repository;

import com.example.demo.entity.ScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.entity.StudentClass;
import java.util.List;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {
    List<ScheduleItem> findByDayOfWeekOrderByStartTimeAsc(String dayOfWeek);
    List<ScheduleItem> findByStudentClass(StudentClass studentClass);
    List<ScheduleItem> findByStudentClassAndDayOfWeekOrderByStartTimeAsc(StudentClass studentClass, String dayOfWeek);
    List<ScheduleItem> findByUniversity(com.example.demo.entity.University university);
}
