package com.ustc.learnx.common;

/**
 * Minimum password requirements, applied wherever a password is chosen or reset.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /**
     * @return {@code null} if the password is acceptable, otherwise a message
     *         explaining what is wrong with it
     */
    public static String validate(String password) {
        if (password == null || password.isBlank()) {
            return "Password is required";
        }
        if (password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters long";
        }
        if (password.length() > MAX_LENGTH) {
            return "Password must be at most " + MAX_LENGTH + " characters long";
        }

        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            return "Password must contain at least one letter and one number";
        }
        return null;
    }
}
