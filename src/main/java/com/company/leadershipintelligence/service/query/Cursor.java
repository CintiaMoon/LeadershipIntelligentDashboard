package com.company.leadershipintelligence.service.query;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** Opaque paging cursors: callers pass them back unchanged and never rely on their content. */
public final class Cursor {

    public record AssessmentPosition(Instant createdAt, String id) {
    }

    private Cursor() {
    }

    public static String encodeSeq(long seq) {
        return encode("r|" + seq);
    }

    public static long decodeSeq(String cursor) {
        String[] parts = decode(cursor).split("\\|", -1);
        if (parts.length != 2 || !parts[0].equals("r")) {
            throw new IllegalArgumentException("invalid cursor");
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid cursor");
        }
    }

    public static String encodeAssessment(Instant createdAt, String id) {
        return encode("a|" + createdAt + "|" + id);
    }

    public static AssessmentPosition decodeAssessment(String cursor) {
        String[] parts = decode(cursor).split("\\|", 3);
        if (parts.length != 3 || !parts[0].equals("a")) {
            throw new IllegalArgumentException("invalid cursor");
        }
        try {
            return new AssessmentPosition(Instant.parse(parts[1]), parts[2]);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid cursor");
        }
    }

    private static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String cursor) {
        try {
            return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid cursor");
        }
    }
}
