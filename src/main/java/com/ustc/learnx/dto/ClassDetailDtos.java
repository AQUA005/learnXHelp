package com.ustc.learnx.dto;

import java.util.List;

/**
 * Everything the class-detail screen shows, in one response.
 *
 * <p>The screen previously had to stitch a class together from the flat list,
 * the assignments endpoint and the student list, which meant three requests and
 * three chances to show a half-loaded class.
 */
public final class ClassDetailDtos {

    private ClassDetailDtos() {
    }

    public record ClassDetail(
            Long id,
            String className,
            String department,
            String batch,
            String section,
            String semester,
            int studentCount,
            /** Null when no class representative is assigned. */
            ClassMember cr,
            List<ClassMember> students,
            List<CourseAssignment> courses,
            List<RoutineEntry> routine,
            List<PromotionEntry> promotions) {
    }

    public record ClassMember(
            Long id,
            String username,
            String fullName,
            String email,
            String idNo,
            String role,
            boolean approved,
            String profilePicUrl) {
    }

    public record CourseAssignment(
            Long id,
            Long courseId,
            String courseCode,
            String courseName,
            Double credits,
            Long teacherId,
            String teacherName,
            String teacherDesignation) {
    }

    public record RoutineEntry(
            Long id,
            String dayOfWeek,
            String startTime,
            String endTime,
            String courseName,
            String teacherName,
            String roomNo) {
    }

    public record PromotionEntry(
            Long id,
            String fromSemester,
            String toSemester,
            String timestamp) {
    }
}
