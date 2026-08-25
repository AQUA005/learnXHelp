package com.ustc.learnx.common;

/**
 * Thrown when the caller is authenticated but the requested object belongs to
 * another university, class, or user. Mapped to HTTP 403.
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
