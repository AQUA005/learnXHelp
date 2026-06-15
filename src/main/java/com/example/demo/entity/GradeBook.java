package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "gradebooks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false)
    private String courseName;

    @Column(nullable = false)
    private String assessmentName; // e.g. "CT 1", "Midterm"

    @Column(nullable = false)
    private double marksObtained;

    @Column(nullable = false)
    private double maxMarks;
}
