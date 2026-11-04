package com.company.leadershipintelligence.domain.identity;

import java.util.Locale;
import java.util.Set;

/**
 * The password rules (decision D65), in the NIST style: length rather than composition. 12 to 128 characters, not the
 * email address (or its local part) and not one of a short list of very common passwords. Nothing is logged or returned
 * about the password itself; the result names only which rule failed.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> COMMON = Set.of(
            "password1234", "password12345", "123456789012", "1234567890123", "qwertyuiop12", "qwertyuiopas",
            "passwordpassword", "letmein12345", "welcome12345", "administrator", "iloveyou1234", "abcdefghijkl",
            "changeme1234", "111111111111", "000000000000", "p@ssw0rd1234", "password!234", "correcthorse");

    private PasswordPolicy() {
    }

    /** The failed rule as a short message for the caller, or null when the password is acceptable. */
    public static String violation(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "The password must have at least " + MIN_LENGTH + " characters.";
        }
        if (password.length() > MAX_LENGTH) {
            return "The password must have at most " + MAX_LENGTH + " characters.";
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            return "The password is too common.";
        }
        if (email != null) {
            String address = email.toLowerCase(Locale.ROOT);
            String local = address.contains("@") ? address.substring(0, address.indexOf('@')) : address;
            if (lower.equals(address) || (local.length() >= 6 && lower.equals(local))) {
                return "The password must not be the email address.";
            }
        }
        if (password.chars().distinct().count() < 4) {
            return "The password is too repetitive.";
        }
        return null;
    }
}
