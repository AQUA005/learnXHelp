package com.example.demo.repository;

import com.example.demo.entity.User;
import com.example.demo.entity.University;
import com.example.demo.entity.StudentClass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    java.util.List<User> findByUniversityAndRole(University university, User.Role role);
    java.util.List<User> findByStudentClass(StudentClass studentClass);
}
