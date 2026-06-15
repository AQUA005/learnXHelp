package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "resource_reactions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"resource_id", "username"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String reactionType;
}
