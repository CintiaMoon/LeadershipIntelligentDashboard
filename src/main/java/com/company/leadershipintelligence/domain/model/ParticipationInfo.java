package com.company.leadershipintelligence.domain.model;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Respondent counts known for the questions of one scoring unit, used by the participation factor of pattern
 * confidence (specv3.md Section 14.2.1). Questions with unknown participation are absent. When no count is known
 * at all this object is not created and confidence algorithm version 1 applies.
 */
public record ParticipationInfo(Map<String, Integer> respondentsByQuestion, int fullConfidenceResponses) {

    public ParticipationInfo {
        if (respondentsByQuestion == null || respondentsByQuestion.isEmpty()) {
            throw new IllegalArgumentException("participation needs at least one known respondent count");
        }
        if (fullConfidenceResponses < 1) {
            throw new IllegalArgumentException("fullConfidenceResponses must be at least 1");
        }
        respondentsByQuestion = Collections.unmodifiableSortedMap(new TreeMap<>(respondentsByQuestion));
    }

    public static Optional<ParticipationInfo> of(Map<String, Integer> counts, int fullConfidenceResponses) {
        return counts.isEmpty() ? Optional.empty() : Optional.of(new ParticipationInfo(counts, fullConfidenceResponses));
    }

    /** The lowest known count among the given questions; empty when none of them has a known count. */
    public OptionalInt lowestAmong(Iterable<String> questionIds) {
        int lowest = Integer.MAX_VALUE;
        boolean found = false;
        for (String id : questionIds) {
            Integer count = respondentsByQuestion.get(id);
            if (count != null) {
                found = true;
                lowest = Math.min(lowest, count);
            }
        }
        return found ? OptionalInt.of(lowest) : OptionalInt.empty();
    }

    /** The lowest known count in the whole unit. */
    public int lowest() {
        return respondentsByQuestion.values().stream().mapToInt(Integer::intValue).min().orElseThrow();
    }
}
