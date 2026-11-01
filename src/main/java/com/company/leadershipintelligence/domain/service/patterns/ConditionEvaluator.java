package com.company.leadershipintelligence.domain.service.patterns;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.GapDirection;
import com.company.leadershipintelligence.domain.enums.SignalDataType;
import com.company.leadershipintelligence.domain.enums.TriState;
import com.company.leadershipintelligence.domain.model.ConditionResult;
import com.company.leadershipintelligence.domain.model.DerivedSignal;
import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.rule.Condition;
import com.company.leadershipintelligence.domain.rule.Operand;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Evaluates condition trees with three-valued (Kleene) logic (specv3.md Sections 13.4, 13.5). A missing
 * operand yields UNKNOWN and is never coerced to zero or false: AND is FALSE if any child is FALSE else
 * UNKNOWN if any is UNKNOWN; OR is TRUE if any child is TRUE else UNKNOWN if any is UNKNOWN; NOT keeps
 * UNKNOWN. EXISTS and NOT_EXISTS are never UNKNOWN. Pure and deterministic; assumes the condition was
 * validated against the configuration.
 */
public class ConditionEvaluator {

    public ConditionResult evaluate(Condition condition, EvaluationContext context) {
        return switch (condition) {
            case Condition.And a -> {
                List<ConditionResult> children = evaluateAll(a.children(), context);
                yield new ConditionResult(ConditionOperator.AND, and(children), "", children);
            }
            case Condition.Or o -> {
                List<ConditionResult> children = evaluateAll(o.children(), context);
                yield new ConditionResult(ConditionOperator.OR, or(children), "", children);
            }
            case Condition.Not n -> {
                ConditionResult child = evaluate(n.child(), context);
                yield new ConditionResult(ConditionOperator.NOT, not(child.outcome()), "", List.of(child));
            }
            case Condition.NumericComparison c -> numeric(c, context);
            case Condition.BooleanComparison c -> bool(c, context);
            case Condition.Between b -> between(b, context);
            case Condition.InList l -> inList(l, context);
            case Condition.Existence e -> existence(e, context);
            case Condition.Contains c -> contains(c, context);
            case Condition.Gap g -> gap(g, context);
        };
    }

    private List<ConditionResult> evaluateAll(List<Condition> conditions, EvaluationContext context) {
        List<ConditionResult> results = new ArrayList<>();
        for (Condition condition : conditions) {
            results.add(evaluate(condition, context));
        }
        return results;
    }

    // ---- logic ----

    private static TriState and(List<ConditionResult> children) {
        boolean unknown = false;
        for (ConditionResult child : children) {
            if (child.outcome() == TriState.FALSE) {
                return TriState.FALSE;
            }
            unknown |= child.outcome() == TriState.UNKNOWN;
        }
        return unknown ? TriState.UNKNOWN : TriState.TRUE;
    }

    private static TriState or(List<ConditionResult> children) {
        boolean unknown = false;
        for (ConditionResult child : children) {
            if (child.outcome() == TriState.TRUE) {
                return TriState.TRUE;
            }
            unknown |= child.outcome() == TriState.UNKNOWN;
        }
        return unknown ? TriState.UNKNOWN : TriState.FALSE;
    }

    private static TriState not(TriState value) {
        return switch (value) {
            case TRUE -> TriState.FALSE;
            case FALSE -> TriState.TRUE;
            case UNKNOWN -> TriState.UNKNOWN;
        };
    }

    private static TriState of(boolean value) {
        return value ? TriState.TRUE : TriState.FALSE;
    }

    // ---- leaves ----

    private ConditionResult numeric(Condition.NumericComparison c, EvaluationContext context) {
        Optional<BigDecimal> value = numericValue(c.operand(), context);
        if (value.isEmpty()) {
            return unknown(c.operator(), c.operand());
        }
        int cmp = value.get().compareTo(c.value());
        boolean result = switch (c.operator()) {
            case EQ -> cmp == 0;
            case NE -> cmp != 0;
            case GT -> cmp > 0;
            case GTE -> cmp >= 0;
            case LT -> cmp < 0;
            case LTE -> cmp <= 0;
            default -> throw new IllegalStateException("unexpected operator " + c.operator());
        };
        return new ConditionResult(c.operator(), of(result),
                c.operand().key() + "=" + value.get().toPlainString() + " " + c.operator() + " " + c.value().toPlainString(), List.of());
    }

    private ConditionResult bool(Condition.BooleanComparison c, EvaluationContext context) {
        Optional<Boolean> value = booleanValue(c.operand(), context);
        if (value.isEmpty()) {
            return unknown(c.operator(), c.operand());
        }
        boolean equal = value.get() == c.value();
        boolean result = c.operator() == ConditionOperator.EQ ? equal : !equal;
        return new ConditionResult(c.operator(), of(result),
                c.operand().key() + "=" + value.get() + " " + c.operator() + " " + c.value(), List.of());
    }

    private ConditionResult between(Condition.Between b, EvaluationContext context) {
        Optional<BigDecimal> value = numericValue(b.operand(), context);
        if (value.isEmpty()) {
            return unknown(b.operator(), b.operand());
        }
        boolean result = value.get().compareTo(b.low()) >= 0 && value.get().compareTo(b.high()) <= 0;
        return new ConditionResult(b.operator(), of(result), b.operand().key() + "=" + value.get().toPlainString()
                + " BETWEEN " + b.low().toPlainString() + " AND " + b.high().toPlainString() + " (inclusive)", List.of());
    }

    private ConditionResult inList(Condition.InList l, EvaluationContext context) {
        Optional<BigDecimal> value = numericValue(l.operand(), context);
        if (value.isEmpty()) {
            return unknown(l.operator(), l.operand());
        }
        boolean member = l.values().stream().anyMatch(v -> v.compareTo(value.get()) == 0);
        return new ConditionResult(l.operator(), of(l.negated() != member),
                l.operand().key() + "=" + value.get().toPlainString() + " " + l.operator() + " " + l.values(), List.of());
    }

    private ConditionResult existence(Condition.Existence e, EvaluationContext context) {
        boolean exists = context.available(e.operand());
        return new ConditionResult(e.operator(), of(e.negated() != exists),
                e.operand().key() + (exists ? " exists" : " does not exist"), List.of());
    }

    private ConditionResult contains(Condition.Contains c, EvaluationContext context) {
        Optional<Set<String>> flags = context.questionSignals(c.operand().questionId());
        if (flags.isEmpty()) {
            return unknown(c.operator(), c.operand());
        }
        boolean result = flags.get().contains(c.signalId());
        return new ConditionResult(c.operator(), of(result),
                c.operand().key() + " contains " + c.signalId() + " = " + result, List.of());
    }

    private ConditionResult gap(Condition.Gap g, EvaluationContext context) {
        Optional<BigDecimal> left = numericValue(g.left(), context);
        Optional<BigDecimal> right = numericValue(g.right(), context);
        if (left.isEmpty() || right.isEmpty()) {
            return new ConditionResult(g.operator(), TriState.UNKNOWN,
                    "missing " + (left.isEmpty() ? g.left().key() : g.right().key()), List.of());
        }
        BigDecimal difference = left.get().subtract(right.get());
        BigDecimal gap = switch (g.direction()) {
            case LEFT_MINUS_RIGHT -> difference;
            case RIGHT_MINUS_LEFT -> difference.negate();
            case ABSOLUTE -> difference.abs();
        };
        int cmp = gap.compareTo(g.threshold());
        boolean result = g.operator() == ConditionOperator.GAP_GT ? cmp > 0 : cmp >= 0;
        return new ConditionResult(g.operator(), of(result),
                "gap(" + g.direction() + ")=" + gap.toPlainString() + " " + g.operator() + " " + g.threshold().toPlainString(),
                List.of());
    }

    private static ConditionResult unknown(ConditionOperator operator, Operand operand) {
        return new ConditionResult(operator, TriState.UNKNOWN, "missing " + operand.key(), List.of());
    }

    private static Optional<BigDecimal> numericValue(Operand operand, EvaluationContext context) {
        return switch (operand) {
            case Operand.SignalOperand s -> context.signal(s.signalId()).map(signal -> {
                if (signal.dataType() != SignalDataType.NUMBER) {
                    throw new IllegalStateException(s.key() + " is not numeric");
                }
                return signal.numericValue().value();
            });
            case Operand.DimensionOperand d -> context.rawDimension(d.dimension()).map(v -> v.value());
            case Operand.QuestionSignalsOperand q -> throw new IllegalStateException(q.key() + " is not numeric");
        };
    }

    private static Optional<Boolean> booleanValue(Operand operand, EvaluationContext context) {
        if (operand instanceof Operand.SignalOperand s) {
            return context.signal(s.signalId()).map((DerivedSignal signal) -> {
                if (signal.dataType() != SignalDataType.BOOLEAN) {
                    throw new IllegalStateException(s.key() + " is not boolean");
                }
                return signal.flag();
            });
        }
        throw new IllegalStateException(operand.key() + " is not boolean");
    }
}
