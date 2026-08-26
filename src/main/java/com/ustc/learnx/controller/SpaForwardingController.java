package com.ustc.learnx.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Returns the application shell for client-side routes.
 *
 * <p>The frontend owns paths such as {@code /schedule} and {@code /exams/12}.
 * The server has no handler for those, so without this a refresh or a shared
 * link would answer 404. Requests under {@code /api} and {@code /actuator}, and
 * anything with a file extension, are excluded so real endpoints and missing
 * assets still report their own status rather than quietly returning HTML.
 */
@Controller
public class SpaForwardingController {

    /**
     * The first path segment of a client-side route.
     *
     * <p>{@code SecurityConfig} matches on this same constant to decide what may
     * be fetched before signing in. Keeping one definition is deliberate: the
     * two were separate once, and the security side was a hand-written list of
     * every route the React router owned. Adding a screen without extending it
     * meant a hard refresh on that path answered 401, with nothing to say why.
     */
    public static final String ROUTE_SEGMENT = "^(?!api|actuator|assets|h2-console)[^.]*";

    private static final String SHELL = "forward:/index.html";

    /** Single-segment routes, such as {@code /schedule}. */
    @GetMapping("/{path:" + ROUTE_SEGMENT + "}")
    public String forwardTopLevel() {
        return SHELL;
    }

    /** Nested routes, such as {@code /exams/12} or {@code /u/ustc-ac-bd}. */
    @GetMapping("/{path:" + ROUTE_SEGMENT + "}/**")
    public String forwardNested() {
        return SHELL;
    }
}
