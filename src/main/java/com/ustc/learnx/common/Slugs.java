package com.ustc.learnx.common;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Turns a name into the short, dot-free key a university is addressed by in a
 * public URL.
 *
 * <p>Dot-free matters: the shell forwards a path to the client router only when
 * its first segment has no file extension, so a slug containing a dot would not
 * resolve. Assigned once at creation and never changed, because it appears in
 * links people share.
 */
public final class Slugs {

    private static final int MAX_LENGTH = 64;

    private Slugs() {
    }

    /**
     * A slug derived from {@code name} that {@code taken} does not reject.
     *
     * @param taken answers whether a candidate is already in use
     */
    public static String uniqueFrom(String name, Predicate<String> taken) {
        String base = of(name);
        if (!taken.test(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 1_000; suffix++) {
            String candidate = truncate(base, MAX_LENGTH - 4) + "-" + suffix;
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not derive a free slug from " + name);
    }

    /** The slug form of {@code name}, ignoring whether it is already taken. */
    public static String of(String name) {
        String cleaned = (name == null ? "" : name)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return cleaned.isBlank() ? "university" : truncate(cleaned, MAX_LENGTH);
    }

    private static String truncate(String value, int length) {
        return value.length() <= length
                ? value
                : value.substring(0, length).replaceAll("-+$", "");
    }
}
