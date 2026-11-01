package com.company.leadershipintelligence.domain.enums;

/** Supported question types (specv3.md Section 4.2). */
public enum QuestionType {
    SCALE,
    MULTIPLE_CHOICE,
    /** Several options may be chosen; the answer is a list (specv3.md Section 4.2.1, decision D49). */
    MULTI_SELECT,
    BOOLEAN,
    NUMBER,
    TEXT
}
