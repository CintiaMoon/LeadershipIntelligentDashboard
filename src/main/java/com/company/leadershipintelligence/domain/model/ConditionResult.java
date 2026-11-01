package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.TriState;

import java.util.List;

/** Evaluated condition tree for the explanation trace (specv3.md Section 19.2). Details hold no raw answers. */
public record ConditionResult(ConditionOperator operator, TriState outcome, String detail, List<ConditionResult> children) {

    public ConditionResult {
        children = List.copyOf(children);
    }
}
