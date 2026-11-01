package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.rule.Operand;
import com.company.leadershipintelligence.domain.valueobject.ScoreValue;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only facts available to pattern conditions (specv3.md Section 6.2): derived signals, RAW dimension
 * scores of VALID dimensions only, and the flags detected for each question. An insufficient dimension is
 * absent, never zero. Built once per run; nothing here can change a score.
 */
public final class EvaluationContext {

    private final Map<String, DerivedSignal> signals = new HashMap<>();
    private final Map<ScoringDimension, ScoreValue> dimensions = new EnumMap<>(ScoringDimension.class);
    private final Map<String, NormalizedInput> inputs = new HashMap<>();

    private ParticipationInfo participation;

    private EvaluationContext() {
    }

    /** Respondent counts of this unit, present only when at least one question has a known count (decision D45). */
    public Optional<ParticipationInfo> participation() {
        return Optional.ofNullable(participation);
    }

    public static EvaluationContext of(List<DerivedSignal> signals, List<DimensionScore> scores, List<NormalizedInput> inputs,
                                       Optional<ParticipationInfo> participation) {
        EvaluationContext context = of(signals, scores, inputs);
        context.participation = participation.orElse(null);
        return context;
    }

    public static EvaluationContext of(List<DerivedSignal> signals, List<DimensionScore> scores, List<NormalizedInput> inputs) {
        EvaluationContext context = new EvaluationContext();
        signals.forEach(s -> context.signals.put(s.signalId(), s));
        scores.forEach(s -> s.score().ifPresent(v -> context.dimensions.put(s.dimension(), v)));
        inputs.forEach(i -> context.inputs.put(i.questionId(), i));
        return context;
    }

    public Optional<DerivedSignal> signal(String signalId) {
        return Optional.ofNullable(signals.get(signalId));
    }

    public Optional<ScoreValue> rawDimension(ScoringDimension dimension) {
        return Optional.ofNullable(dimensions.get(dimension));
    }

    public Optional<Set<String>> questionSignals(String questionId) {
        NormalizedInput input = inputs.get(questionId);
        return input == null ? Optional.empty() : Optional.of(Collections.unmodifiableSet(input.detectedSignals()));
    }

    public boolean available(Operand operand) {
        return switch (operand) {
            case Operand.SignalOperand s -> signals.containsKey(s.signalId());
            case Operand.DimensionOperand d -> dimensions.containsKey(d.dimension());
            case Operand.QuestionSignalsOperand q -> inputs.containsKey(q.questionId());
        };
    }
}
