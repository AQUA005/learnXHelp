package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    private String fullName;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    
    @Builder.Default
    @Column(nullable = false)
    private boolean approved = true;
    
    private String idNo;
    private String department;
    private String batch;
    private String semester;
    private String section;
    private String designation;
    @Column(columnDefinition = "TEXT")
    private String profilePicUrl;

    @ManyToOne
    @JoinColumn(name = "student_class_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties("students")
    private StudentClass studentClass;

    @ManyToOne
    @JoinColumn(name = "university_id")
    private University university;

    public enum Role {
        STUDENT,
        CR,
        TEACHER,
        ADMIN
    }
}
