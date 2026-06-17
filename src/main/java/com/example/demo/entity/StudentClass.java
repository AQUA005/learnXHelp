package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "student_classes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "students")
public class StudentClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String batch;
    private String department;
    private String section;

    @ManyToOne
    @JoinColumn(name = "university_id")
    private University university;

    @OneToMany(mappedBy = "studentClass")
    @JsonIgnore
    private List<User> students;

    @com.fasterxml.jackson.annotation.JsonProperty("className")
    public String getClassName() {
        String deptCode = (department != null && department.contains(" - ")) ? department.split(" - ")[0] : (department != null ? department : "");
        return deptCode + " " + (batch != null ? batch : "") + " " + (section != null ? section : "");
    }
}
