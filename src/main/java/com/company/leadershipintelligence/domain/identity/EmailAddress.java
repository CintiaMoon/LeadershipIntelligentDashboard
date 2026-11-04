package com.company.leadershipintelligence.domain.identity;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An email address as the platform stores it: trimmed and lower case, at most 254 characters, one "@" with text on both
 * sides and a dot in the domain. Deliberately simple: the real proof that an address works is the verification email.
 */
public final class EmailAddress {

    public static final int MAX_LENGTH = 254;
    private static final Pattern SHAPE = Pattern.compile("[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@.]+");

    private EmailAddress() {
    }

    /** The normalized address, or null when the text is not an email address. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > MAX_LENGTH || !SHAPE.matcher(value).matches() || value.contains("..")) {
            return null;
        }
        return value;
    }
}
