package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.ScoreStatus;

/**
 * Canonical outcome of one engine run (specv3.md Section 20): the context it ran in, every stage's facts and the
 * explanation trace. Persistence and the API are layered on top of this and add generated ids and timestamps.
 */
public record ScoringResult(ScoringContext context, ScoringFacts facts, ScoringTrace trace) {

    public OverallScore overall() {
        return facts.overall();
    }

    public ScoreStatus status() {
        return facts.overall().status();
    }
}
