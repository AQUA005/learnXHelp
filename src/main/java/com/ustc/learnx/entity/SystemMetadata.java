package com.ustc.learnx.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemMetadata {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String type; // "SEMESTER", "DEPARTMENT", "SESSION", "DESIGNATION"

    @Column(name = "meta_value", nullable = false)
    private String value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id")
    private University university;
}
