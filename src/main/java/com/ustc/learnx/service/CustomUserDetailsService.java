package com.ustc.learnx.service;

import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads accounts for authentication.
 *
 * <p>Every account, platform administrators included, lives in {@code users}
 * and is distinguished by its role. There used to be a second table for
 * platform administrators, which meant every lookup here — and in the login
 * and profile endpoints — had to try both.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Resolves an account from what the sign-in form was given.
     *
     * <p>People sign in with their email address; the username is generated and
     * never typed. Both are accepted so that dev seeds, the end-to-end helper
     * and any browser holding an older bundle keep working.
     *
     * <p>The principal deliberately stays the <em>username</em>. Everything
     * downstream — {@code CurrentUserService}, and so every tenant check in the
     * application — resolves the caller by {@code authentication.getName()}, and
     * two string columns reference {@code users.username} directly. Making the
     * principal the email would break all of it.
     */
    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String trimmed = identifier == null ? "" : identifier.trim();
        User user = userRepository.findByEmail(trimmed.toLowerCase())
                .or(() -> userRepository.findByUsername(trimmed))
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + trimmed));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name())
                // Pending accounts cannot authenticate at all, rather than
                // relying on a check inside the login controller.
                .disabled(!user.isApproved())
                .build();
    }
}
