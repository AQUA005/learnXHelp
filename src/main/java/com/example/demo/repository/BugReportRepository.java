package com.example.demo.repository;

import com.example.demo.entity.BugReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BugReportRepository extends JpaRepository<BugReport, Long> {
    List<BugReport> findAllByOrderByCreatedAtDesc();
}
