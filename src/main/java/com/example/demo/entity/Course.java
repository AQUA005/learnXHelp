package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "courses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code; // e.g., CSE 3101

    @Column(nullable = false)
    private String name; // e.g., Database Systems

    @Column(nullable = false)
    private Double credits;

    @Column(nullable = false)
    private String semester; // e.g., 3rd Year 1st Semester

    @Column(nullable = false)
    private String department; // e.g., CSE - Computer Science & Engineering

    @ManyToOne(optional = false)
    @JoinColumn(name = "university_id")
    private University university;
}
