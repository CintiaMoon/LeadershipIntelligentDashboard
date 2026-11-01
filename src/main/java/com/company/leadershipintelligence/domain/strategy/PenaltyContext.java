package com.company.leadershipintelligence.domain.strategy;

import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.model.PatternEvaluation;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;

/** Inputs a penalty strategy may read: the triggering finding and the read-only evaluation context. */
public record PenaltyContext(PenaltyDefinition definition, PatternEvaluation trigger, EvaluationContext evaluation) {
}
