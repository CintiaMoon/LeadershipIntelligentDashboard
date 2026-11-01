package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.PenaltyTarget;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.enums.StackingPolicy;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.rule.DimensionScoringConfig;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;
import com.company.leadershipintelligence.domain.rule.SignalDefinition;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.Fixtures.numberSignal;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.Fixtures.scale15;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.cmp;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.sig;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.th001;
import static com.company.leadershipintelligence.domain.service.penalties.PenaltyTestSupport.pen;
import static org.assertj.core.api.Assertions.assertThat;

class PenaltyConfigurationTest {

    private final Map<String, QuestionDefinition> questions = Map.of(
            "Q1", question("Q1", scale15(ScaleDirection.POSITIVE), MissingDataPolicy.IGNORE),
            "Q2", question("Q2", scale15(ScaleDirection.POSITIVE), MissingDataPolicy.IGNORE));
    private final Map<String, SignalDefinition> signals = Map.of(
            "WORKLOAD_PRESSURE", numberSignal("WORKLOAD_PRESSURE", "Q1"),
            "TEAM_SUSTAINABILITY", numberSignal("TEAM_SUSTAINABILITY", "Q2"));

    private ScoringConfigurationSnapshot snapshot(PenaltyDefinition... penalties) {
        return new ScoringConfigurationSnapshot(new ConfigurationVersion("t"), questions, signals,
                DimensionScoringConfig.none(), List.of(th001()), List.of(penalties));
    }

    @Test
    void validPenaltiesAreAcceptedAndSortedByRuleOrder() {
        ScoringConfigurationSnapshot s = snapshot(pen().id("B").order(2).build(), pen().id("A").order(1).build());
        assertThat(s.penalties()).extracting(PenaltyDefinition::penaltyId).containsExactly("A", "B");
    }

    @Test
    void cumulativeCapIsRequiredForCappedStackAndForbiddenOtherwise() {
        assertCode(() -> pen().policy(StackingPolicy.CAPPED_STACK).build(), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> pen().policy(StackingPolicy.STACK).cap("10").build(), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertThat(pen().policy(StackingPolicy.CAPPED_STACK).cap("10").build().maximumCumulativeDeduction().value()).isEqualByComparingTo("10");
    }

    @Test
    void exclusiveGroupIsRequiredForExclusiveGroupAndForbiddenOtherwise() {
        assertCode(() -> pen().policy(StackingPolicy.EXCLUSIVE_GROUP).build(), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> pen().policy(StackingPolicy.MAX).group("g").build(), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
    }

    @Test
    void definitionNeedsCategoryTriggerAndNonNegativeNumbers() {
        assertCode(() -> pen().category(" ").build(), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> pen().trigger("").build(), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> pen().priority(-1).build(), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> pen().order(-1).build(), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> pen().rationale(" ").build(), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
    }

    @Test
    void snapshotRejectsUnknownTriggerAndDuplicates() {
        assertCode(() -> snapshot(pen().trigger("NOPE").build()), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> snapshot(pen().id("A").build(), pen().id("A").build()), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
    }

    @Test
    void cappedStackPenaltiesOnOneTargetMustShareTheCap() {
        assertCode(() -> snapshot(
                pen().id("A").policy(StackingPolicy.CAPPED_STACK).cap("10").build(),
                pen().id("B").policy(StackingPolicy.CAPPED_STACK).cap("12").build()), ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertThat(snapshot(
                pen().id("A").policy(StackingPolicy.CAPPED_STACK).cap("10").rationale("reviewed").build(),
                pen().id("B").policy(StackingPolicy.CAPPED_STACK).cap("10.00").rationale("reviewed").build(),
                pen().id("C").target(PenaltyTarget.OVERALL).policy(StackingPolicy.CAPPED_STACK).cap("3").rationale("reviewed").build(),
                pen().id("D").target(PenaltyTarget.TECHNICAL_RISK).policy(StackingPolicy.CAPPED_STACK).cap("7").rationale("reviewed").build()).penalties())
                .hasSize(4);
    }

    @Test
    void samePatternOnADimensionAndOverallNeedsADocumentedRationale() {
        assertCode(() -> snapshot(pen().id("A").build(), pen().id("B").target(PenaltyTarget.OVERALL).build()),
                ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertCode(() -> snapshot(pen().id("A").rationale("reviewed").build(), pen().id("B").target(PenaltyTarget.OVERALL).build()),
                ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertThat(snapshot(pen().id("A").rationale("reviewed 2026-10").build(),
                pen().id("B").target(PenaltyTarget.OVERALL).rationale("reviewed 2026-10").build()).penalties()).hasSize(2);
    }

    @Test
    void conditionalConditionIsValidatedAgainstTheRegistry() {
        PenaltyDefinition unknown = pen().strategy(new StrategyConfig.Conditional(
                cmp(sig("NOPE"), ConditionOperator.GT, "10"), new StrategyConfig.Fixed(new PenaltyAmount(d("5"))))).build();
        assertCode(() -> snapshot(unknown), ScoringErrorCode.INVALID_CONDITION_EXPRESSION);
        PenaltyDefinition known = pen().strategy(new StrategyConfig.Conditional(
                cmp(sig("WORKLOAD_PRESSURE"), ConditionOperator.GT, "10"), new StrategyConfig.Fixed(new PenaltyAmount(d("5"))))).build();
        assertThat(snapshot(known).penalties()).hasSize(1);
    }
}
