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

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

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
