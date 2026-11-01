package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.SignalDataType;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reference, type and range validation for conditions (specv3.md Sections 13.5, 30.3). Unknown signals,
 * type-incompatible operators, out-of-range thresholds and over-deep trees are rejected. Numeric
 * thresholds must lie in [0, 100] because every numeric operand is on the 0-100 scale.
 */
public final class ConditionValidator {

    public static final int MAX_DEPTH = 8;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private ConditionValidator() {
    }

    public static void validate(Condition condition, Map<String, SignalDefinition> signals,
                                Map<String, QuestionDefinition> questions) {
        List<String> problems = problems(condition, signals, questions);
        if (!problems.isEmpty()) {
            throw new ScoringException(ScoringErrorCode.INVALID_CONDITION_EXPRESSION, String.join("; ", problems));
        }
    }

    /** All problems found, in traversal order, so callers can report every error at once. */
    public static List<String> problems(Condition condition, Map<String, SignalDefinition> signals,
                                        Map<String, QuestionDefinition> questions) {
        List<String> problems = new ArrayList<>();
        walk(condition, signals, questions, 1, problems);
        return problems;
    }

    private static void walk(Condition node, Map<String, SignalDefinition> signals,
                             Map<String, QuestionDefinition> questions, int depth, List<String> problems) {
        if (depth > MAX_DEPTH) {
            problems.add("expression is nested deeper than " + MAX_DEPTH + " levels");
            return;
        }
        for (Operand operand : node.ownOperands()) {
            checkReference(operand, signals, questions, problems);
        }
        switch (node) {
            case Condition.NumericComparison n -> {
                requireNumeric(n.operand(), n.operator(), signals, problems);
                requireRange(n.value(), n.operator().name(), problems);
            }
            case Condition.Between b -> {
                requireNumeric(b.operand(), b.operator(), signals, problems);
                requireRange(b.low(), "BETWEEN low", problems);
                requireRange(b.high(), "BETWEEN high", problems);
            }
            case Condition.InList l -> {
                requireNumeric(l.operand(), l.operator(), signals, problems);
                l.values().forEach(v -> requireRange(v, l.operator().name(), problems));
            }
            case Condition.Gap g -> {
                requireNumeric(g.left(), g.operator(), signals, problems);
                requireNumeric(g.right(), g.operator(), signals, problems);
                requireRange(g.threshold(), g.operator().name(), problems);
            }
            case Condition.BooleanComparison b -> {
                if (!(b.operand() instanceof Operand.SignalOperand s) || !isType(s, SignalDataType.BOOLEAN, signals)) {
                    problems.add(b.operator() + " on a boolean needs a BOOLEAN signal but got " + b.operand().key());
                }
            }
            case Condition.Contains c -> {
                if (!signals.containsKey(c.signalId())) {
                    problems.add("CONTAINS references unknown signal " + c.signalId());
                }
            }
            case Condition.Existence e -> { }
            case Condition.And a -> a.children().forEach(c -> walk(c, signals, questions, depth + 1, problems));
            case Condition.Or o -> o.children().forEach(c -> walk(c, signals, questions, depth + 1, problems));
            case Condition.Not n -> walk(n.child(), signals, questions, depth + 1, problems);
        }
    }

    private static void checkReference(Operand operand, Map<String, SignalDefinition> signals,
                                       Map<String, QuestionDefinition> questions, List<String> problems) {
        switch (operand) {
            case Operand.SignalOperand s -> {
                if (!signals.containsKey(s.signalId())) {
                    problems.add("unknown signal " + s.signalId());
                }
            }
            case Operand.QuestionSignalsOperand q -> {
                QuestionDefinition question = questions.get(q.questionId());
                if (question == null) {
                    problems.add("unknown question " + q.questionId());
                } else if (question.type() != QuestionType.TEXT && question.riskSignalId() == null) {
                    problems.add("question " + q.questionId() + " never produces flags");
                }
            }
            case Operand.DimensionOperand d -> { }
        }
    }

    private static void requireNumeric(Operand operand, Object operator, Map<String, SignalDefinition> signals,
                                       List<String> problems) {
        boolean numeric = operand instanceof Operand.DimensionOperand
                || (operand instanceof Operand.SignalOperand s && isType(s, SignalDataType.NUMBER, signals));
        if (!numeric && !(operand instanceof Operand.SignalOperand s2 && !signals.containsKey(s2.signalId()))) {
            problems.add(operator + " needs a numeric operand but got " + operand.key());
        }
    }

    private static boolean isType(Operand.SignalOperand operand, SignalDataType type, Map<String, SignalDefinition> signals) {
        SignalDefinition definition = signals.get(operand.signalId());
        return definition != null && definition.dataType() == type;
    }

    private static void requireRange(BigDecimal value, String what, List<String> problems) {
        if (value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            problems.add(what + " threshold " + value.toPlainString() + " is outside 0..100");
        }
    }
}
