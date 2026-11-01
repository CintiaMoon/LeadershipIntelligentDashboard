package com.company.leadershipintelligence.domain.exception;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Domain error independent of HTTP (specv3.md Section 29.1). Details never contain raw answers. */
public class ScoringException extends RuntimeException {

    private final ScoringErrorCode code;
    private final Map<String, String> details;

    public ScoringException(ScoringErrorCode code, String message, Map<String, String> details) {
        super(message);
        this.code = code;
        this.details = Collections.unmodifiableMap(new TreeMap<>(details));
    }

    public ScoringException(ScoringErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ScoringErrorCode code() {
        return code;
    }

    public Map<String, String> details() {
        return details;
    }
}
