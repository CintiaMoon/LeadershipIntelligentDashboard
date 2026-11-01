package com.company.leadershipintelligence.domain.service.patterns;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.ConfidenceBand;
import com.company.leadershipintelligence.domain.enums.EvidenceKind;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.rule.Condition;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.rule.PatternDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.cmp;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.ctx;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.dim;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.num;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.pattern;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.score;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.sig;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.th001;
import static org.assertj.core.api.Assertions.assertThat;

class PatternConfidenceServiceTest {

    private final PatternConfidenceService service = new PatternConfidenceService(ConfidenceBandConfig.illustrative());

    private PatternConfidenceService.Assessment assess(PatternDefinition pattern, com.company.leadershipintelligence.domain.model.EvaluationContext context) {
        return service.assess(pattern, context);
    }

    private PatternConfidenceService.Assessment twoSignals(String reliabilityA, String reliabilityB, String groupA, String groupB) {
        return assess(th001(), ctx(num("WORKLOAD_PRESSURE", "80", groupA, reliabilityA), num("TEAM_SUSTAINABILITY", "30", groupB, reliabilityB)));
    }

    @Test
    void fullyReliableIndependentEvidenceGivesFullConfidence() {
        PatternConfidenceService.Assessment a = twoSignals("1", "1", null, null);
        assertThat(a.confidence().value()).isEqualByComparingTo("1");
        assertThat(a.band()).isEqualTo(ConfidenceBand.STRONG);
        assertThat(a.evidence()).hasSize(2);
        assertThat(a.breakdown().supportingSignals()).isEqualTo(2);
        assertThat(a.breakdown().independentGroups()).isEqualTo(2);
        assertThat(a.breakdown().algorithmVersion()).isEqualTo("1");
    }

    @Test
    void reliabilityIsAveragedAcrossEvidence() {
        assertThat(twoSignals("0.9", "0.8", null, null).confidence().value()).isEqualByComparingTo("0.85");
    }

    @Test
    void sharedCorrelationGroupCountsOnce() {
        PatternConfidenceService.Assessment a = twoSignals("1", "1", "burnout", "burnout");
        assertThat(a.breakdown().independentGroups()).isEqualTo(1);
        assertThat(a.breakdown().independence()).isEqualByComparingTo("0.5");
        assertThat(a.confidence().value()).isEqualByComparingTo("0.5");
    }

    @Test
    void missingEvidenceLowersCoverageInsteadOfCountingAsZeroValue() {
        PatternConfidenceService.Assessment a = assess(th001(), ctx(num("WORKLOAD_PRESSURE", "80")));
        assertThat(a.breakdown().coverage()).isEqualByComparingTo("0.5");
        assertThat(a.breakdown().independence()).isEqualByComparingTo("0.5");
        assertThat(a.confidence().value()).isEqualByComparingTo("0.25");
    }

    @Test
    void bandEdges() {
        assertThat(twoSignals("0.39", "0.39", null, null).band()).isEqualTo(ConfidenceBand.INSUFFICIENT);
        assertThat(twoSignals("0.40", "0.40", null, null).band()).isEqualTo(ConfidenceBand.WEAK);
        assertThat(twoSignals("0.59", "0.59", null, null).band()).isEqualTo(ConfidenceBand.WEAK);
        assertThat(twoSignals("0.60", "0.60", null, null).band()).isEqualTo(ConfidenceBand.MODERATE);
        assertThat(twoSignals("0.79", "0.79", null, null).band()).isEqualTo(ConfidenceBand.MODERATE);
        assertThat(twoSignals("0.80", "0.80", null, null).band()).isEqualTo(ConfidenceBand.STRONG);
    }

    @Test
    void confidenceIsRoundedToTwoDecimalsHalfUp() {
        // reliability 0.855 -> 0.86
        assertThat(twoSignals("0.855", "0.855", null, null).confidence().value().toPlainString()).isEqualTo("0.86");
    }

    @Test
    void dimensionEvidenceIsIndependentOfSignalsAndFullyReliable() {
        PatternDefinition pattern = pattern("XD-001", 1, Severity.HIGH, new Condition.And(List.of(
                cmp(sig("WORKLOAD_PRESSURE"), ConditionOperator.GTE, "75"),
                cmp(dim(ScoringDimension.TECHNICAL_RISK), ConditionOperator.LT, "50"))), 2, "0.7", true, false);
        PatternConfidenceService.Assessment a = assess(pattern,
                ctx(List.of(num("WORKLOAD_PRESSURE", "80")), List.of(score(ScoringDimension.TECHNICAL_RISK, "40")), List.of()));
        assertThat(a.evidence()).extracting(e -> e.kind()).containsExactly(EvidenceKind.SIGNAL, EvidenceKind.DIMENSION);
        assertThat(a.confidence().value()).isEqualByComparingTo("1");
    }

    @Test
    void freshnessIsNotAssessedYetAndDoesNotChangeTheResult() {
        PatternConfidenceService.Assessment a = twoSignals("1", "1", null, null);
        assertThat(a.breakdown().freshnessAssessed()).isFalse();
        assertThat(a.breakdown().freshness()).isEqualByComparingTo("1");
    }

    @Test
    void severityDoesNotInfluenceConfidence() {
        var low = pattern("TH-001", 1, Severity.LOW, th001().condition(), 2, "0.7", true, true);
        var critical = pattern("TH-001", 1, Severity.CRITICAL, th001().condition(), 2, "0.7", true, true);
        var context = ctx(num("WORKLOAD_PRESSURE", "80", null, "0.7"), num("TEAM_SUSTAINABILITY", "30", null, "0.7"));
        assertThat(assess(low, context).confidence()).isEqualTo(assess(critical, context).confidence());
    }

    @Test
    void assessmentIsDeterministic() {
        var context = ctx(num("WORKLOAD_PRESSURE", "80", "g", "0.9"), num("TEAM_SUSTAINABILITY", "30", "h", "0.8"));
        assertThat(assess(th001(), context)).isEqualTo(assess(th001(), context));
    }

    @Test
    void bandBoundsMustBeAscending() {
        assertCode(() -> new ConfidenceBandConfig(d("0.6"), d("0.5"), d("0.8")), ScoringErrorCode.INVALID_PATTERN_CONFIGURATION);
        assertCode(() -> new ConfidenceBandConfig(d("0.4"), d("0.6"), d("1.1")), ScoringErrorCode.INVALID_PATTERN_CONFIGURATION);
        assertThat(new BigDecimal("0.8")).isEqualByComparingTo(ConfidenceBandConfig.illustrative().strongFrom());
    }
}
