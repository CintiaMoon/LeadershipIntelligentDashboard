package com.company.leadershipintelligence.domain.service.patterns;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.GapDirection;
import com.company.leadershipintelligence.domain.enums.PatternReason;
import com.company.leadershipintelligence.domain.enums.PatternStatus;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.enums.TriState;
import com.company.leadershipintelligence.domain.model.DerivedSignal;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.model.PatternEvaluation;
import com.company.leadershipintelligence.domain.rule.Condition;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.rule.PatternDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.cmp;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.ctx;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.dim;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.insufficient;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.num;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.pattern;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.score;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.sig;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.th001;
import static org.assertj.core.api.Assertions.assertThat;

class PatternDetectionServiceTest {

    private final PatternDetectionService service = new PatternDetectionService(
            new ConditionEvaluator(), new PatternConfidenceService(ConfidenceBandConfig.illustrative()));

    private PatternEvaluation one(PatternDefinition pattern, EvaluationContext context) {
        return service.detect(List.of(pattern), context).get(0);
    }

    private static EvaluationContext th(String workload, String sustainability, String reliability) {
        return ctx(num("WORKLOAD_PRESSURE", workload, null, reliability), num("TEAM_SUSTAINABILITY", sustainability, null, reliability));
    }

    @Test
    void matchedPatternCarriesEvidenceConfidenceAndSeverity() {
        PatternEvaluation e = one(th001(), th("80", "30", "1"));
        assertThat(e.status()).isEqualTo(PatternStatus.MATCHED);
        assertThat(e.reason()).isEqualTo(PatternReason.CONDITION_SATISFIED);
        assertThat(e.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(e.confidence().value()).isEqualByComparingTo("1");
        assertThat(e.evidence()).hasSize(2);
        assertThat(e.conditionResult().outcome()).isEqualTo(TriState.TRUE);
        assertThat(e.penaltyEligible()).isTrue();
        assertThat(e.actionKeys()).containsExactly("REVIEW_TH-001");
        assertThat(e.missingOperands()).isEmpty();
    }

    @Test
    void thresholdBoundariesMatchAtTheEdgeAndNotJustOutside() {
        assertThat(one(th001(), th("75", "35", "1")).status()).isEqualTo(PatternStatus.MATCHED);
        PatternEvaluation just = one(th001(), th("74.99", "35", "1"));
        assertThat(just.status()).isEqualTo(PatternStatus.NOT_MATCHED);
        assertThat(just.reason()).isEqualTo(PatternReason.CONDITION_NOT_SATISFIED);
        assertThat(just.evidence()).isEmpty();
        assertThat(just.confidence()).isNull();
        assertThat(just.penaltyEligible()).isFalse();
    }

    @Test
    void missingRequiredSignalIsNotEvaluatedWithTheMissingOperandNamed() {
        PatternEvaluation e = one(th001(), ctx(num("WORKLOAD_PRESSURE", "80")));
        assertThat(e.status()).isEqualTo(PatternStatus.NOT_EVALUATED);
        assertThat(e.reason()).isEqualTo(PatternReason.MISSING_SIGNALS);
        assertThat(e.missingOperands()).containsExactly("signal:TEAM_SUSTAINABILITY");
        assertThat(e.confidence()).isNull();
    }

    @Test
    void aDefiniteFalseWinsOverMissingData() {
        PatternEvaluation e = one(th001(), ctx(num("WORKLOAD_PRESSURE", "10")));
        assertThat(e.status()).isEqualTo(PatternStatus.NOT_MATCHED);
    }

    @Test
    void lowConfidenceKeepsTheFindingButMakesItIneligibleAndKeepsSeverity() {
        PatternEvaluation e = one(th001(), th("80", "30", "0.6"));
        assertThat(e.status()).isEqualTo(PatternStatus.INELIGIBLE);
        assertThat(e.reason()).isEqualTo(PatternReason.CONFIDENCE_BELOW_MINIMUM);
        assertThat(e.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(e.confidence().value()).isEqualByComparingTo("0.6");
        assertThat(e.evidence()).hasSize(2);
        assertThat(e.penaltyEligible()).isFalse();
    }

    @Test
    void confidenceExactlyAtTheMinimumIsEligible() {
        PatternEvaluation e = one(th001(), th("80", "30", "0.7"));
        assertThat(e.confidence().value()).isEqualByComparingTo("0.7");
        assertThat(e.status()).isEqualTo(PatternStatus.MATCHED);
    }

    @Test
    void notEnoughIndependentSignalsIsIneligible() {
        EvaluationContext context = ctx(num("WORKLOAD_PRESSURE", "80", "burnout", "1"), num("TEAM_SUSTAINABILITY", "30", "burnout", "1"));
        PatternEvaluation e = one(th001(), context);
        assertThat(e.status()).isEqualTo(PatternStatus.INELIGIBLE);
        assertThat(e.reason()).isEqualTo(PatternReason.INSUFFICIENT_INDEPENDENT_SIGNALS);
        assertThat(e.confidenceBreakdown().independentGroups()).isEqualTo(1);
    }

    @Test
    void disabledPatternIsNotEvaluated() {
        PatternDefinition disabled = pattern("TH-001", 1, Severity.CRITICAL, th001().condition(), 2, "0.7", false, true);
        PatternEvaluation e = one(disabled, th("80", "30", "1"));
        assertThat(e.status()).isEqualTo(PatternStatus.NOT_EVALUATED);
        assertThat(e.reason()).isEqualTo(PatternReason.PATTERN_DISABLED);
        assertThat(e.conditionResult()).isNull();
    }

    @Test
    void matchedPatternWithoutPenaltyEligibilityIsNotPenaltyEligible() {
        PatternDefinition informational = pattern("TH-001", 1, Severity.LOW, th001().condition(), 2, "0.7", true, false);
        PatternEvaluation e = one(informational, th("80", "30", "1"));
        assertThat(e.status()).isEqualTo(PatternStatus.MATCHED);
        assertThat(e.penaltyEligible()).isFalse();
    }

    @Test
    void crossDimensionalGapPatternUsesRawDimensionScores() {
        // specv3.md Section 12.1: delivery 79 vs technical 34
        Condition gap = new Condition.Gap(dim(ScoringDimension.DELIVERY_STABILITY), dim(ScoringDimension.TECHNICAL_RISK),
                GapDirection.LEFT_MINUS_RIGHT, ConditionOperator.GAP_GTE, d("30"));
        PatternDefinition xd = pattern("XD-005", 1, Severity.HIGH, gap, 2, "0.7", true, true);
        PatternEvaluation matched = one(xd, ctx(List.of(), List.of(score(ScoringDimension.DELIVERY_STABILITY, "79"),
                score(ScoringDimension.TECHNICAL_RISK, "34")), List.of()));
        assertThat(matched.status()).isEqualTo(PatternStatus.MATCHED);
        PatternEvaluation unknown = one(xd, ctx(List.of(), List.of(score(ScoringDimension.DELIVERY_STABILITY, "79"),
                insufficient(ScoringDimension.TECHNICAL_RISK)), List.of()));
        assertThat(unknown.status()).isEqualTo(PatternStatus.NOT_EVALUATED);
        assertThat(unknown.missingOperands()).containsExactly("dimension:TECHNICAL_RISK");
    }

    @Test
    void multipleAndOverlappingPatternsAreReturnedInIdThenVersionOrder() {
        PatternDefinition second = pattern("TH-002", 1, Severity.MEDIUM, cmp(sig("WORKLOAD_PRESSURE"), ConditionOperator.GTE, "70"), 1, "0.5", true, false);
        PatternDefinition v2 = pattern("TH-001", 2, Severity.HIGH, th001().condition(), 2, "0.7", true, true);
        List<PatternEvaluation> results = service.detect(List.of(second, v2, th001()), th("80", "30", "1"));
        assertThat(results).extracting(e -> e.patternId() + "/" + e.version()).containsExactly("TH-001/1", "TH-001/2", "TH-002/1");
        assertThat(results).extracting(PatternEvaluation::status).containsOnly(PatternStatus.MATCHED);
    }

    @Test
    void detectionIsPureAndDeterministic() {
        List<DerivedSignal> signals = List.of(num("WORKLOAD_PRESSURE", "80"), num("TEAM_SUSTAINABILITY", "30"));
        List<DimensionScore> scores = List.of(score(ScoringDimension.TEAM_HEALTH, "60"));
        List<DimensionScore> before = List.copyOf(scores);
        EvaluationContext context = ctx(signals, scores, List.of());
        List<PatternEvaluation> first = service.detect(List.of(th001()), context);
        List<PatternEvaluation> second = service.detect(List.of(th001()), context);
        assertThat(first).isEqualTo(second);
        assertThat(scores).isEqualTo(before);
        assertThat(scores.get(0).rawScore().value()).isEqualByComparingTo("60");
    }
}
