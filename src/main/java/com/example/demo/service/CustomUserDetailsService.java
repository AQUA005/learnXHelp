package com.example.demo.service;

import com.example.demo.entity.SystemAdmin;
import com.example.demo.entity.User;
import com.example.demo.repository.SystemAdminRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final SystemAdminRepository systemAdminRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // First check if user is a system admin (site owner / master admin)
        Optional<SystemAdmin> sysAdmin = systemAdminRepository.findByUsername(username);
        if (sysAdmin.isPresent()) {
            return org.springframework.security.core.userdetails.User.builder()
                    .username(sysAdmin.get().getUsername())
                    .password(sysAdmin.get().getPassword())
                    .roles("SYSTEM_ADMIN")
                    .build();
        }

        // Then check if user is a university user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }
}
