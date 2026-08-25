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

    private static final String SHELL = "forward:/index.html";

    /** Single-segment routes, such as {@code /schedule}. */
    @GetMapping("/{path:^(?!api|actuator|assets)[^.]*}")
    public String forwardTopLevel() {
        return SHELL;
    }

    /** Nested routes, such as {@code /exams/12}. */
    @GetMapping("/{path:^(?!api|actuator|assets)[^.]*}/**")
    public String forwardNested() {
        return SHELL;
    }
}
