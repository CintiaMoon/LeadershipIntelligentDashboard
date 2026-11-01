package com.company.leadershipintelligence.domain.enums;

/** Group and leaf operators (specv3.md Section 13.4). */
public enum ConditionOperator {
    EQ,
    NE,
    GT,
    GTE,
    LT,
    LTE,
    BETWEEN,
    IN,
    NOT_IN,
    EXISTS,
    NOT_EXISTS,
    CONTAINS,
    GAP_GT,
    GAP_GTE,
    AND,
    OR,
    NOT;

    /** True for the 14 leaf operators; false for AND, OR, NOT. */
    public boolean isLeaf() {
        return this != AND && this != OR && this != NOT;
    }
}
