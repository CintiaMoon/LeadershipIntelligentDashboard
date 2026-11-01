package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.NormalizationStatus;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Canonical representation of one answer (specv3.md Sections 4.4, 8.11). The value is present exactly when
 * the status carries a value; a missing answer is never represented as zero. Detected signals are kept
 * sorted so output is deterministic. {@code clamped} records that an out-of-range value was clamped by
 * configuration. The raw answer is never retained here.
 */
public record NormalizedInput(
        String questionId,
        QuestionType questionType,
        NormalizedValue normalizedValue,
        Set<String> detectedSignals,
        NormalizationStatus status,
        String strategyId,
        String strategyVersion,
        boolean clamped) {

    public NormalizedInput(String questionId, QuestionType questionType, NormalizedValue normalizedValue,
                           Set<String> detectedSignals, NormalizationStatus status, String strategyId,
                           String strategyVersion) {
        this(questionId, questionType, normalizedValue, detectedSignals, status, strategyId, strategyVersion, false);
    }

    public NormalizedInput {
        if (questionId == null || questionId.isBlank()) {
            throw new IllegalArgumentException("questionId must not be blank");
        }
        if (questionType == null || status == null) {
            throw new IllegalArgumentException("questionType and status are required");
        }
        if (strategyId == null || strategyId.isBlank() || strategyVersion == null || strategyVersion.isBlank()) {
            throw new IllegalArgumentException("strategyId and strategyVersion are required");
        }
        if (status.carriesValue() != (normalizedValue != null)) {
            throw new IllegalArgumentException(
                    "status " + status + (status.carriesValue() ? " requires" : " must not have") + " a normalized value");
        }
        if (clamped && normalizedValue == null) {
            throw new IllegalArgumentException("only a numeric value can be clamped");
        }
        detectedSignals = detectedSignals == null
                ? Collections.emptySortedSet()
                : Collections.unmodifiableSortedSet(new TreeSet<>(detectedSignals));
    }

    public Optional<NormalizedValue> value() {
        return Optional.ofNullable(normalizedValue);
    }
}
