package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "profile_change_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String newFullName;
    private String newEmail;
    private String newIdNo;
    private String newDepartment;
    private String newBatch;
    private String newSemester;
    private String newSection;
    private String newDesignation;

    @Builder.Default
    private boolean approved = false;

    @Builder.Default
    private boolean rejected = false;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
