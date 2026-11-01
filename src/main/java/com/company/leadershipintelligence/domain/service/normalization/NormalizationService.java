package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.NormalizationStatus;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.NormalizedInput;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.strategy.NormalizationResult;
import com.company.leadershipintelligence.domain.strategy.NormalizationStrategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Normalizes a list of inputs against an immutable configuration snapshot (specv3.md Section 8.2).
 * Question type and normalization config come from the snapshot only. Output is sorted by question id,
 * so it does not depend on the order the caller supplied the inputs in (configured questions with no input
 * are included as missing).
 * Duplicate answers are rejected until a template duplicate policy is configured.
 */
public class NormalizationService {

    private final Map<QuestionType, NormalizationStrategy> strategies = new EnumMap<>(QuestionType.class);

    public NormalizationService(List<NormalizationStrategy> registered) {
        for (NormalizationStrategy strategy : registered) {
            if (strategies.put(strategy.supportedType(), strategy) != null) {
                throw new IllegalArgumentException("duplicate strategy for " + strategy.supportedType());
            }
        }
    }

    public List<NormalizedInput> normalize(List<ScoringInput> inputs, ScoringConfigurationSnapshot configuration) {
        List<NormalizedInput> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ScoringInput input : inputs) {
            if (!seen.add(input.questionId())) {
                throw new ScoringException(ScoringErrorCode.DUPLICATE_QUESTION_INPUT,
                        "more than one answer for question " + input.questionId(),
                        Map.of("questionId", input.questionId()));
            }
            QuestionDefinition question = configuration.question(input.questionId()).orElseThrow(() ->
                    new ScoringException(ScoringErrorCode.QUESTION_NOT_CONFIGURED,
                            "question " + input.questionId() + " has no scoring configuration",
                            Map.of("questionId", input.questionId())));
            results.add(normalizeOne(input, question));
        }
        for (QuestionDefinition question : configuration.questionsInOrder()) {
            if (!seen.contains(question.questionId())) {
                results.add(applyMissingPolicy(question));
            }
        }
        results.sort(Comparator.comparing(NormalizedInput::questionId));
        return List.copyOf(results);
    }

    private NormalizedInput normalizeOne(ScoringInput input, QuestionDefinition question) {
        return normalizeAnswer(input, question).orElseGet(() -> applyMissingPolicy(question));
    }

    /**
     * Normalizes one answer without applying the missing-data policy: a blank answer, or one the strategy treats
     * as missing, gives an empty result. Used when several answers to one question are combined, where the
     * policy applies once to the question and never to a single respondent (decision D45).
     */
    public Optional<NormalizedInput> normalizeAnswer(ScoringInput input, ScoringConfigurationSnapshot configuration) {
        QuestionDefinition question = configuration.question(input.questionId()).orElseThrow(() ->
                new ScoringException(ScoringErrorCode.QUESTION_NOT_CONFIGURED,
                        "question " + input.questionId() + " has no scoring configuration",
                        Map.of("questionId", input.questionId())));
        return normalizeAnswer(input, question);
    }

    private Optional<NormalizedInput> normalizeAnswer(ScoringInput input, QuestionDefinition question) {
        NormalizationStrategy strategy = strategyFor(question);
        if (isMissing(input.answer())) {
            return Optional.empty();
        }
        NormalizationResult result = strategy.normalize(input, question, question.normalization());
        if (result.treatAsMissing()) {
            return Optional.empty();
        }
        NormalizationStatus status = result.value() != null ? NormalizationStatus.NORMALIZED : NormalizationStatus.SIGNALS_ONLY;
        return Optional.of(new NormalizedInput(question.questionId(), question.type(), result.value(), result.signals(),
                status, strategy.strategyId(), strategy.strategyVersion(), result.clamped()));
    }

    /** The question's missing-data policy applied once (specv3.md Section 8.10). */
    public NormalizedInput missing(QuestionDefinition question) {
        return applyMissingPolicy(question);
    }

    private NormalizedInput applyMissingPolicy(QuestionDefinition question) {
        NormalizationStrategy strategy = strategyFor(question);
        String id = strategy.strategyId();
        String version = strategy.strategyVersion();
        MissingDataPolicy policy = question.missingPolicy();
        return switch (policy) {
            case IGNORE -> new NormalizedInput(question.questionId(), question.type(), null, Set.of(),
                    NormalizationStatus.MISSING_IGNORED, id, version);
            case NEUTRAL -> new NormalizedInput(question.questionId(), question.type(), question.neutralValue(), Set.of(),
                    NormalizationStatus.NEUTRAL_APPLIED, id, version);
            case RISK_SIGNAL -> new NormalizedInput(question.questionId(), question.type(), null,
                    Set.of(question.riskSignalId()), NormalizationStatus.MISSING_RISK_SIGNAL, id, version);
            case REQUIRED -> throw new ScoringException(ScoringErrorCode.MISSING_REQUIRED_INPUT,
                    "required answer for question " + question.questionId() + " is missing",
                    Map.of("questionId", question.questionId()));
        };
    }

    private NormalizationStrategy strategyFor(QuestionDefinition question) {
        NormalizationStrategy strategy = strategies.get(question.type());
        if (strategy == null) {
            throw new ScoringException(ScoringErrorCode.UNSUPPORTED_QUESTION_TYPE,
                    "no normalization strategy for " + question.type(), Map.of("questionId", question.questionId()));
        }
        return strategy;
    }

    /**
     * Null is missing; blank text is missing (a missing text answer is not the absence of a risk); an empty selection
     * is missing (specv3.md Section 4.2.1), never zero.
     */
    private static boolean isMissing(Object answer) {
        return answer == null || (answer instanceof String text && text.isBlank())
                || (answer instanceof java.util.Collection<?> selection && selection.isEmpty());
    }
}
