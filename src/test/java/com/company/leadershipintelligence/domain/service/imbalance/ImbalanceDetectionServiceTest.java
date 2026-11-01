package com.company.leadershipintelligence.domain.service.imbalance;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.EvaluationBasis;
import com.company.leadershipintelligence.domain.enums.GapDirection;
import com.company.leadershipintelligence.domain.enums.ImbalanceReason;
import com.company.leadershipintelligence.domain.enums.ImbalanceStatus;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.ImbalanceResult;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.rule.DimensionScoringConfig;
import com.company.leadershipintelligence.domain.rule.ImbalanceRule;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.insufficient;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.score;
import static org.assertj.core.api.Assertions.assertThat;

class ImbalanceDetectionServiceTest {

    private final ImbalanceDetectionService service = new ImbalanceDetectionService();

    private static ImbalanceRule rule(String id, ScoringDimension left, ScoringDimension right, GapDirection direction,
                                      ConditionOperator op, String threshold, boolean enabled) {
        return new ImbalanceRule(id, 1, left, right, direction, op, d(threshold), Severity.HIGH,
                EvaluationBasis.RAW_DIMENSION_SCORE, enabled, "explain." + id, "recommend." + id);
    }

    /** specv3.md Section 12.2: delivery vs technical, GTE 30, HIGH. */
    private static ImbalanceRule deliveryTechnical() {
        return rule("DELIVERY_TECHNICAL_GAP", ScoringDimension.DELIVERY_STABILITY, ScoringDimension.TECHNICAL_RISK,
                GapDirection.LEFT_MINUS_RIGHT, ConditionOperator.GAP_GTE, "30", true);
    }

    private static List<DimensionScore> scores(String team, String delivery, String technical, String leadership) {
        List<DimensionScore> list = new ArrayList<>();
        list.add(team == null ? insufficient(ScoringDimension.TEAM_HEALTH) : score(ScoringDimension.TEAM_HEALTH, team));
        list.add(delivery == null ? insufficient(ScoringDimension.DELIVERY_STABILITY) : score(ScoringDimension.DELIVERY_STABILITY, delivery));
        list.add(technical == null ? insufficient(ScoringDimension.TECHNICAL_RISK) : score(ScoringDimension.TECHNICAL_RISK, technical));
        list.add(leadership == null ? insufficient(ScoringDimension.LEADERSHIP_LOAD) : score(ScoringDimension.LEADERSHIP_LOAD, leadership));
        return list;
    }

    private ImbalanceResult one(ImbalanceRule rule, List<DimensionScore> scores) {
        return service.detect(List.of(rule), scores).get(0);
    }

    @Test
    void specExampleIsDetectedWithFullDetail() {
        // 82 / 79 / 34 / 38 -> delivery - technical = 45
        ImbalanceResult r = one(deliveryTechnical(), scores("82", "79", "34", "38"));
        assertThat(r.status()).isEqualTo(ImbalanceStatus.DETECTED);
        assertThat(r.reason()).isEqualTo(ImbalanceReason.THRESHOLD_MET);
        assertThat(r.gap()).isEqualByComparingTo("45");
        assertThat(r.leftValue().value()).isEqualByComparingTo("79");
        assertThat(r.rightValue().value()).isEqualByComparingTo("34");
        assertThat(r.threshold()).isEqualByComparingTo("30");
        assertThat(r.direction()).isEqualTo(GapDirection.LEFT_MINUS_RIGHT);
        assertThat(r.severity()).isEqualTo(Severity.HIGH);
        assertThat(r.basis()).isEqualTo(EvaluationBasis.RAW_DIMENSION_SCORE);
        assertThat(r.explanationKey()).isEqualTo("explain.DELIVERY_TECHNICAL_GAP");
        assertThat(r.evidenceRefs()).containsExactly("dimension:DELIVERY_STABILITY", "dimension:TECHNICAL_RISK");
    }

    @Test
    void noImbalanceWhenTheGapIsSmall() {
        ImbalanceResult r = one(deliveryTechnical(), scores("82", "76", "66", "70"));
        assertThat(r.status()).isEqualTo(ImbalanceStatus.NOT_DETECTED);
        assertThat(r.reason()).isEqualTo(ImbalanceReason.THRESHOLD_NOT_MET);
        assertThat(r.gap()).isEqualByComparingTo("10");
    }

    @Test
    void thresholdBoundaryIsExplicitForGteAndGt() {
        List<DimensionScore> exactly30 = scores("1", "79", "49", "1");
        assertThat(one(deliveryTechnical(), exactly30).status()).isEqualTo(ImbalanceStatus.DETECTED);
        ImbalanceRule strict = rule("STRICT", ScoringDimension.DELIVERY_STABILITY, ScoringDimension.TECHNICAL_RISK,
                GapDirection.LEFT_MINUS_RIGHT, ConditionOperator.GAP_GT, "30", true);
        assertThat(one(strict, exactly30).status()).isEqualTo(ImbalanceStatus.NOT_DETECTED);
        assertThat(one(strict, scores("1", "79.01", "49", "1")).status()).isEqualTo(ImbalanceStatus.DETECTED);
        assertThat(one(deliveryTechnical(), scores("1", "78.99", "49", "1")).status()).isEqualTo(ImbalanceStatus.NOT_DETECTED);
    }

    @Test
    void gapDirectionDecidesWhichWayAroundCounts() {
        List<DimensionScore> technicalAhead = scores("1", "40", "80", "1");
        assertThat(one(deliveryTechnical(), technicalAhead).status()).isEqualTo(ImbalanceStatus.NOT_DETECTED);
        assertThat(one(deliveryTechnical(), technicalAhead).gap()).isEqualByComparingTo("-40");
        ImbalanceRule reverse = rule("R", ScoringDimension.DELIVERY_STABILITY, ScoringDimension.TECHNICAL_RISK,
                GapDirection.RIGHT_MINUS_LEFT, ConditionOperator.GAP_GTE, "30", true);
        assertThat(one(reverse, technicalAhead).status()).isEqualTo(ImbalanceStatus.DETECTED);
        ImbalanceRule absolute = rule("A", ScoringDimension.DELIVERY_STABILITY, ScoringDimension.TECHNICAL_RISK,
                GapDirection.ABSOLUTE, ConditionOperator.GAP_GTE, "30", true);
        assertThat(one(absolute, technicalAhead).status()).isEqualTo(ImbalanceStatus.DETECTED);
        assertThat(one(absolute, scores("1", "79", "34", "1")).status()).isEqualTo(ImbalanceStatus.DETECTED);
    }

    @Test
    void missingDimensionIsNotEvaluatedAndNeverReadAsZero() {
        // if technical were read as zero, the gap would be 79 and the rule would fire
        ImbalanceResult r = one(deliveryTechnical(), scores("82", "79", null, "38"));
        assertThat(r.status()).isEqualTo(ImbalanceStatus.NOT_EVALUATED);
        assertThat(r.reason()).isEqualTo(ImbalanceReason.MISSING_DIMENSION);
        assertThat(r.gap()).isNull();
        assertThat(r.leftValue()).isNull();
        assertThat(r.rightValue()).isNull();
        assertThat(one(deliveryTechnical(), scores("82", null, null, "38")).reason()).isEqualTo(ImbalanceReason.MISSING_DIMENSION);
    }

    @Test
    void disabledRuleIsNotEvaluated() {
        ImbalanceRule disabled = rule("OFF", ScoringDimension.DELIVERY_STABILITY, ScoringDimension.TECHNICAL_RISK,
                GapDirection.LEFT_MINUS_RIGHT, ConditionOperator.GAP_GTE, "30", false);
        ImbalanceResult r = one(disabled, scores("82", "79", "34", "38"));
        assertThat(r.status()).isEqualTo(ImbalanceStatus.NOT_EVALUATED);
        assertThat(r.reason()).isEqualTo(ImbalanceReason.RULE_DISABLED);
    }

    @Test
    void severalRulesAndGapsAreReturnedInRuleIdOrder() {
        ImbalanceRule teamLeadership = rule("TEAM_LEADERSHIP_GAP", ScoringDimension.TEAM_HEALTH, ScoringDimension.LEADERSHIP_LOAD,
                GapDirection.LEFT_MINUS_RIGHT, ConditionOperator.GAP_GTE, "40", true);
        List<ImbalanceResult> results = service.detect(List.of(teamLeadership, deliveryTechnical()), scores("82", "79", "34", "38"));
        assertThat(results).extracting(ImbalanceResult::ruleId).containsExactly("DELIVERY_TECHNICAL_GAP", "TEAM_LEADERSHIP_GAP");
        assertThat(results).extracting(ImbalanceResult::status).containsExactly(ImbalanceStatus.DETECTED, ImbalanceStatus.DETECTED);
        assertThat(results.get(1).gap()).isEqualByComparingTo("44");
    }

    @Test
    void detectionDoesNotChangeAnyScoreAndIsRepeatable() {
        List<DimensionScore> input = scores("82", "79", "34", "38");
        List<DimensionScore> before = List.copyOf(input);
        List<ImbalanceResult> first = service.detect(List.of(deliveryTechnical()), input);
        assertThat(input).isEqualTo(before);
        assertThat(input.get(2).rawScore().value()).isEqualByComparingTo("34");
        assertThat(service.detect(List.of(deliveryTechnical()), input)).isEqualTo(first);
    }

    // ---- configuration ----

    @Test
    void ruleConfigurationIsValidated() {
        assertCode(() -> rule("X", ScoringDimension.TEAM_HEALTH, ScoringDimension.TEAM_HEALTH, GapDirection.ABSOLUTE,
                ConditionOperator.GAP_GT, "10", true), ScoringErrorCode.INVALID_IMBALANCE_CONFIGURATION);
        assertCode(() -> rule("X", ScoringDimension.TEAM_HEALTH, ScoringDimension.TECHNICAL_RISK, GapDirection.ABSOLUTE,
                ConditionOperator.GT, "10", true), ScoringErrorCode.INVALID_IMBALANCE_CONFIGURATION);
        assertCode(() -> rule("X", ScoringDimension.TEAM_HEALTH, ScoringDimension.TECHNICAL_RISK, GapDirection.ABSOLUTE,
                ConditionOperator.GAP_GT, "101", true), ScoringErrorCode.INVALID_IMBALANCE_CONFIGURATION);
        assertCode(() -> rule("X", ScoringDimension.TEAM_HEALTH, ScoringDimension.TECHNICAL_RISK, GapDirection.ABSOLUTE,
                ConditionOperator.GAP_GT, "-1", true), ScoringErrorCode.INVALID_IMBALANCE_CONFIGURATION);
        assertCode(() -> new ImbalanceRule(" ", 1, ScoringDimension.TEAM_HEALTH, ScoringDimension.TECHNICAL_RISK,
                GapDirection.ABSOLUTE, ConditionOperator.GAP_GT, d("1"), Severity.LOW, EvaluationBasis.RAW_DIMENSION_SCORE,
                true, null, null), ScoringErrorCode.INVALID_IMBALANCE_CONFIGURATION);
    }

    @Test
    void snapshotRejectsDuplicateRuleVersions() {
        assertCode(() -> new ScoringConfigurationSnapshot(new ConfigurationVersion("t"), Map.of(), Map.of(),
                DimensionScoringConfig.none(), List.of(), List.of(), List.of(deliveryTechnical(), deliveryTechnical())),
                ScoringErrorCode.INVALID_IMBALANCE_CONFIGURATION);
        assertThat(new ScoringConfigurationSnapshot(new ConfigurationVersion("t"), Map.of(), Map.of(),
                DimensionScoringConfig.none(), List.of(), List.of(), List.of(deliveryTechnical())).imbalanceRules()).hasSize(1);
    }
}
