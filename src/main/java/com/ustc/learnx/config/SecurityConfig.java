package com.ustc.learnx.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Central security configuration.
 *
 * <p>Two rules govern this file:
 * <ol>
 *   <li>Nothing under {@code /api} is public unless it is part of signing in.
 *       Every other endpoint requires authentication here, and its <em>role</em>
 *       requirement is declared with {@code @PreAuthorize} on the controller.</li>
 *   <li>Role checks live on the controller, never only in the browser.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Files served from the jar. Each contains a dot, so none is a client route. */
    private static final String[] PUBLIC_ASSETS = {
            "/", "/index.html", "/favicon.ico",
            // Resolves the stored theme before the first paint, so it is
            // requested by the shell itself, before anybody has signed in.
            "/theme-boot.js",
            "/assets/**", "/learnx_logo.png", "/ustc_building.jpg"
    };

    /**
     * Client-side routes, which all resolve to the application shell.
     *
     * <p>The same patterns {@code SpaForwardingController} forwards, so this can
     * never grant more than already returns {@code index.html}. It used to be a
     * hand-written list of every route the React router owned, which had to be
     * extended for each new screen; forgetting meant a hard refresh on that path
     * answered 401 instead of rendering, with nothing to say why.
     */
    private static final String[] SPA_ROUTES = {
            "/{path:" + com.ustc.learnx.controller.SpaForwardingController.ROUTE_SEGMENT + "}",
            "/{path:" + com.ustc.learnx.controller.SpaForwardingController.ROUTE_SEGMENT + "}/**"
    };

    /** Reachable without a session so a container can probe the service. */
    private static final String[] PUBLIC_PROBES = {
            "/actuator/health", "/actuator/health/**"
    };

    /** Endpoints that must work before a session exists. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/login",
            "/api/auth/signup",
            "/api/auth/recover/**",
            // The pre-login homepage: platform branding, and the universities
            // that have been published. PublicController is the only thing
            // served here, and it exposes nothing tenant-scoped.
            "/api/public/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Privilege ordering. A TEACHER satisfies {@code hasRole('CR')}, an ADMIN
     * satisfies {@code hasRole('TEACHER')}, and so on, so controllers only ever
     * name the <em>minimum</em> role an endpoint requires.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("SYSTEM_ADMIN").implies("ADMIN")
                .role("ADMIN").implies("TEACHER")
                .role("TEACHER").implies("CR")
                .role("CR").implies("STUDENT")
                .build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Session-cookie auth, so CSRF protection is required. spa() installs the
            // cookie-based repository plus the XOR request handler and makes the
            // XSRF-TOKEN cookie readable by the frontend.
            .csrf(csrf -> csrf.spa())
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; "
                        + "script-src 'self'; "
                        // Inline styles remain allowed because the client sets a
                        // few computed values as element styles. The Google Fonts
                        // hosts are named because the interface asks for Space
                        // Grotesk and Outfit from them: the stylesheet comes from
                        // googleapis, the font files it names from gstatic, and
                        // without both the type silently falls back.
                        + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                        + "font-src 'self' https://fonts.gstatic.com; "
                        + "img-src 'self' data:; "
                        + "connect-src 'self'; "
                        + "object-src 'none'; "
                        + "base-uri 'self'; "
                        + "form-action 'self'; "
                        + "frame-ancestors 'none'"))
                .referrerPolicy(referrer -> referrer.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                .ReferrerPolicy.SAME_ORIGIN))
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                // Rotate the session id on login to defeat session fixation.
                .sessionFixation(fixation -> fixation.newSession())
            )
            // Return 401 for unauthenticated API calls instead of redirecting to a
            // login page the SPA does not have.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PROBES).permitAll()
                // Everything else under /actuator is operational detail.
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers(PUBLIC_ASSETS).permitAll()
                // GET only: these resolve to the shell, and nothing is ever
                // written through them.
                .requestMatchers(HttpMethod.GET, SPA_ROUTES).permitAll()
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                // The signup form is filled in before a session exists, and its
                // department, semester, batch, section and designation fields
                // are dropdowns driven by that reference data. Without anonymous
                // read access the request comes back 401, the lists arrive
                // empty, and the form degrades to free-text boxes that accept
                // anything at all.
                //
                // It is served by /api/public/universities/{slug}/metadata,
                // covered by PUBLIC_ENDPOINTS above, rather than by opening
                // /api/metadata: the signup form knows which university it is
                // for, and the unscoped endpoint would answer an anonymous
                // caller with every university's reference data at once.
                // Everything else — including /api/admin/** and /api/master/**,
                // which were previously permitAll — needs a session. Role
                // requirements are enforced by @PreAuthorize on the controllers.
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
