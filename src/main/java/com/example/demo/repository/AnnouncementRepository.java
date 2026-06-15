package com.example.demo.repository;

import com.example.demo.entity.Announcement;
import com.example.demo.entity.StudentClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByStudentClassOrStudentClassIsNullOrderByCreatedAtDesc(StudentClass studentClass);
    List<Announcement> findAllByOrderByCreatedAtDesc();
}