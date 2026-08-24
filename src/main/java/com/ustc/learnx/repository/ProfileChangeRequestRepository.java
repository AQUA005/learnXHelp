package com.ustc.learnx.repository;

import com.ustc.learnx.entity.ProfileChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProfileChangeRequestRepository extends JpaRepository<ProfileChangeRequest, Long> {
    List<ProfileChangeRequest> findByApprovedFalseAndRejectedFalse();
    void deleteByUser(com.ustc.learnx.entity.User user);
}
