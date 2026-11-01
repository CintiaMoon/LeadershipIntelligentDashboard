package com.company.leadershipintelligence.domain.service.patterns;

import com.company.leadershipintelligence.domain.enums.ConfidenceBand;
import com.company.leadershipintelligence.domain.enums.EvidenceKind;
import com.company.leadershipintelligence.domain.model.ConfidenceBreakdown;
import com.company.leadershipintelligence.domain.model.DerivedSignal;
import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.model.ParticipationInfo;
import com.company.leadershipintelligence.domain.model.PatternEvidence;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.rule.Operand;
import com.company.leadershipintelligence.domain.rule.PatternDefinition;
import com.company.leadershipintelligence.domain.valueobject.ConfidenceValue;
import com.company.leadershipintelligence.domain.valueobject.Decimals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Evidence and confidence (decision D17, algorithm version 1, unvalidated):
 * {@code confidence = coverage x independence x reliability x freshness}, rounded to 2 decimals.
 * Supporting evidence is every operand of the condition that has data. coverage and independence divide by
 * {@code minimumSignals} and are capped at 1; independence counts distinct correlation groups, so two
 * questions measuring one phenomenon count once. Confidence measures evidence quality and says nothing
 * about severity.
 */
public class PatternConfidenceService {

    public static final String ALGORITHM_VERSION = "1";
    /** Version 2 multiplies in the participation factor (specv3.md Section 14.2.1, decision D45, unvalidated). */
    public static final String ALGORITHM_VERSION_PARTICIPATION = "2";

    private final ConfidenceBandConfig bands;

    public PatternConfidenceService(ConfidenceBandConfig bands) {
        this.bands = bands;
    }

    public record Assessment(List<PatternEvidence> evidence, ConfidenceBreakdown breakdown,
                             ConfidenceValue confidence, ConfidenceBand band) {
    }

    public Assessment assess(PatternDefinition pattern, EvaluationContext context) {
        List<PatternEvidence> evidence = new ArrayList<>();
        for (Operand operand : pattern.condition().operands()) {
            evidenceFor(operand, context).ifPresent(evidence::add);
        }
        int minimum = pattern.evidence().minimumSignals();
        Set<String> groups = new HashSet<>();
        BigDecimal reliabilitySum = BigDecimal.ZERO;
        for (PatternEvidence item : evidence) {
            groups.add(item.correlationGroup());
            reliabilitySum = reliabilitySum.add(item.reliability());
        }
        BigDecimal minimumSignals = BigDecimal.valueOf(minimum);
        BigDecimal coverage = cap(Decimals.divide(BigDecimal.valueOf(evidence.size()), minimumSignals));
        BigDecimal independence = cap(Decimals.divide(BigDecimal.valueOf(groups.size()), minimumSignals));
        BigDecimal reliability = evidence.isEmpty()
                ? BigDecimal.ZERO
                : Decimals.divide(reliabilitySum, BigDecimal.valueOf(evidence.size()));
        BigDecimal freshness = BigDecimal.ONE;

        BigDecimal product = coverage.multiply(independence).multiply(reliability).multiply(freshness);
        ConfidenceBreakdown breakdown;
        if (context.participation().isPresent()) {
            ParticipationInfo info = context.participation().get();
            Integer respondents = lowestRespondents(evidence, info);
            BigDecimal participation = respondents == null
                    ? BigDecimal.ONE
                    : cap(Decimals.divide(BigDecimal.valueOf(respondents), BigDecimal.valueOf(info.fullConfidenceResponses())));
            product = product.multiply(participation);
            breakdown = new ConfidenceBreakdown(coverage, independence, reliability, freshness, false,
                    evidence.size(), groups.size(), ALGORITHM_VERSION_PARTICIPATION, participation, respondents);
        } else {
            breakdown = new ConfidenceBreakdown(coverage, independence, reliability, freshness,
                    false, evidence.size(), groups.size(), ALGORITHM_VERSION);
        }
        ConfidenceValue confidence = new ConfidenceValue(Decimals.roundScore(product));
        return new Assessment(List.copyOf(evidence), breakdown, confidence, bands.bandFor(confidence.value()));
    }

    /** The version a run records: 2 when its answers carry participation, otherwise 1. */
    public static String versionFor(boolean withParticipation) {
        return withParticipation ? ALGORITHM_VERSION_PARTICIPATION : ALGORITHM_VERSION;
    }

    /**
     * The lowest known respondent count among the questions behind the evidence. Evidence that names no question
     * (a dimension score) uses the lowest count in the unit, the conservative choice. Null when nothing is known.
     */
    private static Integer lowestRespondents(List<PatternEvidence> evidence, ParticipationInfo info) {
        Set<String> questions = new HashSet<>();
        boolean unit = false;
        for (PatternEvidence item : evidence) {
            if (item.sourceQuestionIds().isEmpty()) {
                unit = true;
            } else {
                questions.addAll(item.sourceQuestionIds());
            }
        }
        OptionalInt lowest = info.lowestAmong(questions);
        if (unit) {
            int all = info.lowest();
            return lowest.isPresent() ? Math.min(all, lowest.getAsInt()) : all;
        }
        return lowest.isPresent() ? lowest.getAsInt() : null;
    }

    private static BigDecimal cap(BigDecimal ratio) {
        return ratio.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : ratio;
    }

    private static Optional<PatternEvidence> evidenceFor(Operand operand, EvaluationContext context) {
        return switch (operand) {
            case Operand.SignalOperand s -> context.signal(s.signalId()).map(signal -> signalEvidence(s, signal));
            case Operand.DimensionOperand d -> context.rawDimension(d.dimension()).map(score -> new PatternEvidence(
                    d.key(), EvidenceKind.DIMENSION, score.value(), null, Set.of(), d.key(), BigDecimal.ONE));
            case Operand.QuestionSignalsOperand q -> context.questionSignals(q.questionId()).map(flags -> new PatternEvidence(
                    q.key(), EvidenceKind.QUESTION_SIGNALS, null, null, Set.of(q.questionId()), q.key(), BigDecimal.ONE));
        };
    }

    private static PatternEvidence signalEvidence(Operand.SignalOperand operand, DerivedSignal signal) {
        String group = signal.correlationGroup() != null ? signal.correlationGroup() : operand.key();
        BigDecimal numeric = signal.numericValue() == null ? null : signal.numericValue().value();
        return new PatternEvidence(operand.key(), EvidenceKind.SIGNAL, numeric, signal.flag(),
                signal.sourceQuestionIds(), group, signal.reliability());
    }
}
