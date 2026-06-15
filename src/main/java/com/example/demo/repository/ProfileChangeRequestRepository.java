package com.example.demo.repository;

import com.example.demo.entity.ProfileChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProfileChangeRequestRepository extends JpaRepository<ProfileChangeRequest, Long> {
    List<ProfileChangeRequest> findByApprovedFalseAndRejectedFalse();
}
