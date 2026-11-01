package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.enums.CalculationOutcome;
import com.company.leadershipintelligence.domain.enums.EvaluationOutcome;
import com.company.leadershipintelligence.domain.enums.PenaltyReason;
import com.company.leadershipintelligence.domain.enums.PenaltyStrategyType;
import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.model.PatternEvaluation;
import com.company.leadershipintelligence.domain.model.PenaltyEvaluation;
import com.company.leadershipintelligence.domain.model.PatternEvidence;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;
import com.company.leadershipintelligence.domain.strategy.PenaltyCalculation;
import com.company.leadershipintelligence.domain.strategy.PenaltyContext;
import com.company.leadershipintelligence.domain.strategy.PenaltyStrategy;
import com.company.leadershipintelligence.domain.valueobject.Decimals;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates candidate deductions (specv3.md Sections 6 steps 15-16, 15.3). Rounding policy (decision D16):
 * the strategy result is capped by the penalty's individual maximum and then rounded once to 2 decimals
 * (HALF_UP). A zero result is NOT_TRIGGERED. Missing score-base data makes the penalty INELIGIBLE, never zero.
 * Results are returned in rule-order, then penalty-id order.
 */
public class PenaltyEvaluationService {

    private final PenaltyEligibilityService eligibilityService;
    private final Map<PenaltyStrategyType, PenaltyStrategy> strategies = new EnumMap<>(PenaltyStrategyType.class);

    public PenaltyEvaluationService(PenaltyEligibilityService eligibilityService, List<PenaltyStrategy> registered) {
        this.eligibilityService = eligibilityService;
        for (PenaltyStrategy strategy : registered) {
            if (strategies.put(strategy.supportedType(), strategy) != null) {
                throw new IllegalArgumentException("duplicate penalty strategy " + strategy.supportedType());
            }
        }
    }

    public List<PenaltyEvaluation> evaluate(List<PenaltyDefinition> penalties, List<PatternEvaluation> patterns,
                                            EvaluationContext context) {
        List<PenaltyDefinition> ordered = new ArrayList<>(penalties);
        ordered.sort(Comparator.comparingInt(PenaltyDefinition::ruleOrder)
                .thenComparing(PenaltyDefinition::penaltyId).thenComparingInt(PenaltyDefinition::version));
        List<PenaltyEvaluation> results = new ArrayList<>();
        for (PenaltyDefinition penalty : ordered) {
            results.add(evaluateOne(penalty, find(patterns, penalty), context));
        }
        return List.copyOf(results);
    }

    private static PatternEvaluation find(List<PatternEvaluation> patterns, PenaltyDefinition penalty) {
        return patterns.stream()
                .filter(p -> p.patternId().equals(penalty.triggerPatternId()) && p.version() == penalty.triggerPatternVersion())
                .findFirst().orElse(null);
    }

    private PenaltyEvaluation evaluateOne(PenaltyDefinition penalty, PatternEvaluation trigger, EvaluationContext context) {
        PenaltyEligibilityService.Eligibility eligibility = eligibilityService.check(penalty, trigger);
        if (!eligibility.eligible()) {
            return new PenaltyEvaluation(penalty, eligibility.outcome(), eligibility.reason(),
                    trigger == null ? null : trigger.severity(), null, null, null, null, evidenceRefs(trigger));
        }
        PenaltyStrategy strategy = strategies.get(penalty.strategy().type());
        if (strategy == null) {
            throw new IllegalStateException("no penalty strategy registered for " + penalty.strategy().type());
        }
        PenaltyCalculation calculation = strategy.calculate(new PenaltyContext(penalty, trigger, context), penalty.strategy());
        if (calculation.outcome() == CalculationOutcome.MISSING_DATA) {
            return result(penalty, trigger, EvaluationOutcome.INELIGIBLE, calculation.reason(), calculation, null, null);
        }
        if (calculation.outcome() == CalculationOutcome.NO_DEDUCTION) {
            return result(penalty, trigger, EvaluationOutcome.NOT_TRIGGERED, calculation.reason(), calculation, null, null);
        }
        BigDecimal capped = calculation.amount().min(penalty.maximumDeduction().value());
        BigDecimal rounded = Decimals.roundScore(capped);
        if (rounded.signum() == 0) {
            return result(penalty, trigger, EvaluationOutcome.NOT_TRIGGERED, PenaltyReason.ZERO_DEDUCTION, calculation,
                    calculation.amount(), null);
        }
        return result(penalty, trigger, EvaluationOutcome.CANDIDATE, PenaltyReason.CANDIDATE_CALCULATED, calculation,
                calculation.amount(), new PenaltyAmount(rounded));
    }

    private static PenaltyEvaluation result(PenaltyDefinition penalty, PatternEvaluation trigger, EvaluationOutcome outcome,
                                            PenaltyReason reason, PenaltyCalculation calculation, BigDecimal calculated,
                                            PenaltyAmount candidate) {
        return new PenaltyEvaluation(penalty, outcome, reason, trigger.severity(), calculation.scoreBase(),
                calculation.scoreBaseValue(), calculated, candidate, evidenceRefs(trigger));
    }

    private static List<String> evidenceRefs(PatternEvaluation trigger) {
        if (trigger == null) {
            return List.of();
        }
        return trigger.evidence().stream().map(PatternEvidence::operandKey).toList();
    }
}
