package com.ustc.learnx.common;

/**
 * Thrown when a request carries no usable identity — bad credentials, or a
 * session whose account no longer exists. Mapped to HTTP 401.
 *
 * <p>Distinct from {@link AccessDeniedException}, which means "we know who you
 * are and the answer is no" and maps to 403. Signing in with an unknown account
 * and signing in with a wrong password must both raise this with the <em>same</em>
 * message, so the response cannot be used to enumerate accounts.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
