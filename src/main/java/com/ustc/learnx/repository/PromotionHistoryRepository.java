package com.ustc.learnx.repository;

import com.ustc.learnx.entity.PromotionHistory;
import com.ustc.learnx.entity.StudentClass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PromotionHistoryRepository extends JpaRepository<PromotionHistory, Long> {
    List<PromotionHistory> findByStudentClassOrderByTimestampDesc(StudentClass studentClass);
}
