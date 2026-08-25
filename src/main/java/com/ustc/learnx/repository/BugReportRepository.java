package com.ustc.learnx.repository;

import com.ustc.learnx.entity.BugReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BugReportRepository extends JpaRepository<BugReport, Long> {
    List<BugReport> findAllByOrderByCreatedAtDesc();
}
