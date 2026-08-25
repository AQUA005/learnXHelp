package com.ustc.learnx.service;

import com.ustc.learnx.common.AccessDeniedException;
import com.ustc.learnx.entity.StudentClass;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** The authenticated username, or empty if the request is anonymous. */
    public Optional<String> currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        return Optional.ofNullable(auth.getName());
    }

    /** The authenticated account, or empty if the request is anonymous. */
    @Transactional(readOnly = true)
    public Optional<User> currentUser() {
        return currentUsername().flatMap(userRepository::findByUsername);
    }

    /** The authenticated account, or 403 if there isn't one. */
    public User requireCurrentUser() {
        return currentUser().orElseThrow(
                () -> new AccessDeniedException("No authenticated user for this request"));
    }

    /** Platform administrators sit above any single university. */
    public boolean isSystemAdmin() {
        return currentUser().map(u -> u.getRole() == User.Role.SYSTEM_ADMIN).orElse(false);
    }

    /** The caller's university, or 403 if they have none. */
    @Transactional(readOnly = true)
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
     * @param target the university that the object under access belongs to
     */
    @Transactional(readOnly = true)
    public void assertSameUniversity(University target) {
        User me = requireCurrentUser();
        if (me.getRole() == User.Role.SYSTEM_ADMIN) {
            return;
        }
        University own = me.getUniversity();
        if (own == null) {
            throw new AccessDeniedException("Your account is not attached to a university");
        }
        if (target == null || !own.getId().equals(target.getId())) {
            throw new AccessDeniedException("That item belongs to a different university");
        }
    }

    /**
     * Asserts that {@code target} is the caller's own class. Teachers and above
     * are allowed across classes within their university.
     */
    @Transactional(readOnly = true)
    public void assertSameClass(StudentClass target) {
        User me = requireCurrentUser();
        if (me.getRole() == User.Role.SYSTEM_ADMIN) {
            return;
        }
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
