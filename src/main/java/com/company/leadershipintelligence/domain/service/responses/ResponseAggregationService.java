package com.company.leadershipintelligence.domain.service.responses;

import com.company.leadershipintelligence.domain.enums.AggregationMethod;
import com.company.leadershipintelligence.domain.enums.NormalizationStatus;
import com.company.leadershipintelligence.domain.enums.ResponseScope;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.NormalizedInput;
import com.company.leadershipintelligence.domain.model.ParticipationInfo;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ResponseSummary;
import com.company.leadershipintelligence.domain.model.ScopedResponse;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.rule.ResponseSettings;
import com.company.leadershipintelligence.domain.service.normalization.NormalizationService;
import com.company.leadershipintelligence.domain.valueobject.Decimals;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns the answers of one scoring unit (the organization, or one team) into one normalized input per
 * configured question (specv3.md Sections 4.10 to 4.13, decision D45). Each answer is normalized on its own,
 * so direction is preserved, and the normalized values are then combined by the configured method.
 *
 * <p>Rules: one scope per question inside a unit (otherwise {@code MIXED_RESPONSE_SCOPES}); a question with fewer
 * answers than the minimum, or with no usable answer, is missing under its own missing-data policy, applied once and
 * never as zero; a single answer passes through unchanged, so a request with one answer per question scores exactly
 * as it did before scopes existed. The result does not depend on the order of the answers.
 */
public class ResponseAggregationService {

    public record Outcome(List<NormalizedInput> inputs, List<ResponseSummary> summaries, Optional<ParticipationInfo> participation) {
    }

    private final NormalizationService normalization;

    public ResponseAggregationService(NormalizationService normalization) {
        this.normalization = normalization;
    }

    /**
     * @param teamId the team of the unit, or null for the organization unit (which pools every answer except
     *               that team figures stay team figures)
     */
    public Outcome aggregate(List<ScopedResponse> responses, String teamId, ScoringConfigurationSnapshot configuration) {
        ResponseSettings settings = configuration.responses();
        Map<String, List<ScopedResponse>> byQuestion = new TreeMap<>();
        for (ScopedResponse response : responses) {
            if (response.belongsToUnit(teamId)) {
                byQuestion.computeIfAbsent(response.questionId(), k -> new ArrayList<>()).add(response);
            }
        }
        List<NormalizedInput> inputs = new ArrayList<>();
        List<ResponseSummary> summaries = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        for (Map.Entry<String, List<ScopedResponse>> entry : byQuestion.entrySet()) {
            QuestionDefinition question = configuration.question(entry.getKey()).orElseThrow(() ->
                    new ScoringException(ScoringErrorCode.QUESTION_NOT_CONFIGURED,
                            "question " + entry.getKey() + " has no scoring configuration",
                            Map.of("questionId", entry.getKey())));
            Combined combined = combine(question, entry.getValue(), configuration, settings);
            inputs.add(combined.input());
            summaries.add(combined.summary());
            if (combined.summary().respondents() != null && !combined.summary().belowMinimum()) {
                counts.put(entry.getKey(), combined.summary().respondents());
            }
        }
        for (QuestionDefinition question : configuration.questionsInOrder()) {
            if (!byQuestion.containsKey(question.questionId())) {
                inputs.add(normalization.missing(question));
            }
        }
        inputs.sort(Comparator.comparing(NormalizedInput::questionId));
        return new Outcome(List.copyOf(inputs), List.copyOf(summaries),
                ParticipationInfo.of(counts, settings.fullConfidenceResponses()));
    }

    private record Combined(NormalizedInput input, ResponseSummary summary) {
    }

    private Combined combine(QuestionDefinition question, List<ScopedResponse> answers, ScoringConfigurationSnapshot configuration,
                             ResponseSettings settings) {
        ResponseScope scope = singleScope(question.questionId(), answers);
        rejectDuplicates(question.questionId(), scope, answers);
        AggregationMethod method = question.aggregation() != null ? question.aggregation() : settings.defaultMethod();

        List<NormalizedInput> usable = new ArrayList<>();
        List<ScopedResponse> usableSources = new ArrayList<>();
        for (ScopedResponse answer : answers) {
            normalization.normalizeAnswer(answer.asInput(), configuration).ifPresent(normalized -> {
                usable.add(normalized);
                usableSources.add(answer);
            });
        }

        Integer respondents;
        ResponseSummary.Participation participation;
        if (scope == ResponseScope.MEMBER) {
            respondents = usable.size();
            participation = ResponseSummary.Participation.OBSERVED;
        } else {
            respondents = declaredTotal(usableSources);
            participation = respondents == null ? ResponseSummary.Participation.UNKNOWN : ResponseSummary.Participation.DECLARED;
        }
        boolean belowMinimum = respondents != null && respondents < settings.minResponses();
        if (usable.isEmpty() || belowMinimum) {
            ResponseSummary summary = new ResponseSummary(question.questionId(), scope, method, respondents, participation,
                    usable.size(), settings.minResponses(), belowMinimum);
            return new Combined(normalization.missing(question), summary);
        }
        NormalizedInput input = usable.size() == 1 ? usable.getFirst() : merge(question, usable, method, settings);
        ResponseSummary summary = new ResponseSummary(question.questionId(), scope, method, respondents, participation,
                usable.size(), settings.minResponses(), false);
        return new Combined(input, summary);
    }

    private static ResponseScope singleScope(String questionId, List<ScopedResponse> answers) {
        Set<ResponseScope> scopes = EnumSet.noneOf(ResponseScope.class);
        answers.forEach(a -> scopes.add(a.scope()));
        if (scopes.size() > 1) {
            throw new ScoringException(ScoringErrorCode.MIXED_RESPONSE_SCOPES,
                    "question " + questionId + " has answers of more than one scope", Map.of("questionId", questionId));
        }
        return scopes.iterator().next();
    }

    private static void rejectDuplicates(String questionId, ResponseScope scope, List<ScopedResponse> answers) {
        if (scope == ResponseScope.ORGANIZATION && answers.size() > 1) {
            throw new ScoringException(ScoringErrorCode.DUPLICATE_QUESTION_INPUT,
                    "more than one answer for question " + questionId, Map.of("questionId", questionId));
        }
        if (scope == ResponseScope.TEAM) {
            Set<String> teams = new HashSet<>();
            for (ScopedResponse answer : answers) {
                if (!teams.add(answer.teamId())) {
                    throw new ScoringException(ScoringErrorCode.DUPLICATE_TEAM_SCOPE,
                            "more than one team answer for question " + questionId + " and one team", Map.of("questionId", questionId));
                }
            }
        }
    }

    /** The sum of declared counts, or null when any usable figure does not declare one. */
    private static Integer declaredTotal(List<ScopedResponse> sources) {
        if (sources.isEmpty()) {
            return null;
        }
        long total = 0;
        for (ScopedResponse source : sources) {
            if (source.declaredRespondents() == null) {
                return null;
            }
            total += source.declaredRespondents();
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    private static NormalizedInput merge(QuestionDefinition question, List<NormalizedInput> usable, AggregationMethod method,
                                         ResponseSettings settings) {
        List<BigDecimal> values = new ArrayList<>();
        Map<String, Integer> signalCounts = new TreeMap<>();
        boolean clamped = false;
        for (NormalizedInput input : usable) {
            input.value().ifPresent(v -> values.add(v.value()));
            input.detectedSignals().forEach(s -> signalCounts.merge(s, 1, Integer::sum));
            clamped |= input.clamped();
        }
        BigDecimal threshold = settings.signalShare().multiply(BigDecimal.valueOf(usable.size()));
        Set<String> signals = new TreeSet<>();
        signalCounts.forEach((signal, count) -> {
            if (BigDecimal.valueOf(count).compareTo(threshold) >= 0) {
                signals.add(signal);
            }
        });
        NormalizedInput first = usable.getFirst();
        if (values.isEmpty()) {
            return new NormalizedInput(question.questionId(), question.type(), null, signals, NormalizationStatus.SIGNALS_ONLY,
                    first.strategyId(), first.strategyVersion(), false);
        }
        BigDecimal combined = method == AggregationMethod.MEAN ? mean(values) : median(values);
        return new NormalizedInput(question.questionId(), question.type(), new NormalizedValue(combined), signals,
                NormalizationStatus.NORMALIZED, first.strategyId(), first.strategyVersion(), clamped);
    }

    static BigDecimal mean(List<BigDecimal> values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(value);
        }
        return Decimals.divide(sum, BigDecimal.valueOf(values.size()));
    }

    static BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return Decimals.divide(sorted.get(size / 2 - 1).add(sorted.get(size / 2)), BigDecimal.valueOf(2));
    }
}
