package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.GapDirection;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Recursive condition expression (specv3.md Section 13.4). Structure (arity, operator/operand shape) is
 * enforced on construction; references and types are checked by {@link ConditionValidator}.
 * BETWEEN is inclusive at both ends. For GAP_*, the gap is computed per {@link GapDirection} and compared
 * to the threshold with > (GAP_GT) or >= (GAP_GTE).
 */
public sealed interface Condition {

    ConditionOperator operator();

    /** Operands referenced directly by this node. */
    List<Operand> ownOperands();

    default List<Condition> children() {
        return List.of();
    }

    /** All distinct operands in the tree, in traversal order. */
    default Set<Operand> operands() {
        Set<Operand> all = new LinkedHashSet<>(ownOperands());
        for (Condition child : children()) {
            all.addAll(child.operands());
        }
        return all;
    }

    private static boolean isOneOf(ConditionOperator operator, ConditionOperator... allowed) {
        for (ConditionOperator candidate : allowed) {
            if (candidate == operator) {
                return true;
            }
        }
        return false;
    }

    record NumericComparison(Operand operand, ConditionOperator operator, BigDecimal value) implements Condition {
        public NumericComparison {
            ConditionChecks.require(operand != null && value != null, "operand and value are required");
            ConditionChecks.require(isOneOf(operator, ConditionOperator.EQ, ConditionOperator.NE, ConditionOperator.GT,
                    ConditionOperator.GTE, ConditionOperator.LT, ConditionOperator.LTE),
                    "numeric comparison needs EQ, NE, GT, GTE, LT or LTE");
        }

        @Override
        public List<Operand> ownOperands() {
            return List.of(operand);
        }
    }

    record BooleanComparison(Operand operand, ConditionOperator operator, boolean value) implements Condition {
        public BooleanComparison {
            ConditionChecks.require(operand != null, "operand is required");
            ConditionChecks.require(isOneOf(operator, ConditionOperator.EQ, ConditionOperator.NE),
                    "boolean comparison needs EQ or NE");
        }

        @Override
        public List<Operand> ownOperands() {
            return List.of(operand);
        }
    }

    record Between(Operand operand, BigDecimal low, BigDecimal high) implements Condition {
        public Between {
            ConditionChecks.require(operand != null && low != null && high != null, "operand, low and high are required");
            ConditionChecks.require(low.compareTo(high) <= 0, "BETWEEN low must not exceed high");
        }

        @Override
        public ConditionOperator operator() {
            return ConditionOperator.BETWEEN;
        }

        @Override
        public List<Operand> ownOperands() {
            return List.of(operand);
        }
    }

    record InList(Operand operand, boolean negated, List<BigDecimal> values) implements Condition {
        public InList {
            ConditionChecks.require(operand != null && values != null && !values.isEmpty(), "operand and values are required");
            values = List.copyOf(values);
        }

        @Override
        public ConditionOperator operator() {
            return negated ? ConditionOperator.NOT_IN : ConditionOperator.IN;
        }

        @Override
        public List<Operand> ownOperands() {
            return List.of(operand);
        }
    }

    record Existence(Operand operand, boolean negated) implements Condition {
        public Existence {
            ConditionChecks.require(operand != null, "operand is required");
        }

        @Override
        public ConditionOperator operator() {
            return negated ? ConditionOperator.NOT_EXISTS : ConditionOperator.EXISTS;
        }

        @Override
        public List<Operand> ownOperands() {
            return List.of(operand);
        }
    }

    /** True when the question's detected flag set contains {@code signalId}. */
    record Contains(Operand.QuestionSignalsOperand operand, String signalId) implements Condition {
        public Contains {
            ConditionChecks.require(operand != null && signalId != null && !signalId.isBlank(),
                    "operand and signal id are required");
        }

        @Override
        public ConditionOperator operator() {
            return ConditionOperator.CONTAINS;
        }

        @Override
        public List<Operand> ownOperands() {
            return List.of(operand);
        }
    }

    record Gap(Operand left, Operand right, GapDirection direction, ConditionOperator operator, BigDecimal threshold)
            implements Condition {
        public Gap {
            ConditionChecks.require(left != null && right != null && direction != null && threshold != null,
                    "both operands, a direction and a threshold are required");
            ConditionChecks.require(!left.equals(right), "gap operands must differ");
            ConditionChecks.require(isOneOf(operator, ConditionOperator.GAP_GT, ConditionOperator.GAP_GTE),
                    "gap needs GAP_GT or GAP_GTE");
        }

        @Override
        public List<Operand> ownOperands() {
            return List.of(left, right);
        }
    }

    record And(List<Condition> children) implements Condition {
        public And {
            ConditionChecks.require(children != null && children.size() >= 2, "AND needs at least two children");
            children = List.copyOf(children);
        }

        @Override
        public ConditionOperator operator() {
            return ConditionOperator.AND;
        }

        @Override
        public List<Operand> ownOperands() {
            return List.of();
        }
    }

    record Or(List<Condition> children) implements Condition {
        public Or {
            ConditionChecks.require(children != null && children.size() >= 2, "OR needs at least two children");
            children = List.copyOf(children);
        }

        @Override
        public ConditionOperator operator() {
            return ConditionOperator.OR;
        }

        @Override
        public List<Operand> ownOperands() {
            return List.of();
        }
    }

    /** NOT has exactly one child. */
    record Not(Condition child) implements Condition {
        public Not {
            ConditionChecks.require(child != null, "NOT needs exactly one child");
        }

        @Override
        public ConditionOperator operator() {
            return ConditionOperator.NOT;
        }

        @Override
        public List<Operand> ownOperands() {
            return List.of();
        }

        @Override
        public List<Condition> children() {
            return List.of(child);
        }
    }
}
