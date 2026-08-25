package com.ustc.learnx.common;

/**
 * Thrown when a request is well-formed but asks for something the domain does
 * not allow. Mapped to HTTP 400.
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
