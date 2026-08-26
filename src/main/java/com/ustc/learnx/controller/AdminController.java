package com.ustc.learnx.controller;

import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.UserRepository;
import com.ustc.learnx.service.CurrentUserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
    private final com.ustc.learnx.service.ClassPlacementService classPlacementService;

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

    /**
     * Approves an account and places it in its class.
     *
     * <p>The placement is the part that was missing. Approving used to set the
     * flag and nothing else, so a student approved from the administration
     * screen belonged to no class — and the routine, notes, announcements and
     * class tests are all scoped to one, so every such screen stayed empty for
     * them with nothing to explain it.
     */
    @PostMapping("/approve/{id}")
    @Transactional
    public ResponseEntity<?> approveUser(@PathVariable Long id) {
        User user = requireUserInOwnUniversity(id);
        user.setApproved(true);
        classPlacementService.place(user);
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
