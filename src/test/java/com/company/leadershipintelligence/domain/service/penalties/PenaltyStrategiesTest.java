package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.enums.CalculationOutcome;
import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.GapDirection;
import com.company.leadershipintelligence.domain.enums.PenaltyReason;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.rule.Condition;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;
import com.company.leadershipintelligence.domain.rule.ScoreBase;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.rule.ThresholdBand;
import com.company.leadershipintelligence.domain.service.patterns.ConditionEvaluator;
import com.company.leadershipintelligence.domain.strategy.PenaltyCalculation;
import com.company.leadershipintelligence.domain.strategy.PenaltyContext;
import com.company.leadershipintelligence.domain.strategy.PenaltyStrategy;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.cmp;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.dim;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.insufficient;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.score;
import static com.company.leadershipintelligence.domain.service.penalties.PenaltyTestSupport.matched;
import static com.company.leadershipintelligence.domain.service.penalties.PenaltyTestSupport.pen;
import static org.assertj.core.api.Assertions.assertThat;

class PenaltyStrategiesTest {

    private final PenaltyStrategy fixed = new FixedPenaltyStrategy();
    private final PenaltyStrategy proportional = new ProportionalPenaltyStrategy();
    private final PenaltyStrategy severity = new SeverityBasedPenaltyStrategy();
    private final PenaltyStrategy threshold = new ThresholdBasedPenaltyStrategy();
    private final PenaltyStrategy conditional =
            new ConditionalPenaltyStrategy(new ConditionEvaluator(), List.of(fixed, proportional, severity, threshold));

    private static EvaluationContext dims(String team, String delivery, String technical) {
        List<com.company.leadershipintelligence.domain.model.DimensionScore> list = new java.util.ArrayList<>();
        list.add(team == null ? insufficient(ScoringDimension.TEAM_HEALTH) : score(ScoringDimension.TEAM_HEALTH, team));
        list.add(delivery == null ? insufficient(ScoringDimension.DELIVERY_STABILITY) : score(ScoringDimension.DELIVERY_STABILITY, delivery));
        list.add(technical == null ? insufficient(ScoringDimension.TECHNICAL_RISK) : score(ScoringDimension.TECHNICAL_RISK, technical));
        return EvaluationContext.of(List.of(), list, List.of());
    }

    private PenaltyCalculation run(PenaltyStrategy strategy, StrategyConfig config, Severity sev, EvaluationContext context) {
        PenaltyDefinition definition = pen().strategy(config).build();
        return strategy.calculate(new PenaltyContext(definition, matched("TH-001", sev, "0.9", true), context), config);
    }

    // ---- FIXED ----
    @Test
    void fixedDeductsTheConfiguredAmount() {
        PenaltyCalculation c = run(fixed, new StrategyConfig.Fixed(new PenaltyAmount(d("8"))), Severity.LOW, dims("60", "60", "60"));
        assertThat(c.outcome()).isEqualTo(CalculationOutcome.CALCULATED);
        assertThat(c.amount()).isEqualByComparingTo("8");
    }

    // ---- PROPORTIONAL ----
    @Test
    void proportionalUsesTheExplicitRawDimensionBase() {
        StrategyConfig config = new StrategyConfig.Proportional(new ScoreBase.RawDimension(ScoringDimension.TEAM_HEALTH), d("0.1"));
        PenaltyCalculation c = run(proportional, config, Severity.HIGH, dims("60", "10", "10"));
        assertThat(c.amount()).isEqualByComparingTo("6");
        assertThat(c.scoreBase()).isEqualTo("raw TEAM_HEALTH");
        assertThat(c.scoreBaseValue()).isEqualByComparingTo("60");
    }

    @Test
    void proportionalCanUseADimensionGap() {
        StrategyConfig config = new StrategyConfig.Proportional(
                new ScoreBase.DimensionGap(ScoringDimension.DELIVERY_STABILITY, ScoringDimension.TECHNICAL_RISK, GapDirection.LEFT_MINUS_RIGHT), d("0.5"));
        assertThat(run(proportional, config, Severity.HIGH, dims("1", "79", "34")).amount()).isEqualByComparingTo("22.5");
    }

    @Test
    void proportionalWithAMissingBaseIsMissingDataNotZero() {
        StrategyConfig config = new StrategyConfig.Proportional(new ScoreBase.RawDimension(ScoringDimension.TEAM_HEALTH), d("0.1"));
        PenaltyCalculation c = run(proportional, config, Severity.HIGH, dims(null, "10", "10"));
        assertThat(c.outcome()).isEqualTo(CalculationOutcome.MISSING_DATA);
        assertThat(c.reason()).isEqualTo(PenaltyReason.MISSING_SCORE_BASE);
        assertThat(c.amount()).isNull();
    }

    @Test
    void proportionalRateMustBeBetweenZeroAndOneWithAnExplicitBase() {
        assertCode(() -> new StrategyConfig.Proportional(new ScoreBase.RawDimension(ScoringDimension.TEAM_HEALTH), d("1.01")),
                ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> new StrategyConfig.Proportional(new ScoreBase.RawDimension(ScoringDimension.TEAM_HEALTH), d("-0.1")),
                ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> new StrategyConfig.Proportional(null, d("0.1")), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> new ScoreBase.DimensionGap(ScoringDimension.TEAM_HEALTH, ScoringDimension.TEAM_HEALTH, GapDirection.ABSOLUTE),
                ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
    }

    // ---- SEVERITY_BASED (specv3.md Section 15.4 example: base 12) ----
    private final StrategyConfig th01 = new StrategyConfig.SeverityBased(new PenaltyAmount(d("12")),
            Map.of(Severity.LOW, d("0"), Severity.MEDIUM, d("0.4"), Severity.HIGH, d("0.7"), Severity.CRITICAL, d("1.0")));

    @Test
    void severityMultipliersScaleTheBaseDeduction() {
        assertThat(run(severity, th01, Severity.LOW, dims("1", "1", "1")).amount()).isEqualByComparingTo("0");
        assertThat(run(severity, th01, Severity.MEDIUM, dims("1", "1", "1")).amount()).isEqualByComparingTo("4.8");
        assertThat(run(severity, th01, Severity.HIGH, dims("1", "1", "1")).amount()).isEqualByComparingTo("8.4");
        assertThat(run(severity, th01, Severity.CRITICAL, dims("1", "1", "1")).amount()).isEqualByComparingTo("12");
    }

    @Test
    void severityBasedNeedsAMultiplierForEverySeverity() {
        assertCode(() -> new StrategyConfig.SeverityBased(new PenaltyAmount(d("12")),
                Map.of(Severity.LOW, d("0"), Severity.HIGH, d("0.7"))), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> new StrategyConfig.SeverityBased(new PenaltyAmount(d("12")),
                Map.of(Severity.LOW, d("-1"), Severity.MEDIUM, d("0"), Severity.HIGH, d("0"), Severity.CRITICAL, d("0"))),
                ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
    }

    // ---- THRESHOLD_BASED ----
    private final ScoreBase gapBase = new ScoreBase.DimensionGap(ScoringDimension.DELIVERY_STABILITY,
            ScoringDimension.TECHNICAL_RISK, GapDirection.LEFT_MINUS_RIGHT);

    private StrategyConfig schedule() {
        return new StrategyConfig.ThresholdBased(gapBase, List.of(
                new ThresholdBand(d("20"), true, null, false, new PenaltyAmount(d("6"))),
                new ThresholdBand(d("10"), true, d("20"), false, new PenaltyAmount(d("3")))));
    }

    @Test
    void thresholdBoundariesFollowTheirInclusivity() {
        // metric = delivery - technical
        assertThat(run(threshold, schedule(), Severity.HIGH, dims("1", "59.99", "50")).outcome()).isEqualTo(CalculationOutcome.NO_DEDUCTION);
        assertThat(run(threshold, schedule(), Severity.HIGH, dims("1", "60", "50")).amount()).isEqualByComparingTo("3");
        assertThat(run(threshold, schedule(), Severity.HIGH, dims("1", "69.99", "50")).amount()).isEqualByComparingTo("3");
        assertThat(run(threshold, schedule(), Severity.HIGH, dims("1", "70", "50")).amount()).isEqualByComparingTo("6");
        assertThat(run(threshold, schedule(), Severity.HIGH, dims("1", "100", "0")).amount()).isEqualByComparingTo("6");
    }

    @Test
    void exclusiveLowerBoundDoesNotIncludeTheBoundaryValue() {
        StrategyConfig exclusive = new StrategyConfig.ThresholdBased(gapBase, List.of(
                new ThresholdBand(d("10"), false, null, false, new PenaltyAmount(d("3")))));
        assertThat(run(threshold, exclusive, Severity.HIGH, dims("1", "60", "50")).outcome()).isEqualTo(CalculationOutcome.NO_DEDUCTION);
        assertThat(run(threshold, exclusive, Severity.HIGH, dims("1", "60.01", "50")).amount()).isEqualByComparingTo("3");
        assertThat(run(threshold, exclusive, Severity.HIGH, dims("1", "60", "50")).reason()).isEqualTo(PenaltyReason.BELOW_THRESHOLD);
    }

    @Test
    void thresholdWithAMissingMetricIsMissingData() {
        PenaltyCalculation c = run(threshold, schedule(), Severity.HIGH, dims("1", null, "50"));
        assertThat(c.outcome()).isEqualTo(CalculationOutcome.MISSING_DATA);
        assertThat(c.reason()).isEqualTo(PenaltyReason.MISSING_SCORE_BASE);
    }

    @Test
    void overlappingOrReversedBandsAreRejected() {
        assertCode(() -> new StrategyConfig.ThresholdBased(gapBase, List.of(
                new ThresholdBand(d("10"), true, d("20"), true, new PenaltyAmount(d("1"))),
                new ThresholdBand(d("20"), true, d("30"), true, new PenaltyAmount(d("2"))))), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> new StrategyConfig.ThresholdBased(gapBase, List.of(
                new ThresholdBand(d("10"), true, null, false, new PenaltyAmount(d("1"))),
                new ThresholdBand(d("30"), true, d("40"), false, new PenaltyAmount(d("2"))))), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> new ThresholdBand(d("20"), true, d("10"), false, new PenaltyAmount(d("1"))), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> new ThresholdBand(d("10"), true, d("10"), false, new PenaltyAmount(d("1"))), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> new ThresholdBand(d("10"), true, null, true, new PenaltyAmount(d("1"))), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        // adjacent bands that meet at an exclusive/inclusive boundary are fine
        assertThat(new StrategyConfig.ThresholdBased(gapBase, List.of(
                new ThresholdBand(d("10"), true, d("20"), false, new PenaltyAmount(d("1"))),
                new ThresholdBand(d("20"), true, d("30"), true, new PenaltyAmount(d("2")))))).isNotNull();
    }

    // ---- CONDITIONAL ----
    private StrategyConfig whenTechnicalLow() {
        return new StrategyConfig.Conditional(cmp(dim(ScoringDimension.TECHNICAL_RISK), ConditionOperator.LT, "50"),
                new StrategyConfig.Fixed(new PenaltyAmount(d("5"))));
    }

    @Test
    void conditionalDelegatesOnlyWhenTheConditionHolds() {
        assertThat(run(conditional, whenTechnicalLow(), Severity.HIGH, dims("1", "1", "49")).amount()).isEqualByComparingTo("5");
        PenaltyCalculation no = run(conditional, whenTechnicalLow(), Severity.HIGH, dims("1", "1", "50"));
        assertThat(no.outcome()).isEqualTo(CalculationOutcome.NO_DEDUCTION);
        assertThat(no.reason()).isEqualTo(PenaltyReason.CONDITION_NOT_SATISFIED);
    }

    @Test
    void conditionalWithMissingDataIsMissingDataNotFalse() {
        PenaltyCalculation c = run(conditional, whenTechnicalLow(), Severity.HIGH, dims("1", "1", null));
        assertThat(c.outcome()).isEqualTo(CalculationOutcome.MISSING_DATA);
        assertThat(c.reason()).isEqualTo(PenaltyReason.MISSING_SIGNALS);
    }

    @Test
    void conditionalCannotNest() {
        assertCode(() -> new StrategyConfig.Conditional(cmp(dim(ScoringDimension.TECHNICAL_RISK), ConditionOperator.LT, "50"), whenTechnicalLow()),
                ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertThat(new Condition.Not(cmp(dim(ScoringDimension.TEAM_HEALTH), ConditionOperator.LT, "5"))).isNotNull();
        assertThat(BigDecimal.ONE).isEqualByComparingTo("1");
    }
}
