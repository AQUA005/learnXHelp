package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotion_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_class_id")
    private StudentClass studentClass;

    @Column(nullable = false)
    private String fromSemester;

    @Column(nullable = false)
    private String toSemester;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(columnDefinition = "TEXT")
    private String savedAssignmentsJson; // JSON representation of course/teacher mappings
}
