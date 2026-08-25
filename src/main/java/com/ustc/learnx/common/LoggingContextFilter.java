package com.ustc.learnx.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Tags every log line written while handling a request.
 *
 * <p>Puts a request id and the caller's username into the logging context, so a
 * report of "it failed for me at about two o'clock" can be traced to the exact
 * request rather than guessed at from timestamps.
 */
@Component
@Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE - 10)
public class LoggingContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";
    public static final String USERNAME = "username";

    /** Echoed back so a user can quote it when reporting a problem. */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(REQUEST_ID, requestId);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            MDC.put(USERNAME, authentication.getName());
        }

        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are pooled, so the context must not leak into the next
            // request handled by this one.
            MDC.remove(REQUEST_ID);
            MDC.remove(USERNAME);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Static assets are noise; there is nothing to trace.
        String path = request.getRequestURI();
        return path.startsWith("/assets/");
    }
}
