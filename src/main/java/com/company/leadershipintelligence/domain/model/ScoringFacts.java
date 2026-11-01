package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.ScoringDimension;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the earlier stages produced, handed unchanged to the explanation stage. {@code lisInterpretation}
 * is null when the score is INSUFFICIENT_DATA; {@code dimensionInterpretations} may be empty.
 */
public record ScoringFacts(
        RunVersions versions,
        List<NormalizedInput> normalizedInputs,
        List<DerivedSignal> signals,
        List<DimensionScore> dimensionScores,
        List<ImbalanceResult> imbalances,
        List<PatternEvaluation> patterns,
        PenaltyAggregation penalties,
        OverallScore overall,
        InterpretationResult lisInterpretation,
        Map<ScoringDimension, InterpretationResult> dimensionInterpretations,
        List<ResponseSummary> responseSummaries) {

    /** Facts of a run whose answers were not combined from several respondents (the earlier form). */
    public ScoringFacts(RunVersions versions, List<NormalizedInput> normalizedInputs, List<DerivedSignal> signals,
                        List<DimensionScore> dimensionScores, List<ImbalanceResult> imbalances, List<PatternEvaluation> patterns,
                        PenaltyAggregation penalties, OverallScore overall, InterpretationResult lisInterpretation,
                        Map<ScoringDimension, InterpretationResult> dimensionInterpretations) {
        this(versions, normalizedInputs, signals, dimensionScores, imbalances, patterns, penalties, overall,
                lisInterpretation, dimensionInterpretations, List.of());
    }

    public ScoringFacts {
        responseSummaries = List.copyOf(responseSummaries);
        normalizedInputs = List.copyOf(normalizedInputs);
        signals = List.copyOf(signals);
        dimensionScores = List.copyOf(dimensionScores);
        imbalances = List.copyOf(imbalances);
        patterns = List.copyOf(patterns);
        Map<ScoringDimension, InterpretationResult> copy = new EnumMap<>(ScoringDimension.class);
        copy.putAll(dimensionInterpretations);
        dimensionInterpretations = Collections.unmodifiableMap(copy);
    }
}
