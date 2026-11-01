package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.enums.EvaluationOutcome;
import com.company.leadershipintelligence.domain.enums.PatternReason;
import com.company.leadershipintelligence.domain.enums.PatternStatus;
import com.company.leadershipintelligence.domain.enums.PenaltyReason;
import com.company.leadershipintelligence.domain.model.PatternEvaluation;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;

/**
 * Decides whether a penalty may be calculated (specv3.md Sections 6 steps 15, 14.5, 15). A penalty needs its
 * trigger pattern MATCHED, allowed to carry penalties and above the penalty's own minimum confidence. A pattern
 * that could not be evaluated for lack of data makes the penalty INELIGIBLE; a pattern that simply did not match
 * (or is disabled) makes it NOT_TRIGGERED.
 */
public class PenaltyEligibilityService {

    public record Eligibility(boolean eligible, EvaluationOutcome outcome, PenaltyReason reason) {
        static Eligibility yes() {
            return new Eligibility(true, EvaluationOutcome.CANDIDATE, PenaltyReason.CANDIDATE_CALCULATED);
        }

        static Eligibility no(EvaluationOutcome outcome, PenaltyReason reason) {
            return new Eligibility(false, outcome, reason);
        }
    }

    public Eligibility check(PenaltyDefinition penalty, PatternEvaluation trigger) {
        if (!penalty.enabled()) {
            return Eligibility.no(EvaluationOutcome.NOT_TRIGGERED, PenaltyReason.PENALTY_DISABLED);
        }
        if (trigger == null) {
            return Eligibility.no(EvaluationOutcome.INELIGIBLE, PenaltyReason.TRIGGER_PATTERN_ABSENT);
        }
        return switch (trigger.status()) {
            case NOT_MATCHED -> Eligibility.no(EvaluationOutcome.NOT_TRIGGERED, PenaltyReason.PATTERN_NOT_MATCHED);
            case NOT_EVALUATED -> trigger.reason() == PatternReason.PATTERN_DISABLED
                    ? Eligibility.no(EvaluationOutcome.NOT_TRIGGERED, PenaltyReason.PATTERN_DISABLED)
                    : Eligibility.no(EvaluationOutcome.INELIGIBLE, PenaltyReason.PATTERN_NOT_EVALUATED);
            case INELIGIBLE -> Eligibility.no(EvaluationOutcome.INELIGIBLE, PenaltyReason.PATTERN_INELIGIBLE);
            case MATCHED -> matched(penalty, trigger);
        };
    }

    private static Eligibility matched(PenaltyDefinition penalty, PatternEvaluation trigger) {
        if (!trigger.penaltyEligible()) {
            return Eligibility.no(EvaluationOutcome.INELIGIBLE, PenaltyReason.PATTERN_NOT_PENALTY_ELIGIBLE);
        }
        if (trigger.confidence().value().compareTo(penalty.minimumConfidence().value()) < 0) {
            return Eligibility.no(EvaluationOutcome.INELIGIBLE, PenaltyReason.CONFIDENCE_BELOW_PENALTY_MINIMUM);
        }
        return Eligibility.yes();
    }
}
