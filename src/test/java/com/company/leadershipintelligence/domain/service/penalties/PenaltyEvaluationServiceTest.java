package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.enums.EvaluationOutcome;
import com.company.leadershipintelligence.domain.enums.PatternReason;
import com.company.leadershipintelligence.domain.enums.PenaltyReason;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.model.PatternEvaluation;
import com.company.leadershipintelligence.domain.model.PenaltyEvaluation;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;
import com.company.leadershipintelligence.domain.rule.ScoreBase;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.service.patterns.ConditionEvaluator;
import com.company.leadershipintelligence.domain.strategy.PenaltyStrategy;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.score;
import static com.company.leadershipintelligence.domain.service.penalties.PenaltyTestSupport.ineligible;
import static com.company.leadershipintelligence.domain.service.penalties.PenaltyTestSupport.matched;
import static com.company.leadershipintelligence.domain.service.penalties.PenaltyTestSupport.notEvaluated;
import static com.company.leadershipintelligence.domain.service.penalties.PenaltyTestSupport.notMatched;
import static com.company.leadershipintelligence.domain.service.penalties.PenaltyTestSupport.pen;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PenaltyEvaluationServiceTest {

    private static List<PenaltyStrategy> strategies() {
        PenaltyStrategy fixed = new FixedPenaltyStrategy();
        PenaltyStrategy proportional = new ProportionalPenaltyStrategy();
        PenaltyStrategy severity = new SeverityBasedPenaltyStrategy();
        PenaltyStrategy threshold = new ThresholdBasedPenaltyStrategy();
        return List.of(fixed, proportional, severity, threshold,
                new ConditionalPenaltyStrategy(new ConditionEvaluator(), List.of(fixed, proportional, severity, threshold)));
    }

    private final PenaltyEvaluationService service = new PenaltyEvaluationService(new PenaltyEligibilityService(), strategies());
    private final EvaluationContext context = EvaluationContext.of(List.of(),
            List.of(score(ScoringDimension.TEAM_HEALTH, "55.55")), List.of());

    private PenaltyEvaluation one(PenaltyDefinition definition, PatternEvaluation pattern) {
        return service.evaluate(List.of(definition), pattern == null ? List.of() : List.of(pattern), context).get(0);
    }

    @Test
    void matchedEligiblePatternGivesACandidateWithEvidenceReferences() {
        PenaltyEvaluation e = one(pen().build(), matched("TH-001", Severity.HIGH, "0.9", true));
        assertThat(e.outcome()).isEqualTo(EvaluationOutcome.CANDIDATE);
        assertThat(e.candidateDeduction().value()).isEqualByComparingTo("5");
        assertThat(e.evidenceRefs()).containsExactly("signal:WORKLOAD_PRESSURE");
        assertThat(e.triggerSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void individualMaximumCapsTheCandidate() {
        PenaltyDefinition fixed15 = pen().strategy(new StrategyConfig.Fixed(new PenaltyAmount(d("15")))).max("12").build();
        PenaltyEvaluation e = one(fixed15, matched("TH-001", Severity.HIGH, "0.9", true));
        assertThat(e.candidateDeduction().value()).isEqualByComparingTo("12");
        assertThat(e.calculatedDeduction()).isEqualByComparingTo("15");
    }

    @Test
    void candidateIsRoundedOnceToTwoDecimalsHalfUp() {
        PenaltyDefinition proportional = pen().strategy(new StrategyConfig.Proportional(
                new ScoreBase.RawDimension(ScoringDimension.TEAM_HEALTH), d("0.1"))).build();
        PenaltyEvaluation e = one(proportional, matched("TH-001", Severity.HIGH, "0.9", true));
        // 55.55 x 0.1 = 5.555 -> 5.56
        assertThat(e.candidateDeduction().value().toPlainString()).isEqualTo("5.56");
        assertThat(e.scoreBaseValue()).isEqualByComparingTo("55.55");
    }

    @Test
    void zeroDeductionIsNotTriggered() {
        PenaltyDefinition lowIsZero = pen().strategy(new StrategyConfig.SeverityBased(new PenaltyAmount(d("12")),
                Map.of(Severity.LOW, d("0"), Severity.MEDIUM, d("0.4"), Severity.HIGH, d("0.7"), Severity.CRITICAL, d("1")))).build();
        PenaltyEvaluation e = one(lowIsZero, matched("TH-001", Severity.LOW, "0.9", true));
        assertThat(e.outcome()).isEqualTo(EvaluationOutcome.NOT_TRIGGERED);
        assertThat(e.reason()).isEqualTo(PenaltyReason.ZERO_DEDUCTION);
        assertThat(e.candidateDeduction()).isNull();
    }

    @Test
    void severityBasedUsesTheTriggeringPatternsSeverity() {
        PenaltyDefinition severity = pen().strategy(new StrategyConfig.SeverityBased(new PenaltyAmount(d("12")),
                Map.of(Severity.LOW, d("0"), Severity.MEDIUM, d("0.4"), Severity.HIGH, d("0.7"), Severity.CRITICAL, d("1")))).build();
        assertThat(one(severity, matched("TH-001", Severity.HIGH, "0.9", true)).candidateDeduction().value()).isEqualByComparingTo("8.4");
        assertThat(one(severity, matched("TH-001", Severity.CRITICAL, "0.9", true)).candidateDeduction().value()).isEqualByComparingTo("12");
    }

    @Test
    void patternThatDidNotMatchDoesNotTrigger() {
        PenaltyEvaluation e = one(pen().build(), notMatched("TH-001"));
        assertThat(e.outcome()).isEqualTo(EvaluationOutcome.NOT_TRIGGERED);
        assertThat(e.reason()).isEqualTo(PenaltyReason.PATTERN_NOT_MATCHED);
    }

    @Test
    void patternThatCouldNotBeEvaluatedMakesThePenaltyIneligible() {
        PenaltyEvaluation e = one(pen().build(), notEvaluated("TH-001", PatternReason.MISSING_SIGNALS));
        assertThat(e.outcome()).isEqualTo(EvaluationOutcome.INELIGIBLE);
        assertThat(e.reason()).isEqualTo(PenaltyReason.PATTERN_NOT_EVALUATED);
    }

    @Test
    void disabledPatternDoesNotTrigger() {
        PenaltyEvaluation e = one(pen().build(), notEvaluated("TH-001", PatternReason.PATTERN_DISABLED));
        assertThat(e.outcome()).isEqualTo(EvaluationOutcome.NOT_TRIGGERED);
        assertThat(e.reason()).isEqualTo(PenaltyReason.PATTERN_DISABLED);
    }

    @Test
    void highSeverityWithInsufficientConfidenceNeverPenalizes() {
        PenaltyEvaluation e = one(pen().build(), ineligible("TH-001", Severity.CRITICAL));
        assertThat(e.outcome()).isEqualTo(EvaluationOutcome.INELIGIBLE);
        assertThat(e.reason()).isEqualTo(PenaltyReason.PATTERN_INELIGIBLE);
        assertThat(e.candidateDeduction()).isNull();
    }

    @Test
    void informationalPatternIsIneligibleForAPenalty() {
        PenaltyEvaluation e = one(pen().build(), matched("TH-001", Severity.HIGH, "0.9", false));
        assertThat(e.outcome()).isEqualTo(EvaluationOutcome.INELIGIBLE);
        assertThat(e.reason()).isEqualTo(PenaltyReason.PATTERN_NOT_PENALTY_ELIGIBLE);
    }

    @Test
    void penaltyCanDemandMoreConfidenceThanThePattern() {
        PenaltyDefinition strict = pen().minConfidence("0.8").build();
        assertThat(one(strict, matched("TH-001", Severity.HIGH, "0.79", true)).reason()).isEqualTo(PenaltyReason.CONFIDENCE_BELOW_PENALTY_MINIMUM);
        assertThat(one(strict, matched("TH-001", Severity.HIGH, "0.80", true)).outcome()).isEqualTo(EvaluationOutcome.CANDIDATE);
    }

    @Test
    void disabledPenaltyAndAbsentTriggerAreReported() {
        PenaltyEvaluation disabled = one(pen().enabled(false).build(), matched("TH-001", Severity.HIGH, "0.9", true));
        assertThat(disabled.outcome()).isEqualTo(EvaluationOutcome.NOT_TRIGGERED);
        assertThat(disabled.reason()).isEqualTo(PenaltyReason.PENALTY_DISABLED);
        PenaltyEvaluation absent = one(pen().build(), null);
        assertThat(absent.outcome()).isEqualTo(EvaluationOutcome.INELIGIBLE);
        assertThat(absent.reason()).isEqualTo(PenaltyReason.TRIGGER_PATTERN_ABSENT);
    }

    @Test
    void missingScoreBaseMakesThePenaltyIneligibleNotZero() {
        PenaltyDefinition onDelivery = pen().strategy(new StrategyConfig.Proportional(
                new ScoreBase.RawDimension(ScoringDimension.DELIVERY_STABILITY), d("0.1"))).build();
        PenaltyEvaluation e = one(onDelivery, matched("TH-001", Severity.HIGH, "0.9", true));
        assertThat(e.outcome()).isEqualTo(EvaluationOutcome.INELIGIBLE);
        assertThat(e.reason()).isEqualTo(PenaltyReason.MISSING_SCORE_BASE);
    }

    @Test
    void resultsAreOrderedByRuleOrderThenIdWhateverTheInputOrder() {
        PenaltyDefinition b = pen().id("B").order(1).build();
        PenaltyDefinition a = pen().id("A").order(1).build();
        PenaltyDefinition c = pen().id("C").order(0).build();
        List<PatternEvaluation> patterns = List.of(matched("TH-001", Severity.HIGH, "0.9", true));
        assertThat(service.evaluate(List.of(b, a, c), patterns, context)).extracting(PenaltyEvaluation::penaltyId)
                .containsExactly("C", "A", "B");
        assertThat(service.evaluate(List.of(c, b, a), patterns, context)).isEqualTo(service.evaluate(List.of(a, c, b), patterns, context));
    }

    @Test
    void registeringTwoStrategiesForOneTypeIsRejected() {
        assertThatThrownBy(() -> new PenaltyEvaluationService(new PenaltyEligibilityService(),
                List.of(new FixedPenaltyStrategy(), new FixedPenaltyStrategy()))).isInstanceOf(IllegalArgumentException.class);
    }
}
