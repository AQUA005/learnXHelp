package com.ustc.learnx.service;

import com.ustc.learnx.common.AccessDeniedException;
import com.ustc.learnx.entity.StudentClass;
import com.ustc.learnx.entity.SystemAdmin;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.SystemAdminRepository;
import com.ustc.learnx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves the caller's identity from the security context and answers
 * "may this user touch that object?".
 *
 * <p>Tenancy is derived from the authenticated user's own row. It is never read
 * from a request header, because a client can set any header it likes.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;
    private final SystemAdminRepository systemAdminRepository;

    /** The authenticated username, or empty if the request is anonymous. */
    public Optional<String> currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        return Optional.ofNullable(auth.getName());
    }

    /** The authenticated {@link User}, or empty for anonymous or system-admin callers. */
    public Optional<User> currentUser() {
        return currentUsername().flatMap(userRepository::findByUsername);
    }

    /** The authenticated {@link User}, or 403 if there isn't one. */
    public User requireCurrentUser() {
        return currentUser().orElseThrow(
                () -> new AccessDeniedException("No authenticated university user for this request"));
    }

    /** The authenticated platform administrator, if the caller is one. */
    public Optional<SystemAdmin> currentSystemAdmin() {
        return currentUsername().flatMap(systemAdminRepository::findByUsername);
    }

    public boolean isSystemAdmin() {
        return currentSystemAdmin().isPresent();
    }

    /** The caller's university, or 403 if they have none. */
    public University requireUniversity() {
        University university = requireCurrentUser().getUniversity();
        if (university == null) {
            throw new AccessDeniedException("Your account is not attached to a university");
        }
        return university;
    }

    public Long requireUniversityId() {
        return requireUniversity().getId();
    }

    /**
     * Asserts that {@code target} belongs to the caller's university.
     * Platform administrators bypass this check.
     *
     * @param target the university an object under access belongs to
     */
    public void assertSameUniversity(University target) {
        if (isSystemAdmin()) {
            return;
        }
        University own = requireUniversity();
        if (target == null || !own.getId().equals(target.getId())) {
            throw new AccessDeniedException("That item belongs to a different university");
        }
    }

    /**
     * Asserts that {@code target} is the caller's own class. Teachers and above
     * are allowed across classes within their university.
     */
    public void assertSameClass(StudentClass target) {
        if (isSystemAdmin()) {
            return;
        }
        User me = requireCurrentUser();
        if (me.getRole() == User.Role.TEACHER || me.getRole() == User.Role.ADMIN) {
            assertSameUniversity(target == null ? null : target.getUniversity());
            return;
        }
        StudentClass own = me.getStudentClass();
        if (target == null || own == null || !own.getId().equals(target.getId())) {
            throw new AccessDeniedException("That item belongs to a different class");
        }
    }
}
