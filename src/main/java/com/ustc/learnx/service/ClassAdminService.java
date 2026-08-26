package com.ustc.learnx.service;

import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.dto.ClassDetailDtos.ClassDetail;
import com.ustc.learnx.dto.ClassDetailDtos.ClassMember;
import com.ustc.learnx.dto.ClassDetailDtos.CourseAssignment;
import com.ustc.learnx.dto.ClassDetailDtos.PromotionEntry;
import com.ustc.learnx.dto.ClassDetailDtos.RoutineEntry;
import com.ustc.learnx.entity.StudentClass;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.ClassCourseAssignmentRepository;
import com.ustc.learnx.repository.PromotionHistoryRepository;
import com.ustc.learnx.repository.ScheduleItemRepository;
import com.ustc.learnx.repository.StudentClassRepository;
import com.ustc.learnx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One class group, gathered for the screen that administers it.
 *
 * <p>Four queries regardless of how many students are in the class, then sorted
 * in Java — the roster, the course assignments, the routine and the promotion
 * history.
 */
@Service
@RequiredArgsConstructor
public class ClassAdminService {

    private final StudentClassRepository studentClassRepository;
    private final UserRepository userRepository;
    private final ClassCourseAssignmentRepository classCourseAssignmentRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final PromotionHistoryRepository promotionHistoryRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public ClassDetail detail(Long classId) {
        StudentClass sc = studentClassRepository.findById(classId)
                .orElseThrow(() -> new NotFoundException("No class group with id " + classId));
        currentUserService.assertSameUniversity(sc.getUniversity());

        List<User> members = userRepository.findByStudentClass(sc);

        // The class carries no semester of its own; it is read off its members.
        // Fragile, but it is the existing behaviour and changing it here would
        // silently move students between semesters.
        String semester = members.stream()
                .map(User::getSemester)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse("1st Year 1st Semester");

        User cr = members.stream()
                .filter(member -> member.getRole() == User.Role.CR)
                .findFirst()
                .orElse(null);

        return new ClassDetail(
                sc.getId(),
                sc.getClassName(),
                sc.getDepartment(),
                sc.getBatch(),
                sc.getSection(),
                semester,
                members.size(),
                cr == null ? null : toMember(cr),
                members.stream()
                        .sorted(Comparator.comparing(User::getFullName, Comparator.nullsLast(String::compareTo)))
                        .map(ClassAdminService::toMember)
                        .toList(),
                classCourseAssignmentRepository.findByStudentClass(sc).stream()
                        .map(cca -> new CourseAssignment(
                                cca.getId(),
                                cca.getCourse().getId(),
                                cca.getCourse().getCode(),
                                cca.getCourse().getName(),
                                cca.getCourse().getCredits(),
                                cca.getTeacher().getId(),
                                cca.getTeacher().getFullName(),
                                cca.getTeacher().getDesignation()))
                        .sorted(Comparator.comparing(CourseAssignment::courseCode,
                                Comparator.nullsLast(String::compareTo)))
                        .toList(),
                scheduleItemRepository.findByStudentClass(sc).stream()
                        .map(item -> new RoutineEntry(
                                item.getId(),
                                item.getDayOfWeek(),
                                String.valueOf(item.getStartTime()),
                                String.valueOf(item.getEndTime()),
                                item.getCourseName(),
                                item.getTeacherName(),
                                item.getRoomNo()))
                        .sorted(Comparator.comparingInt((RoutineEntry e) -> dayOrder(e.dayOfWeek()))
                                .thenComparing(RoutineEntry::startTime,
                                        Comparator.nullsLast(String::compareTo)))
                        .toList(),
                promotionHistoryRepository.findByStudentClassOrderByTimestampDesc(sc).stream()
                        .map(history -> new PromotionEntry(
                                history.getId(),
                                history.getFromSemester(),
                                history.getToSemester(),
                                String.valueOf(history.getTimestamp())))
                        .toList());
    }

    private static ClassMember toMember(User user) {
        return new ClassMember(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getIdNo(),
                user.getRole().name(),
                user.isApproved(),
                user.getProfilePicUrl());
    }

    /** Monday first, and anything unrecognised last rather than dropped. */
    private static int dayOrder(String dayOfWeek) {
        if (dayOfWeek == null) {
            return Integer.MAX_VALUE;
        }
        try {
            return DayOfWeek.valueOf(dayOfWeek.toUpperCase(Locale.ROOT)).getValue();
        } catch (IllegalArgumentException e) {
            return Integer.MAX_VALUE;
        }
    }
}
