package com.ustc.learnx.service;

import com.ustc.learnx.common.AccessDeniedException;
import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.dto.AnnouncementDtos.AnnouncementResponse;
import com.ustc.learnx.dto.AnnouncementDtos.CreateAnnouncementRequest;
import com.ustc.learnx.entity.Announcement;
import com.ustc.learnx.entity.StudentClass;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Announcements to a class or to the whole university.
 *
 * <p>Two problems are corrected here. Announcements were saved without a
 * university, so they were visible across tenants; and the right to delete one
 * was decided by comparing display names, meaning two people sharing a name
 * could remove each other's posts. Deletion is now decided by role and class.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> list() {
        User user = currentUserService.requireCurrentUser();

        List<Announcement> announcements =
                (isStudentLike(user) && user.getStudentClass() != null)
                        ? announcementRepository
                                .findByStudentClassOrStudentClassIsNullOrderByCreatedAtDesc(user.getStudentClass())
                        : announcementRepository.findAllByOrderByCreatedAtDesc();

        return announcements.stream()
                .filter(a -> inCallersUniversity(a, user))
                .map(AnnouncementService::toResponse)
                .toList();
    }

    @Transactional
    public AnnouncementResponse create(CreateAnnouncementRequest request) {
        User user = currentUserService.requireCurrentUser();

        // A representative speaks only to their own class; staff may address
        // the whole university.
        StudentClass target;
        if (user.getRole() == User.Role.CR) {
            if (user.getStudentClass() == null) {
                throw new ValidationException("Your account is not assigned to a class yet");
            }
            target = user.getStudentClass();
        } else {
            target = request.global() ? null : user.getStudentClass();
        }

        Announcement saved = announcementRepository.save(Announcement.builder()
                .title(request.title())
                .content(request.content())
                .createdAt(LocalDateTime.now())
                .createdBy(user.getFullName())
                .createdByRole(user.getRole().name())
                .studentClass(target)
                // Previously left unset, which put announcements outside every
                // tenancy check.
                .university(user.getUniversity())
                .build());

        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        User user = currentUserService.requireCurrentUser();
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Announcement not found"));

        currentUserService.assertSameUniversity(announcement.getUniversity());

        // Staff may remove any announcement in their university. A class
        // representative may remove only those addressed to their own class.
        boolean isStaff = user.getRole() == User.Role.TEACHER
                || user.getRole() == User.Role.ADMIN
                || user.getRole() == User.Role.SYSTEM_ADMIN;
        if (!isStaff) {
            Long own = user.getStudentClass() == null ? null : user.getStudentClass().getId();
            StudentClass target = announcement.getStudentClass();
            if (target == null || own == null || !target.getId().equals(own)) {
                throw new AccessDeniedException("You may only remove announcements for your own class");
            }
        }

        announcementRepository.delete(announcement);
    }

    private static boolean isStudentLike(User user) {
        return user.getRole() == User.Role.STUDENT || user.getRole() == User.Role.CR;
    }

    private boolean inCallersUniversity(Announcement announcement, User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMIN || user.getUniversity() == null) {
            return true;
        }
        // Announcements predating the university column are still shown, rather
        // than vanishing from every feed.
        return announcement.getUniversity() == null
                || announcement.getUniversity().getId().equals(user.getUniversity().getId());
    }

    private static AnnouncementResponse toResponse(Announcement a) {
        StudentClass sc = a.getStudentClass();
        return new AnnouncementResponse(
                a.getId(), a.getTitle(), a.getContent(), a.getCreatedAt(),
                a.getCreatedBy(), a.getCreatedByRole(),
                sc == null ? null : sc.getId(),
                sc == null ? null : sc.getClassName());
    }
}
