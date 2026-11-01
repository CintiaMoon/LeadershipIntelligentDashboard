package com.company.leadershipintelligence.domain.service.patterns;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.CoverageStatus;
import com.company.leadershipintelligence.domain.enums.DimensionStatus;
import com.company.leadershipintelligence.domain.enums.NormalizationStatus;
import com.company.leadershipintelligence.domain.enums.PatternCategory;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.model.DerivedSignal;
import com.company.leadershipintelligence.domain.model.DimensionCoverage;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.model.NormalizedInput;
import com.company.leadershipintelligence.domain.rule.Condition;
import com.company.leadershipintelligence.domain.rule.EvidenceRequirements;
import com.company.leadershipintelligence.domain.rule.Operand;
import com.company.leadershipintelligence.domain.rule.PatternDefinition;
import com.company.leadershipintelligence.domain.valueobject.ConfidenceValue;
import com.company.leadershipintelligence.domain.valueobject.ScoreValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.Fixtures.nv;

/** Builders for pattern tests. All data is illustrative. */
public final class PatternTestSupport {

    private PatternTestSupport() {
    }

    public static Operand.SignalOperand sig(String id) {
        return new Operand.SignalOperand(id);
    }

    public static Operand.DimensionOperand dim(ScoringDimension dimension) {
        return new Operand.DimensionOperand(dimension);
    }

    public static Condition cmp(Operand operand, ConditionOperator operator, String value) {
        return new Condition.NumericComparison(operand, operator, d(value));
    }

    public static DerivedSignal num(String id, String value) {
        return num(id, value, null, "1");
    }

    public static DerivedSignal num(String id, String value, String group, String reliability) {
        return DerivedSignal.number(id, nv(value), Set.of("Q1"), group, "1", d(reliability));
    }

    public static DerivedSignal flag(String id, boolean value) {
        return DerivedSignal.flag(id, value, Set.of("Q4"), null, "1", BigDecimal.ONE);
    }

    public static DimensionScore score(ScoringDimension dimension, String value) {
        DimensionCoverage coverage = new DimensionCoverage(1, 1, 1, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                d("0.75"), CoverageStatus.SUFFICIENT);
        return new DimensionScore(dimension, DimensionStatus.VALID, ScoreValue.of(value), coverage, List.of());
    }

    public static DimensionScore insufficient(ScoringDimension dimension) {
        DimensionCoverage coverage = new DimensionCoverage(1, 0, 0, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                d("0.75"), CoverageStatus.INSUFFICIENT);
        return new DimensionScore(dimension, DimensionStatus.INSUFFICIENT_DATA, null, coverage, List.of());
    }

    public static NormalizedInput textFlags(String questionId, String... signalIds) {
        return new NormalizedInput(questionId, QuestionType.TEXT, null, Set.of(signalIds),
                NormalizationStatus.SIGNALS_ONLY, "text", "1");
    }

    public static EvaluationContext ctx(List<DerivedSignal> signals, List<DimensionScore> scores, List<NormalizedInput> inputs) {
        return EvaluationContext.of(signals, scores, inputs);
    }

    public static EvaluationContext ctx(DerivedSignal... signals) {
        return ctx(List.of(signals), List.of(), List.of());
    }

    public static PatternDefinition pattern(String id, int version, Severity severity, Condition condition,
                                            int minSignals, String minConfidence, boolean enabled, boolean penaltyEligible) {
        return new PatternDefinition(id, id + "_CODE", version, PatternCategory.TEAM_HEALTH, severity, enabled, condition,
                new EvidenceRequirements(minSignals, new ConfidenceValue(d(minConfidence))), penaltyEligible,
                List.of("REVIEW_" + id));
    }

    /** V3 Section 13.4 TH-001 example: workload >= 75 AND sustainability <= 35; two signals, confidence 0.70. */
    public static PatternDefinition th001() {
        return pattern("TH-001", 1, Severity.CRITICAL, new Condition.And(List.of(
                cmp(sig("WORKLOAD_PRESSURE"), ConditionOperator.GTE, "75"),
                cmp(sig("TEAM_SUSTAINABILITY"), ConditionOperator.LTE, "35"))), 2, "0.70", true, true);
    }
}
