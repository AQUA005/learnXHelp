package com.ustc.learnx.service;

import com.ustc.learnx.common.AccessDeniedException;
import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.dto.ScheduleDtos.AuditLogResponse;
import com.ustc.learnx.dto.ScheduleDtos.ClassRef;
import com.ustc.learnx.dto.ScheduleDtos.ClassTestRequest;
import com.ustc.learnx.dto.ScheduleDtos.ClassTestResponse;
import com.ustc.learnx.dto.ScheduleDtos.RoutineItemRequest;
import com.ustc.learnx.dto.ScheduleDtos.RoutineItemResponse;
import com.ustc.learnx.entity.AuditLog;
import com.ustc.learnx.entity.ClassTest;
import com.ustc.learnx.entity.ScheduleItem;
import com.ustc.learnx.entity.StudentClass;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.AuditLogRepository;
import com.ustc.learnx.repository.ClassTestRepository;
import com.ustc.learnx.repository.ScheduleItemRepository;
import com.ustc.learnx.repository.StudentClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The weekly routine and class tests.
 *
 * <p>Decides which class a change applies to. Previously the request body was
 * bound onto the entity, so the owning class and university arrived from the
 * client; a class representative was corrected afterwards but a teacher or
 * administrator was not, and updating or deleting a class test checked nothing
 * at all. Here the target class is resolved once, up front, and every write
 * goes through it.
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleItemRepository scheduleItemRepository;
    private final ClassTestRepository classTestRepository;
    private final StudentClassRepository studentClassRepository;
    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;

    // --- Routine ---

    @Transactional(readOnly = true)
    public List<RoutineItemResponse> listRoutine(Long classId) {
        User user = currentUserService.requireCurrentUser();
        StudentClass target = resolveReadableClass(user, classId);

        List<ScheduleItem> items = target != null
                ? scheduleItemRepository.findByStudentClass(target)
                : scheduleItemRepository.findByUniversity(user.getUniversity());

        return items.stream().map(ScheduleService::toRoutineResponse).toList();
    }

    @Transactional
    public RoutineItemResponse createRoutineItem(RoutineItemRequest request) {
        User user = currentUserService.requireCurrentUser();
        requireOrderedTimes(request.startTime(), request.endTime());
        StudentClass target = resolveWritableClass(user, request.studentClass());

        ScheduleItem saved = scheduleItemRepository.save(ScheduleItem.builder()
                .courseName(request.courseName())
                .dayOfWeek(request.dayOfWeek())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .teacherName(request.teacherName())
                .roomNo(request.roomNo())
                .studentClass(target)
                .university(user.getUniversity())
                .build());

        record_("ROUTINE", saved.getId(), "CREATE", user, String.format(
                "Added Routine Class: Course '%s', Day '%s', Time %s-%s, Room '%s', Teacher '%s'",
                saved.getCourseName(), saved.getDayOfWeek(), saved.getStartTime(),
                saved.getEndTime(), saved.getRoomNo(), saved.getTeacherName()));

        return toRoutineResponse(saved);
    }

    @Transactional
    public RoutineItemResponse updateRoutineItem(Long id, RoutineItemRequest request) {
        User user = currentUserService.requireCurrentUser();
        requireOrderedTimes(request.startTime(), request.endTime());

        ScheduleItem existing = scheduleItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Routine item not found"));
        requireWritable(user, existing.getStudentClass(), existing.getUniversity());

        String before = String.format("Day '%s', Time %s-%s, Course '%s', Room '%s', Teacher '%s'",
                existing.getDayOfWeek(), existing.getStartTime(), existing.getEndTime(),
                existing.getCourseName(), existing.getRoomNo(), existing.getTeacherName());

        existing.setDayOfWeek(request.dayOfWeek());
        existing.setStartTime(request.startTime());
        existing.setEndTime(request.endTime());
        existing.setCourseName(request.courseName());
        existing.setRoomNo(request.roomNo());
        existing.setTeacherName(request.teacherName());
        // The owning class may only be changed to one the caller may write to.
        if (request.studentClass() != null && request.studentClass().id() != null) {
            existing.setStudentClass(resolveWritableClass(user, request.studentClass()));
        }

        ScheduleItem saved = scheduleItemRepository.save(existing);

        record_("ROUTINE", id, "UPDATE", user, String.format(
                "Updated Routine Class from [%s] to [Day '%s', Time %s-%s, Course '%s', Room '%s', Teacher '%s']",
                before, saved.getDayOfWeek(), saved.getStartTime(), saved.getEndTime(),
                saved.getCourseName(), saved.getRoomNo(), saved.getTeacherName()));

        return toRoutineResponse(saved);
    }

    @Transactional
    public void deleteRoutineItem(Long id) {
        User user = currentUserService.requireCurrentUser();
        ScheduleItem existing = scheduleItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Routine item not found"));
        requireWritable(user, existing.getStudentClass(), existing.getUniversity());

        String details = String.format(
                "Deleted Routine Class: Course '%s', Day '%s', Time %s-%s, Room '%s', Teacher '%s'",
                existing.getCourseName(), existing.getDayOfWeek(), existing.getStartTime(),
                existing.getEndTime(), existing.getRoomNo(), existing.getTeacherName());

        scheduleItemRepository.delete(existing);
        record_("ROUTINE", id, "DELETE", user, details);
    }

    // --- Class tests ---

    @Transactional(readOnly = true)
    public List<ClassTestResponse> listClassTests(Long classId) {
        User user = currentUserService.requireCurrentUser();
        StudentClass target = resolveReadableClass(user, classId);

        List<ClassTest> tests = target != null
                ? classTestRepository.findByStudentClassOrderByDateTimeAsc(target)
                : classTestRepository.findAllByOrderByDateTimeAsc().stream()
                        .filter(ct -> belongsToUniversity(ct.getUniversity(), user))
                        .toList();

        return tests.stream().map(ScheduleService::toClassTestResponse).toList();
    }

    @Transactional
    public ClassTestResponse createClassTest(ClassTestRequest request) {
        User user = currentUserService.requireCurrentUser();
        StudentClass target = resolveWritableClass(user, request.studentClass());

        ClassTest saved = classTestRepository.save(ClassTest.builder()
                .courseName(request.courseName())
                .dateTime(request.dateTime())
                .durationMinutes(request.durationMinutes())
                .roomNo(request.roomNo())
                .topic(request.topic())
                .createdBy(user.getUsername())
                .studentClass(target)
                .university(user.getUniversity())
                .build());

        record_("CLASS_TEST", saved.getId(), "CREATE", user, String.format(
                "Scheduled Class Test (CT): Course '%s', Date/Time '%s', Duration %d mins, Room '%s', Topic '%s'",
                saved.getCourseName(), saved.getDateTime(), saved.getDurationMinutes(),
                saved.getRoomNo(), saved.getTopic()));

        return toClassTestResponse(saved);
    }

    @Transactional
    public ClassTestResponse updateClassTest(Long id, ClassTestRequest request) {
        User user = currentUserService.requireCurrentUser();
        ClassTest existing = classTestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Class test not found"));
        // This check did not exist: any class representative could edit any
        // class test, in any class or university.
        requireWritable(user, existing.getStudentClass(), existing.getUniversity());

        String before = String.format("Course '%s', Date/Time '%s', Room '%s', Topic '%s'",
                existing.getCourseName(), existing.getDateTime(), existing.getRoomNo(), existing.getTopic());

        existing.setCourseName(request.courseName());
        existing.setDateTime(request.dateTime());
        existing.setDurationMinutes(request.durationMinutes());
        existing.setRoomNo(request.roomNo());
        existing.setTopic(request.topic());

        ClassTest saved = classTestRepository.save(existing);

        record_("CLASS_TEST", id, "UPDATE", user, String.format(
                "Updated Class Test (CT) from [%s] to [Course '%s', Date/Time '%s', Room '%s', Topic '%s']",
                before, saved.getCourseName(), saved.getDateTime(), saved.getRoomNo(), saved.getTopic()));

        return toClassTestResponse(saved);
    }

    @Transactional
    public void deleteClassTest(Long id) {
        User user = currentUserService.requireCurrentUser();
        ClassTest existing = classTestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Class test not found"));
        requireWritable(user, existing.getStudentClass(), existing.getUniversity());

        String details = String.format(
                "Cancelled Class Test (CT): Course '%s', Date/Time '%s', Room '%s', Topic '%s'",
                existing.getCourseName(), existing.getDateTime(), existing.getRoomNo(), existing.getTopic());

        classTestRepository.delete(existing);
        record_("CLASS_TEST", id, "DELETE", user, details);
    }

    // --- Audit ---

    @Transactional(readOnly = true)
    public List<AuditLogResponse> listAuditLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc().stream()
                .map(a -> new AuditLogResponse(a.getId(), a.getEntityType(), a.getEntityId(),
                        a.getAction(), a.getChangedBy(), a.getTimestamp(), a.getDetails()))
                .toList();
    }

    // --- Class resolution ---

    /**
     * The class a read should cover, or null to mean "everything in the
     * caller's university".
     */
    private StudentClass resolveReadableClass(User user, Long classId) {
        if (classId != null) {
            StudentClass requested = studentClassRepository.findById(classId)
                    .orElseThrow(() -> new NotFoundException("Class not found"));
            currentUserService.assertSameUniversity(requested.getUniversity());
            return requested;
        }
        // Students and their representatives only ever see their own class.
        if (user.getRole() == User.Role.STUDENT || user.getRole() == User.Role.CR) {
            if (user.getStudentClass() == null) {
                throw new ValidationException("Your account is not assigned to a class yet");
            }
            return user.getStudentClass();
        }
        return null;
    }

    /**
     * The class a write should apply to.
     *
     * <p>A class representative is always pinned to their own class, whatever
     * the request asked for. Staff may name a class, and it must be one of
     * theirs.
     */
    private StudentClass resolveWritableClass(User user, ClassRef requested) {
        if (user.getRole() == User.Role.CR || user.getRole() == User.Role.STUDENT) {
            if (user.getStudentClass() == null) {
                throw new ValidationException("Your account is not assigned to a class yet");
            }
            return user.getStudentClass();
        }
        if (requested == null || requested.id() == null) {
            return null;
        }
        StudentClass target = studentClassRepository.findById(requested.id())
                .orElseThrow(() -> new NotFoundException("Class not found"));
        currentUserService.assertSameUniversity(target.getUniversity());
        return target;
    }

    /** Asserts the caller may modify an existing item. */
    private void requireWritable(User user, StudentClass itemClass, com.ustc.learnx.entity.University itemUniversity) {
        currentUserService.assertSameUniversity(itemUniversity);
        if (user.getRole() == User.Role.CR || user.getRole() == User.Role.STUDENT) {
            Long own = user.getStudentClass() == null ? null : user.getStudentClass().getId();
            if (itemClass == null || own == null || !itemClass.getId().equals(own)) {
                throw new AccessDeniedException("You cannot change the schedule of another class");
            }
        }
    }

    private boolean belongsToUniversity(com.ustc.learnx.entity.University university, User user) {
        if (user.getUniversity() == null) {
            return true;
        }
        return university != null && university.getId().equals(user.getUniversity().getId());
    }

    private static void requireOrderedTimes(java.time.LocalTime start, java.time.LocalTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new ValidationException("The class must end after it starts");
        }
    }

    /** Records a change. Named with a trailing underscore to avoid the record keyword. */
    private void record_(String entityType, Long entityId, String action, User user, String details) {
        auditLogRepository.save(AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .changedBy(user.getUsername())
                .timestamp(LocalDateTime.now())
                .details(details)
                .build());
    }

    private static RoutineItemResponse toRoutineResponse(ScheduleItem item) {
        StudentClass sc = item.getStudentClass();
        return new RoutineItemResponse(
                item.getId(), item.getDayOfWeek(), item.getStartTime(), item.getEndTime(),
                item.getCourseName(), item.getTeacherName(), item.getRoomNo(),
                sc == null ? null : sc.getId(),
                sc == null ? null : sc.getClassName());
    }

    private static ClassTestResponse toClassTestResponse(ClassTest ct) {
        StudentClass sc = ct.getStudentClass();
        return new ClassTestResponse(
                ct.getId(), ct.getCourseName(), ct.getDateTime(), ct.getDurationMinutes(),
                ct.getRoomNo(), ct.getTopic(), ct.getCreatedBy(),
                sc == null ? null : sc.getId(),
                sc == null ? null : sc.getClassName());
    }
}
