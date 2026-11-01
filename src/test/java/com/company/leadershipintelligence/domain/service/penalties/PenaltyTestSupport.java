package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.enums.ConfidenceBand;
import com.company.leadershipintelligence.domain.enums.EvaluationOutcome;
import com.company.leadershipintelligence.domain.enums.EvidenceKind;
import com.company.leadershipintelligence.domain.enums.PatternCategory;
import com.company.leadershipintelligence.domain.enums.PatternReason;
import com.company.leadershipintelligence.domain.enums.PatternStatus;
import com.company.leadershipintelligence.domain.enums.PenaltyReason;
import com.company.leadershipintelligence.domain.enums.PenaltyTarget;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.enums.StackingPolicy;
import com.company.leadershipintelligence.domain.enums.TriState;
import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.model.ConditionResult;
import com.company.leadershipintelligence.domain.model.PatternEvaluation;
import com.company.leadershipintelligence.domain.model.PatternEvidence;
import com.company.leadershipintelligence.domain.model.PenaltyEvaluation;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.valueobject.ConfidenceValue;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static com.company.leadershipintelligence.domain.Fixtures.d;

/** Builders for penalty tests. All values are illustrative. */
public final class PenaltyTestSupport {

    private PenaltyTestSupport() {
    }

    // ---- pattern findings ----

    private static final ConditionResult TRUE_RESULT = new ConditionResult(ConditionOperator.AND, TriState.TRUE, "", List.of());

    public static PatternEvaluation matched(String id, Severity severity, String confidence, boolean penaltyEligible) {
        PatternEvidence evidence = new PatternEvidence("signal:WORKLOAD_PRESSURE", EvidenceKind.SIGNAL, d("80"), null,
                Set.of("Q1"), "signal:WORKLOAD_PRESSURE", BigDecimal.ONE);
        return new PatternEvaluation(id, id + "_CODE", 1, PatternCategory.TEAM_HEALTH, severity, PatternStatus.MATCHED,
                PatternReason.CONDITION_SATISFIED, TRUE_RESULT, List.of(), List.of(evidence),
                new ConfidenceValue(d(confidence)), ConfidenceBand.STRONG, null, penaltyEligible, List.of());
    }

    public static PatternEvaluation notMatched(String id) {
        return without(id, PatternStatus.NOT_MATCHED, PatternReason.CONDITION_NOT_SATISFIED);
    }

    public static PatternEvaluation notEvaluated(String id, PatternReason reason) {
        return without(id, PatternStatus.NOT_EVALUATED, reason);
    }

    public static PatternEvaluation ineligible(String id, Severity severity) {
        return new PatternEvaluation(id, id + "_CODE", 1, PatternCategory.TEAM_HEALTH, severity, PatternStatus.INELIGIBLE,
                PatternReason.CONFIDENCE_BELOW_MINIMUM, TRUE_RESULT, List.of(), List.of(), new ConfidenceValue(d("0.3")),
                ConfidenceBand.INSUFFICIENT, null, false, List.of());
    }

    private static PatternEvaluation without(String id, PatternStatus status, PatternReason reason) {
        return new PatternEvaluation(id, id + "_CODE", 1, PatternCategory.TEAM_HEALTH, Severity.HIGH, status, reason,
                null, List.of(), List.of(), null, null, null, false, List.of());
    }

    // ---- penalty definitions ----

    public static final class Pen {
        private String id = "P1";
        private String trigger = "TH-001";
        private PenaltyTarget target = PenaltyTarget.TEAM_HEALTH;
        private StrategyConfig strategy = new StrategyConfig.Fixed(new PenaltyAmount(d("5")));
        private String max = "100";
        private String category = "C";
        private String group;
        private int priority;
        private int order;
        private StackingPolicy policy = StackingPolicy.STACK;
        private String cap;
        private String minConfidence = "0";
        private boolean enabled = true;
        private String rationale;

        public Pen id(String v) { id = v; return this; }
        public Pen trigger(String v) { trigger = v; return this; }
        public Pen target(PenaltyTarget v) { target = v; return this; }
        public Pen strategy(StrategyConfig v) { strategy = v; return this; }
        public Pen max(String v) { max = v; return this; }
        public Pen category(String v) { category = v; return this; }
        public Pen group(String v) { group = v; return this; }
        public Pen priority(int v) { priority = v; return this; }
        public Pen order(int v) { order = v; return this; }
        public Pen policy(StackingPolicy v) { policy = v; return this; }
        public Pen cap(String v) { cap = v; return this; }
        public Pen minConfidence(String v) { minConfidence = v; return this; }
        public Pen enabled(boolean v) { enabled = v; return this; }
        public Pen rationale(String v) { rationale = v; return this; }

        public PenaltyDefinition build() {
            return new PenaltyDefinition(id, id + "_CODE", 1, trigger, 1, target, strategy, new PenaltyAmount(d(max)),
                    category, group, priority, order, policy, cap == null ? null : new PenaltyAmount(d(cap)),
                    new ConfidenceValue(d(minConfidence)), enabled, rationale, "explain." + id);
        }
    }

    public static Pen pen() {
        return new Pen();
    }

    public static PenaltyEvaluation candidate(PenaltyDefinition definition, String amount) {
        return new PenaltyEvaluation(definition, EvaluationOutcome.CANDIDATE, PenaltyReason.CANDIDATE_CALCULATED,
                Severity.HIGH, null, null, null, new PenaltyAmount(d(amount)), List.of("signal:WORKLOAD_PRESSURE"));
    }

    public static PenaltyEvaluation notCandidate(PenaltyDefinition definition, EvaluationOutcome outcome, PenaltyReason reason) {
        return new PenaltyEvaluation(definition, outcome, reason, Severity.HIGH, null, null, null, null, List.of());
    }
}
