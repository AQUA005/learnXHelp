package com.ustc.learnx.controller;

import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.UserRepository;
import com.ustc.learnx.service.CurrentUserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Account approval queue. Administrators only — these endpoints create, approve
 * and delete user accounts.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@AllArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    /** Accounts awaiting approval, scoped to the caller's university. */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingUsers() {
        Long universityId = currentUserService.requireUniversityId();

        List<Map<String, Object>> pending = userRepository
                .findByApprovedFalseAndUniversity_Id(universityId).stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "fullName", u.getFullName(),
                        "email", u.getEmail(),
                        "role", u.getRole().name()))
                .toList();

        return ResponseEntity.ok(pending);
    }

    @PostMapping("/approve/{id}")
    public ResponseEntity<?> approveUser(@PathVariable Long id) {
        User user = requireUserInOwnUniversity(id);
        user.setApproved(true);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "User account approved successfully"));
    }

    @DeleteMapping("/reject/{id}")
    public ResponseEntity<?> rejectUser(@PathVariable Long id) {
        User user = requireUserInOwnUniversity(id);
        userRepository.delete(user);
        return ResponseEntity.ok(Map.of("message", "User account request rejected and deleted"));
    }

    /**
     * Loads a user and refuses if they belong to a different university, so an
     * administrator of one tenant cannot act on another's accounts.
     */
    private User requireUserInOwnUniversity(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        currentUserService.assertSameUniversity(user.getUniversity());
        return user;
    }
}
