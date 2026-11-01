package com.company.leadershipintelligence.domain.strategy;

import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Result of normalizing one present answer (specv3.md Section 8.4). A strategy can also report that the
 * answer must be handled under the question's missing-data policy (unknown multiple-choice option), and
 * whether an out-of-range value was clamped by configuration, so the explanation can say so.
 */
public record NormalizationResult(NormalizedValue value, Set<String> signals, boolean treatAsMissing, boolean clamped) {

    public NormalizationResult {
        signals = signals == null ? Collections.emptySortedSet() : Collections.unmodifiableSortedSet(new TreeSet<>(signals));
    }

    public static NormalizationResult ofValue(NormalizedValue value) {
        return new NormalizationResult(value, Set.of(), false, false);
    }

    public static NormalizationResult ofClampedValue(NormalizedValue value) {
        return new NormalizationResult(value, Set.of(), false, true);
    }

    public static NormalizationResult ofSignals(Set<String> signals) {
        return new NormalizationResult(null, signals, false, false);
    }

    public static NormalizationResult missing() {
        return new NormalizationResult(null, Set.of(), true, false);
    }
}
