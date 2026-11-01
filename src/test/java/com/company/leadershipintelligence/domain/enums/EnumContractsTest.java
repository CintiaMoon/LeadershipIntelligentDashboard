package com.company.leadershipintelligence.domain.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the closed sets the specification defines, so a change is a deliberate decision. */
class EnumContractsTest {

    @Test
    void dimensionsAreTheFourCanonicalOnes() {
        assertThat(names(ScoringDimension.values()))
                .containsExactly("TEAM_HEALTH", "DELIVERY_STABILITY", "TECHNICAL_RISK", "LEADERSHIP_LOAD");
    }

    @Test
    void penaltyHasExactlyFourTerminalStates() {
        assertThat(names(PenaltyStatus.values()))
                .containsExactly("APPLIED", "SUPPRESSED", "INELIGIBLE", "NOT_TRIGGERED");
    }

    @Test
    void patternHasExactlyFourStatuses() {
        assertThat(names(PatternStatus.values()))
                .containsExactly("MATCHED", "NOT_MATCHED", "INELIGIBLE", "NOT_EVALUATED");
    }

    @Test
    void penaltyStrategiesAreTheFiveSpecifiedOnes() {
        assertThat(names(PenaltyStrategyType.values()))
                .containsExactly("FIXED", "PROPORTIONAL", "SEVERITY_BASED", "THRESHOLD_BASED", "CONDITIONAL");
    }

    @Test
    void sixStackingPolicies() {
        assertThat(names(StackingPolicy.values())).containsExactly(
                "STACK", "MAX", "MAX_PER_CATEGORY", "PRIORITY", "CAPPED_STACK", "EXCLUSIVE_GROUP");
    }

    @Test
    void penaltyTargetsAreDimensionsPlusOverall() {
        assertThat(names(PenaltyTarget.values())).containsExactly(
                "TEAM_HEALTH", "DELIVERY_STABILITY", "TECHNICAL_RISK", "LEADERSHIP_LOAD", "OVERALL");
        for (ScoringDimension dimension : ScoringDimension.values()) {
            assertThat(PenaltyTarget.valueOf(dimension.name())).isNotNull();
        }
    }

    @Test
    void questionTypesAndMissingPolicies() {
        assertThat(names(QuestionType.values())).containsExactly("SCALE", "MULTIPLE_CHOICE", "MULTI_SELECT", "BOOLEAN", "NUMBER", "TEXT"); // the five of specv3 4.2 plus MULTI_SELECT (4.2.1, D49)
        assertThat(names(MissingDataPolicy.values())).containsExactly("IGNORE", "NEUTRAL", "RISK_SIGNAL", "REQUIRED");
    }

    @Test
    void executionStatusIsSeparateFromScoreValidity() {
        assertThat(names(RunStatus.values())).containsExactly("PENDING", "RUNNING", "COMPLETED", "FAILED");
        assertThat(names(ScoreStatus.values())).containsExactly("VALID", "INSUFFICIENT_DATA", "PARTIAL");
        assertThat(names(DimensionStatus.values())).containsExactly("VALID", "INSUFFICIENT_DATA");
    }

    @Test
    void severityAndConfidenceBandsAreOrdered() {
        assertThat(names(Severity.values())).containsExactly("LOW", "MEDIUM", "HIGH", "CRITICAL");
        assertThat(names(ConfidenceBand.values())).containsExactly("INSUFFICIENT", "WEAK", "MODERATE", "STRONG");
    }

    @Test
    void onlyValueCarryingStatusesMayHaveAValue() {
        assertThat(NormalizationStatus.NORMALIZED.carriesValue()).isTrue();
        assertThat(NormalizationStatus.NEUTRAL_APPLIED.carriesValue()).isTrue();
        assertThat(NormalizationStatus.MISSING_IGNORED.carriesValue()).isFalse();
        assertThat(NormalizationStatus.MISSING_RISK_SIGNAL.carriesValue()).isFalse();
        assertThat(NormalizationStatus.SIGNALS_ONLY.carriesValue()).isFalse();
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
