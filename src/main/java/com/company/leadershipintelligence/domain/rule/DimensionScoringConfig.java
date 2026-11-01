package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Question-to-dimension mappings and per-dimension coverage settings (specv3.md Sections 10.2, 10.6, 10.7).
 * Either empty ({@link #none()}) or complete: all four dimensions need settings and at least one
 * positive-weight mapping. The same question may not be mapped twice to one dimension (duplicate source
 * detection) or to more dimensions than {@code maxDimensionsPerQuestion}. Correlation-group policies are
 * not implemented yet.
 */
public record DimensionScoringConfig(
        Map<ScoringDimension, DimensionSettings> settings,
        List<ContributionRule> rules,
        int maxDimensionsPerQuestion) {

    public DimensionScoringConfig {
        if (settings == null || rules == null) {
            throw new ScoringException(ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION, "settings and rules are required");
        }
        Map<ScoringDimension, DimensionSettings> copy = new EnumMap<>(ScoringDimension.class);
        copy.putAll(settings);
        settings = Collections.unmodifiableMap(copy);
        List<ContributionRule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparing(ContributionRule::dimension).thenComparing(ContributionRule::questionId));
        rules = List.copyOf(sorted);
        if (maxDimensionsPerQuestion < 1) {
            throw new ScoringException(ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION, "maxDimensionsPerQuestion must be at least 1");
        }
        if (!rules.isEmpty() || !settings.isEmpty()) {
            validate(settings, rules, maxDimensionsPerQuestion);
        }
    }

    public static DimensionScoringConfig none() {
        return new DimensionScoringConfig(Map.of(), List.of(), 4);
    }

    public boolean isConfigured() {
        return !settings.isEmpty();
    }

    /** Rules for one dimension in question-id order. */
    public List<ContributionRule> rulesFor(ScoringDimension dimension) {
        return rules.stream().filter(r -> r.dimension() == dimension).toList();
    }

    private static void validate(Map<ScoringDimension, DimensionSettings> settings, List<ContributionRule> rules, int max) {
        Set<String> pairs = new HashSet<>();
        Map<String, Integer> perQuestion = new java.util.HashMap<>();
        for (ContributionRule rule : rules) {
            if (!pairs.add(rule.questionId() + "|" + rule.dimension())) {
                throw new ScoringException(ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION,
                        "question " + rule.questionId() + " is mapped twice to " + rule.dimension());
            }
            if (perQuestion.merge(rule.questionId(), 1, Integer::sum) > max) {
                throw new ScoringException(ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION,
                        "question " + rule.questionId() + " contributes to more than " + max + " dimensions");
            }
        }
        for (ScoringDimension dimension : ScoringDimension.values()) {
            if (!settings.containsKey(dimension)) {
                throw new ScoringException(ScoringErrorCode.INVALID_COVERAGE_CONFIGURATION,
                        "no coverage settings for " + dimension);
            }
            boolean hasPositive = rules.stream().anyMatch(r -> r.dimension() == dimension && !r.weight().isZero());
            if (!hasPositive) {
                throw new ScoringException(ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION,
                        dimension + " has no mapping with a positive weight");
            }
        }
    }
}
