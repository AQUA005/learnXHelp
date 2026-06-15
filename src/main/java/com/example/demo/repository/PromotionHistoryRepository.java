package com.example.demo.repository;

import com.example.demo.entity.PromotionHistory;
import com.example.demo.entity.StudentClass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PromotionHistoryRepository extends JpaRepository<PromotionHistory, Long> {
    List<PromotionHistory> findByStudentClassOrderByTimestampDesc(StudentClass studentClass);
}
