package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "class_tests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String courseName;

    @Column(nullable = false)
    private LocalDateTime dateTime;

    private int durationMinutes;
    private String roomNo;
    private String topic;
    private String createdBy; // username of CR or Teacher

    @ManyToOne
    @JoinColumn(name = "student_class_id")
    private StudentClass studentClass;

    @ManyToOne
    @JoinColumn(name = "university_id")
    private University university;
}
