package com.company.leadershipintelligence.domain.service.explain;

import com.company.leadershipintelligence.domain.enums.ContributionStatus;
import com.company.leadershipintelligence.domain.enums.PenaltyTarget;
import com.company.leadershipintelligence.domain.model.AdjustedDimension;
import com.company.leadershipintelligence.domain.model.DimensionContribution;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.DimensionTrace;
import com.company.leadershipintelligence.domain.model.PenaltyResult;
import com.company.leadershipintelligence.domain.model.ScoringFacts;
import com.company.leadershipintelligence.domain.model.ScoringTrace;
import com.company.leadershipintelligence.domain.model.TraceHeader;
import com.company.leadershipintelligence.domain.model.TraceStep;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the explanation trace from recorded scoring facts (specv3.md Section 19, decision D24). It only
 * arranges facts: it does not call any scoring service, does not recompute a score, deduction, pattern or
 * label, and never reads configuration. It refuses facts that contradict each other, because a trace that
 * disagrees with the result must be fixed at its source, not patched here.
 */
public class ExplainabilityService {

    public static final String TRACE_VERSION = "1";

    public ScoringTrace build(ScoringFacts facts) {
        verifyConsistency(facts);
        TraceHeader header = new TraceHeader(
                facts.versions().engineVersion(),
                facts.versions().algorithmVersion(),
                facts.versions().configurationVersion().value(),
                facts.versions().roundingPolicyVersion(),
                facts.versions().confidenceAlgorithmVersion(),
                facts.penalties().tieBreakVersion(),
                facts.overall().status());
        List<DimensionTrace> dimensions = facts.dimensionScores().stream().map(ExplainabilityService::dimensionTrace).toList();
        List<TraceStep> steps = List.of(
                new TraceStep.Normalization(facts.normalizedInputs()),
                new TraceStep.Signals(facts.signals()),
                new TraceStep.RawDimensions(dimensions),
                new TraceStep.Imbalances(facts.imbalances()),
                new TraceStep.Patterns(facts.patterns()),
                new TraceStep.Penalties(facts.penalties()),
                new TraceStep.AdjustedDimensions(facts.overall().dimensions(), facts.dimensionInterpretations()),
                new TraceStep.Overall(facts.overall(), facts.lisInterpretation()));
        return new ScoringTrace(TRACE_VERSION, header, steps);
    }

    private static DimensionTrace dimensionTrace(DimensionScore score) {
        if (score.rawScore() == null) {
            return new DimensionTrace(score, List.of(), List.of());
        }
        BigDecimal raw = score.rawScore().value();
        List<DimensionContribution> applied = score.contributions().stream()
                .filter(c -> c.status() == ContributionStatus.APPLIED).toList();
        List<String> positive = applied.stream()
                .filter(c -> c.value().value().compareTo(raw) > 0)
                .sorted(Comparator.comparing((DimensionContribution c) -> c.value().value()).reversed()
                        .thenComparing(DimensionContribution::questionId))
                .map(DimensionContribution::questionId).toList();
        List<String> negative = applied.stream()
                .filter(c -> c.value().value().compareTo(raw) < 0)
                .sorted(Comparator.comparing((DimensionContribution c) -> c.value().value())
                        .thenComparing(DimensionContribution::questionId))
                .map(DimensionContribution::questionId).toList();
        return new DimensionTrace(score, positive, negative);
    }

    private static void verifyConsistency(ScoringFacts facts) {
        for (PenaltyResult result : facts.penalties().results()) {
            boolean triggerRecorded = facts.patterns().stream().anyMatch(p ->
                    p.patternId().equals(result.definition().triggerPatternId())
                            && p.version() == result.definition().triggerPatternVersion());
            if (!triggerRecorded) {
                throw inconsistent("penalty " + result.penaltyId() + " names a trigger pattern missing from the trace");
            }
        }
        Map<PenaltyTarget, BigDecimal> sums = new EnumMap<>(PenaltyTarget.class);
        for (PenaltyResult result : facts.penalties().results()) {
            sums.merge(result.definition().target(), result.effectiveDeduction().value(), BigDecimal::add);
        }
        for (PenaltyTarget target : PenaltyTarget.values()) {
            BigDecimal recorded = sums.getOrDefault(target, BigDecimal.ZERO);
            if (recorded.compareTo(facts.penalties().total(target).value()) != 0) {
                throw inconsistent("penalty results for " + target + " do not add up to the recorded total");
            }
        }
        for (AdjustedDimension adjusted : facts.overall().dimensions()) {
            DimensionScore raw = facts.dimensionScores().stream()
                    .filter(s -> s.dimension() == adjusted.dimension()).findFirst()
                    .orElseThrow(() -> inconsistent("no raw score recorded for " + adjusted.dimension()));
            boolean same = adjusted.rawScore() == null ? raw.rawScore() == null : adjusted.rawScore().equals(raw.rawScore());
            if (!same) {
                throw inconsistent("the adjusted " + adjusted.dimension() + " does not start from the recorded raw score");
            }
        }
    }

    private static IllegalStateException inconsistent(String message) {
        return new IllegalStateException("inconsistent scoring facts: " + message);
    }
}
