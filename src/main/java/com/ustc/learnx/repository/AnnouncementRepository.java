package com.ustc.learnx.repository;

import com.ustc.learnx.entity.Announcement;
import com.ustc.learnx.entity.StudentClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByStudentClassOrStudentClassIsNullOrderByCreatedAtDesc(StudentClass studentClass);
    List<Announcement> findAllByOrderByCreatedAtDesc();
}