package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.entity.User.Role;
import com.example.demo.entity.StudentClass;
import com.example.demo.entity.ProfileChangeRequest;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.StudentClassRepository;
import com.example.demo.repository.ProfileChangeRequestRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/approvals")
@AllArgsConstructor
public class UserApprovalController {

    private final UserRepository userRepository;
    private final StudentClassRepository studentClassRepository;
    private final ProfileChangeRequestRepository profileChangeRequestRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingUsers(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = userRepository.findByUsername(principal.getName()).orElse(null);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }

        Role currentRole = currentUser.getRole();
        List<User> allUsers = userRepository.findAll();
        List<Map<String, Object>> pending = new ArrayList<>();

        for (User u : allUsers) {
            if (!u.isApproved()) {
                boolean canManage = false;
                if (currentRole == Role.ADMIN) {
                    canManage = true;
                } else if (currentRole == Role.TEACHER) {
                    if (u.getRole() == Role.CR || u.getRole() == Role.STUDENT) {
                        canManage = true;
                    }
                } else if (currentRole == Role.CR) {
                    if (u.getRole() == Role.STUDENT) {
                        canManage = true;
                    }
                }

                if (canManage) {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", u.getId());
                    map.put("username", u.getUsername());
                    map.put("fullName", u.getFullName());
                    map.put("email", u.getEmail());
                    map.put("role", u.getRole().name());
                    map.put("idNo", u.getIdNo() != null ? u.getIdNo() : "");
                    map.put("department", u.getDepartment() != null ? u.getDepartment() : "");
                    map.put("batch", u.getBatch() != null ? u.getBatch() : "");
                    map.put("semester", u.getSemester() != null ? u.getSemester() : "");
                    map.put("section", u.getSection() != null ? u.getSection() : "");
                    map.put("designation", u.getDesignation() != null ? u.getDesignation() : "");
                    pending.add(map);
                }
            }
        }

        return ResponseEntity.ok(pending);
    }

    @PostMapping("/approve/{id}")
    public ResponseEntity<?> approveUser(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = userRepository.findByUsername(principal.getName()).orElse(null);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }

        User targetUser = userRepository.findById(id).orElse(null);
        if (targetUser == null) {
            return ResponseEntity.notFound().build();
        }

        Role currentRole = currentUser.getRole();
        Role targetRole = targetUser.getRole();
        boolean canApprove = false;

        if (currentRole == Role.ADMIN) {
            canApprove = true;
        } else if (currentRole == Role.TEACHER) {
            if (targetRole == Role.CR || targetRole == Role.STUDENT) {
                canApprove = true;
            }
        } else if (currentRole == Role.CR) {
            if (targetRole == Role.STUDENT) {
                canApprove = true;
            }
        }

        if (!canApprove) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You do not have permission to approve a user with role: " + targetRole));
        }

        // Auto-group Student/CR into a StudentClass
        if (targetRole == Role.STUDENT || targetRole == Role.CR) {
            String batch = targetUser.getBatch();
            String dept = targetUser.getDepartment();
            String sec = targetUser.getSection();
            if (batch != null && dept != null && sec != null && !batch.isEmpty() && !dept.isEmpty() && !sec.isEmpty()) {
                StudentClass studentClass = studentClassRepository.findByBatchAndDepartmentAndSection(batch, dept, sec)
                        .orElseGet(() -> {
                            StudentClass sc = StudentClass.builder()
                                    .batch(batch)
                                    .department(dept)
                                    .section(sec)
                                    .build();
                            return studentClassRepository.save(sc);
                        });
                targetUser.setStudentClass(studentClass);
            }
        }

        targetUser.setApproved(true);
        userRepository.save(targetUser);
        return ResponseEntity.ok(Map.of("message", "User account approved successfully"));
    }

    @DeleteMapping("/reject/{id}")
    public ResponseEntity<?> rejectUser(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = userRepository.findByUsername(principal.getName()).orElse(null);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }

        User targetUser = userRepository.findById(id).orElse(null);
        if (targetUser == null) {
            return ResponseEntity.notFound().build();
        }

        Role currentRole = currentUser.getRole();
        Role targetRole = targetUser.getRole();
        boolean canReject = false;

        if (currentRole == Role.ADMIN) {
            canReject = true;
        } else if (currentRole == Role.TEACHER) {
            if (targetRole == Role.CR || targetRole == Role.STUDENT) {
                canReject = true;
            }
        } else if (currentRole == Role.CR) {
            if (targetRole == Role.STUDENT) {
                canReject = true;
            }
        }

        if (!canReject) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You do not have permission to reject a user with role: " + targetRole));
        }

        userRepository.delete(targetUser);
        return ResponseEntity.ok(Map.of("message", "User account request rejected and deleted"));
    }

    // -------------------------------------------------------------
    // ADMIN ONLY: Create New Admin Accounts
    // -------------------------------------------------------------
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminCreateRequest {
        private String username;
        private String password;
        private String fullName;
        private String email;
    }

    @PostMapping("/admin-create")
    public ResponseEntity<?> createNewAdmin(@RequestBody AdminCreateRequest request, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = userRepository.findByUsername(principal.getName()).orElse(null);
        if (currentUser == null || currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can create other administrator accounts."));
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }

        User admin = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .role(Role.ADMIN)
                .approved(true)
                .build();

        userRepository.save(admin);
        return ResponseEntity.ok(Map.of("message", "Administrator account created successfully"));
    }

    // -------------------------------------------------------------
    // ADMIN ONLY: Sensitive Profile Change Requests Approvals
    // -------------------------------------------------------------
    @GetMapping("/profile-requests")
    public ResponseEntity<?> getPendingProfileRequests(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = userRepository.findByUsername(principal.getName()).orElse(null);
        if (currentUser == null || currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can view pending profile changes."));
        }

        List<ProfileChangeRequest> requests = profileChangeRequestRepository.findByApprovedFalseAndRejectedFalse();
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/profile-requests/{id}/approve")
    public ResponseEntity<?> approveProfileRequest(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = userRepository.findByUsername(principal.getName()).orElse(null);
        if (currentUser == null || currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can approve profile changes."));
        }

        ProfileChangeRequest req = profileChangeRequestRepository.findById(id).orElse(null);
        if (req == null) {
            return ResponseEntity.notFound().build();
        }

        User targetUser = req.getUser();
        if (req.getNewFullName() != null) targetUser.setFullName(req.getNewFullName());
        if (req.getNewEmail() != null) targetUser.setEmail(req.getNewEmail());
        if (req.getNewIdNo() != null) targetUser.setIdNo(req.getNewIdNo());
        if (req.getNewDepartment() != null) targetUser.setDepartment(req.getNewDepartment());
        if (req.getNewBatch() != null) targetUser.setBatch(req.getNewBatch());
        if (req.getNewSemester() != null) targetUser.setSemester(req.getNewSemester());
        if (req.getNewSection() != null) targetUser.setSection(req.getNewSection());
        if (req.getNewDesignation() != null) targetUser.setDesignation(req.getNewDesignation());
 
        // Update Class Grouping if batch/dept/section changed
        if (targetUser.getRole() == Role.STUDENT || targetUser.getRole() == Role.CR) {
            String batch = targetUser.getBatch();
            String dept = targetUser.getDepartment();
            String sec = targetUser.getSection();
            if (batch != null && dept != null && sec != null && !batch.isEmpty() && !dept.isEmpty() && !sec.isEmpty()) {
                StudentClass studentClass = studentClassRepository.findByBatchAndDepartmentAndSection(batch, dept, sec)
                        .orElseGet(() -> {
                            StudentClass sc = StudentClass.builder()
                                    .batch(batch)
                                    .department(dept)
                                    .section(sec)
                                    .build();
                            return studentClassRepository.save(sc);
                        });
                targetUser.setStudentClass(studentClass);
            }
        }

        targetUser.setApproved(true);
        userRepository.save(targetUser);

        req.setApproved(true);
        profileChangeRequestRepository.save(req);

        return ResponseEntity.ok(Map.of("message", "Profile change request approved and applied successfully"));
    }

    @PostMapping("/profile-requests/{id}/reject")
    public ResponseEntity<?> rejectProfileRequest(@PathVariable Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User currentUser = userRepository.findByUsername(principal.getName()).orElse(null);
        if (currentUser == null || currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only administrators can reject profile changes."));
        }

        ProfileChangeRequest req = profileChangeRequestRepository.findById(id).orElse(null);
        if (req == null) {
            return ResponseEntity.notFound().build();
        }

        req.setRejected(true);
        profileChangeRequestRepository.save(req);

        return ResponseEntity.ok(Map.of("message", "Profile change request rejected"));
    }
}
