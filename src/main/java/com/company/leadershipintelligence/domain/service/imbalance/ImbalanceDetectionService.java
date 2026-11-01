package com.company.leadershipintelligence.domain.service.imbalance;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.ImbalanceReason;
import com.company.leadershipintelligence.domain.enums.ImbalanceStatus;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.ImbalanceResult;
import com.company.leadershipintelligence.domain.rule.ImbalanceRule;
import com.company.leadershipintelligence.domain.valueobject.ScoreValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Detects configured dimension gaps on RAW dimension scores (specv3.md Section 12). Diagnostic only: it
 * returns findings and can change no score. A dimension without a valid score makes the rule NOT_EVALUATED;
 * it is never treated as zero. Results are in rule-id then version order.
 */
public class ImbalanceDetectionService {

    public List<ImbalanceResult> detect(List<ImbalanceRule> rules, List<DimensionScore> scores) {
        Map<ScoringDimension, ScoreValue> raw = new EnumMap<>(ScoringDimension.class);
        scores.forEach(s -> s.score().ifPresent(v -> raw.put(s.dimension(), v)));
        List<ImbalanceRule> ordered = new ArrayList<>(rules);
        ordered.sort(Comparator.comparing(ImbalanceRule::ruleId).thenComparingInt(ImbalanceRule::version));
        List<ImbalanceResult> results = new ArrayList<>();
        for (ImbalanceRule rule : ordered) {
            results.add(evaluate(rule, raw));
        }
        return List.copyOf(results);
    }

    private static ImbalanceResult evaluate(ImbalanceRule rule, Map<ScoringDimension, ScoreValue> raw) {
        List<String> refs = List.of("dimension:" + rule.left(), "dimension:" + rule.right());
        if (!rule.enabled()) {
            return result(rule, ImbalanceStatus.NOT_EVALUATED, ImbalanceReason.RULE_DISABLED, null, null, null, refs);
        }
        ScoreValue left = raw.get(rule.left());
        ScoreValue right = raw.get(rule.right());
        if (left == null || right == null) {
            return result(rule, ImbalanceStatus.NOT_EVALUATED, ImbalanceReason.MISSING_DIMENSION, null, null, null, refs);
        }
        BigDecimal difference = left.value().subtract(right.value());
        BigDecimal gap = switch (rule.direction()) {
            case LEFT_MINUS_RIGHT -> difference;
            case RIGHT_MINUS_LEFT -> difference.negate();
            case ABSOLUTE -> difference.abs();
        };
        int cmp = gap.compareTo(rule.threshold());
        boolean detected = rule.gapOperator() == ConditionOperator.GAP_GT ? cmp > 0 : cmp >= 0;
        return result(rule, detected ? ImbalanceStatus.DETECTED : ImbalanceStatus.NOT_DETECTED,
                detected ? ImbalanceReason.THRESHOLD_MET : ImbalanceReason.THRESHOLD_NOT_MET, left, right, gap, refs);
    }

    private static ImbalanceResult result(ImbalanceRule rule, ImbalanceStatus status, ImbalanceReason reason,
                                          ScoreValue left, ScoreValue right, BigDecimal gap, List<String> refs) {
        return new ImbalanceResult(rule.ruleId(), rule.version(), status, reason, rule.left(), rule.right(), left, right,
                gap, rule.direction(), rule.gapOperator(), rule.threshold(), rule.severity(), rule.basis(),
                rule.explanationKey(), rule.recommendationKey(), refs);
    }
}
