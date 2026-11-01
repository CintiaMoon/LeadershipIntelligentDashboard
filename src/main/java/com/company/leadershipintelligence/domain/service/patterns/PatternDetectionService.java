package com.company.leadershipintelligence.domain.service.patterns;

import com.company.leadershipintelligence.domain.enums.PatternReason;
import com.company.leadershipintelligence.domain.enums.PatternStatus;
import com.company.leadershipintelligence.domain.enums.TriState;
import com.company.leadershipintelligence.domain.model.ConditionResult;
import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.model.PatternEvaluation;
import com.company.leadershipintelligence.domain.rule.Operand;
import com.company.leadershipintelligence.domain.rule.PatternDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pattern detection (specv3.md Sections 4.6, 13, 14). Pure: reads the evaluation context and returns
 * findings; it can change no score. Statuses: MATCHED, NOT_MATCHED, INELIGIBLE (condition satisfied but
 * the evidence gate failed) and NOT_EVALUATED (disabled, or required data missing). Findings are returned
 * in pattern-id then version order.
 */
public class PatternDetectionService {

    private final ConditionEvaluator evaluator;
    private final PatternConfidenceService confidenceService;

    public PatternDetectionService(ConditionEvaluator evaluator, PatternConfidenceService confidenceService) {
        this.evaluator = evaluator;
        this.confidenceService = confidenceService;
    }

    public List<PatternEvaluation> detect(List<PatternDefinition> patterns, EvaluationContext context) {
        List<PatternDefinition> ordered = new ArrayList<>(patterns);
        ordered.sort(Comparator.comparing(PatternDefinition::patternId).thenComparingInt(PatternDefinition::version));
        List<PatternEvaluation> results = new ArrayList<>();
        for (PatternDefinition pattern : ordered) {
            results.add(evaluate(pattern, context));
        }
        return List.copyOf(results);
    }

    PatternEvaluation evaluate(PatternDefinition pattern, EvaluationContext context) {
        if (!pattern.enabled()) {
            return without(pattern, PatternStatus.NOT_EVALUATED, PatternReason.PATTERN_DISABLED, null, List.of());
        }
        ConditionResult result = evaluator.evaluate(pattern.condition(), context);
        List<String> missing = pattern.condition().operands().stream()
                .filter(operand -> !context.available(operand))
                .map(Operand::key)
                .sorted()
                .toList();
        if (result.outcome() == TriState.FALSE) {
            return without(pattern, PatternStatus.NOT_MATCHED, PatternReason.CONDITION_NOT_SATISFIED, result, List.of());
        }
        if (result.outcome() == TriState.UNKNOWN) {
            return without(pattern, PatternStatus.NOT_EVALUATED, PatternReason.MISSING_SIGNALS, result, missing);
        }
        PatternConfidenceService.Assessment assessment = confidenceService.assess(pattern, context);
        PatternStatus status = PatternStatus.MATCHED;
        PatternReason reason = PatternReason.CONDITION_SATISFIED;
        if (assessment.breakdown().independentGroups() < pattern.evidence().minimumSignals()) {
            status = PatternStatus.INELIGIBLE;
            reason = PatternReason.INSUFFICIENT_INDEPENDENT_SIGNALS;
        } else if (assessment.confidence().value().compareTo(pattern.evidence().minimumConfidence().value()) < 0) {
            status = PatternStatus.INELIGIBLE;
            reason = PatternReason.CONFIDENCE_BELOW_MINIMUM;
        }
        return new PatternEvaluation(pattern.patternId(), pattern.code(), pattern.version(), pattern.category(),
                pattern.severity(), status, reason, result, missing, assessment.evidence(), assessment.confidence(),
                assessment.band(), assessment.breakdown(), pattern.penaltyEligible() && status == PatternStatus.MATCHED,
                pattern.actionKeys());
    }

    private static PatternEvaluation without(PatternDefinition pattern, PatternStatus status, PatternReason reason,
                                             ConditionResult result, List<String> missing) {
        return new PatternEvaluation(pattern.patternId(), pattern.code(), pattern.version(), pattern.category(),
                pattern.severity(), status, reason, result, missing, List.of(), null, null, null, false,
                pattern.actionKeys());
    }
}
