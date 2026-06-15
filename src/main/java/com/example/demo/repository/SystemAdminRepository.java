package com.example.demo.repository;

import com.example.demo.entity.SystemAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SystemAdminRepository extends JpaRepository<SystemAdmin, Long> {
    Optional<SystemAdmin> findByUsername(String username);
    boolean existsByUsername(String username);
}
