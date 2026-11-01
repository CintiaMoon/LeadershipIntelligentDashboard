package com.company.leadershipintelligence.domain.service.explain;

import com.company.leadershipintelligence.domain.enums.ImbalanceStatus;
import com.company.leadershipintelligence.domain.enums.PatternStatus;
import com.company.leadershipintelligence.domain.enums.PenaltyStatus;
import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.TraceStage;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.DimensionTrace;
import com.company.leadershipintelligence.domain.model.PenaltyAggregation;
import com.company.leadershipintelligence.domain.model.ScoringFacts;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.model.ScoringTrace;
import com.company.leadershipintelligence.domain.model.TraceStep;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.service.explain.PipelineFixture.run;
import static com.company.leadershipintelligence.domain.service.explain.PipelineFixture.snapshot;
import static com.company.leadershipintelligence.domain.service.explain.PipelineFixture.standardInputs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExplainabilityServiceTest {

    private final ExplainabilityService service = new ExplainabilityService();
    private final ScoringFacts facts = run(snapshot(false), standardInputs());
    private final ScoringTrace trace = service.build(facts);

    @Test
    void traceHasEveryStageOnceInOrderWithVersions() {
        assertThat(trace.traceVersion()).isEqualTo("1");
        assertThat(trace.steps()).extracting(TraceStep::stage).containsExactly(TraceStage.values());
        assertThat(trace.header().engineVersion()).isEqualTo("3.0.0");
        assertThat(trace.header().algorithmVersion()).isEqualTo("1.0.0");
        assertThat(trace.header().configurationVersion()).isEqualTo("cfg-1");
        assertThat(trace.header().roundingPolicyVersion()).isEqualTo("1");
        assertThat(trace.header().confidenceAlgorithmVersion()).isEqualTo("1");
        assertThat(trace.header().tieBreakVersion()).isEqualTo("1");
        assertThat(trace.header().scoreStatus()).isEqualTo(ScoreStatus.VALID);
    }

    @Test
    void traceCarriesTheRecordedFactsUnchanged() {
        assertThat(trace.step(TraceStep.Normalization.class).inputs()).isEqualTo(facts.normalizedInputs());
        assertThat(trace.step(TraceStep.Signals.class).signals()).isEqualTo(facts.signals());
        assertThat(trace.step(TraceStep.RawDimensions.class).dimensions()).extracting(DimensionTrace::score)
                .isEqualTo(facts.dimensionScores());
        assertThat(trace.step(TraceStep.Imbalances.class).imbalances()).isEqualTo(facts.imbalances());
        assertThat(trace.step(TraceStep.Patterns.class).patterns()).isEqualTo(facts.patterns());
        assertThat(trace.step(TraceStep.Penalties.class).aggregation()).isSameAs(facts.penalties());
        assertThat(trace.step(TraceStep.AdjustedDimensions.class).dimensions()).isEqualTo(facts.overall().dimensions());
        assertThat(trace.step(TraceStep.Overall.class).overall()).isSameAs(facts.overall());
    }

    @Test
    void theTraceTellsTheWholeStoryFromInputToScore() {
        assertThat(trace.step(TraceStep.Signals.class).signals()).extracting(s -> s.signalId())
                .containsExactly("SINGLE_POINT_OF_FAILURE", "TEAM_SUSTAINABILITY", "WORKLOAD_PRESSURE");
        List<DimensionTrace> raw = trace.step(TraceStep.RawDimensions.class).dimensions();
        assertThat(raw).extracting(t -> t.score().rawScore().value().toPlainString()).containsExactly("30.00", "80.00", "66.00", "70.00");
        assertThat(trace.step(TraceStep.Imbalances.class).imbalances().get(0).status()).isEqualTo(ImbalanceStatus.DETECTED);
        assertThat(trace.step(TraceStep.Patterns.class).patterns().get(0).status()).isEqualTo(PatternStatus.MATCHED);
        var penalty = trace.step(TraceStep.Penalties.class).aggregation().results().get(0);
        assertThat(penalty.status()).isEqualTo(PenaltyStatus.APPLIED);
        assertThat(penalty.effectiveDeduction().value()).isEqualByComparingTo("8");
        var adjusted = trace.step(TraceStep.AdjustedDimensions.class);
        assertThat(adjusted.dimensions().get(0).adjustedScore().value().toPlainString()).isEqualTo("22.00");
        assertThat(adjusted.labels().get(ScoringDimension.TEAM_HEALTH).label()).isEqualTo("CRITICAL");
        var overall = trace.step(TraceStep.Overall.class);
        assertThat(overall.overall().finalScore().value().toPlainString()).isEqualTo("59.50");
        assertThat(overall.interpretation().label()).isEqualTo("AT_RISK");
    }

    @Test
    void driversComeFromRecordedContributionsOrderedByValue() {
        ScoringFacts withMany = run(snapshot(true), List.of(new ScoringInput("T1", 30), new ScoringInput("T2", 90),
                new ScoringInput("T3", 60), new ScoringInput("T4", 80), new ScoringInput("D1", 80), new ScoringInput("R1", 66),
                new ScoringInput("L1", 70)));
        DimensionTrace team = service.build(withMany).step(TraceStep.RawDimensions.class).dimensions().get(0);
        assertThat(team.score().rawScore().value()).isEqualByComparingTo("65");
        assertThat(team.positiveDrivers()).containsExactly("T2", "T4");
        assertThat(team.negativeDrivers()).containsExactly("T1", "T3");
        DimensionTrace delivery = service.build(withMany).step(TraceStep.RawDimensions.class).dimensions().get(1);
        assertThat(delivery.positiveDrivers()).isEmpty();
        assertThat(delivery.negativeDrivers()).isEmpty();
    }

    @Test
    void theTraceNeverContainsTheRawAnswers() {
        String text = trace.toString();
        assertThat(text).doesNotContain("key person").doesNotContain("We depend");
        assertThat(trace.step(TraceStep.Normalization.class).inputs()).anySatisfy(i ->
                assertThat(i.detectedSignals()).containsExactly("SINGLE_POINT_OF_FAILURE"));
    }

    @Test
    void insufficientDataIsExplainedWithoutAnyInventedNumber() {
        ScoringFacts insufficient = run(snapshot(false), List.of(new ScoringInput("T1", 30), new ScoringInput("D1", 80),
                new ScoringInput("L1", 70), new ScoringInput("Q4", "We depend on one key person.")));
        ScoringTrace t = service.build(insufficient);
        assertThat(t.header().scoreStatus()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        DimensionTrace technical = t.step(TraceStep.RawDimensions.class).dimensions().get(2);
        assertThat(technical.score().rawScore()).isNull();
        assertThat(technical.score().coverage().coverage()).isEqualByComparingTo("0");
        assertThat(t.step(TraceStep.Overall.class).overall().finalScore()).isNull();
        assertThat(t.step(TraceStep.Overall.class).interpretation()).isNull();
        assertThat(t.step(TraceStep.AdjustedDimensions.class).labels()).doesNotContainKey(ScoringDimension.TECHNICAL_RISK);
        assertThat(t.step(TraceStep.Patterns.class).patterns()).hasSize(1);
    }

    @Test
    void buildingTwiceGivesTheSameTrace() {
        assertThat(service.build(facts)).isEqualTo(service.build(facts));
    }

    // ---- consistency: a trace that disagrees with the result is refused, not patched ----

    @Test
    void penaltyWithAnUnrecordedTriggerIsRefused() {
        ScoringFacts broken = new ScoringFacts(facts.versions(), facts.normalizedInputs(), facts.signals(), facts.dimensionScores(),
                facts.imbalances(), List.of(), facts.penalties(), facts.overall(), facts.lisInterpretation(), facts.dimensionInterpretations());
        assertThatThrownBy(() -> service.build(broken)).isInstanceOf(IllegalStateException.class).hasMessageContaining("trigger pattern");
    }

    @Test
    void penaltyTotalsThatDoNotMatchTheResultsAreRefused() {
        PenaltyAggregation wrong = new PenaltyAggregation(facts.penalties().results(),
                Map.of(com.company.leadershipintelligence.domain.enums.PenaltyTarget.TEAM_HEALTH, new PenaltyAmount(d("9"))), "1");
        ScoringFacts broken = new ScoringFacts(facts.versions(), facts.normalizedInputs(), facts.signals(), facts.dimensionScores(),
                facts.imbalances(), facts.patterns(), wrong, facts.overall(), facts.lisInterpretation(), facts.dimensionInterpretations());
        assertThatThrownBy(() -> service.build(broken)).isInstanceOf(IllegalStateException.class).hasMessageContaining("add up");
    }

    @Test
    void adjustedScoresThatDoNotStartFromTheRecordedRawScoreAreRefused() {
        List<DimensionScore> other = run(snapshot(false), List.of(new ScoringInput("T1", 31), new ScoringInput("D1", 80),
                new ScoringInput("R1", 66), new ScoringInput("L1", 70))).dimensionScores();
        ScoringFacts broken = new ScoringFacts(facts.versions(), facts.normalizedInputs(), facts.signals(), other,
                facts.imbalances(), facts.patterns(), facts.penalties(), facts.overall(), facts.lisInterpretation(), facts.dimensionInterpretations());
        assertThatThrownBy(() -> service.build(broken)).isInstanceOf(IllegalStateException.class).hasMessageContaining("raw score");
    }

    @Test
    void aTraceMustHaveAllStagesInOrder() {
        List<TraceStep> steps = trace.steps();
        assertThatThrownBy(() -> new ScoringTrace("1", trace.header(), steps.subList(0, 7))).isInstanceOf(IllegalArgumentException.class);
        List<TraceStep> swapped = new java.util.ArrayList<>(steps);
        java.util.Collections.swap(swapped, 0, 1);
        assertThatThrownBy(() -> new ScoringTrace("1", trace.header(), swapped)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Arrays.stream(TraceStage.values())).hasSize(8);
    }
}
