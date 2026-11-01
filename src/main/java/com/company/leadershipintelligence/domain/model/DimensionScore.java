package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.DimensionStatus;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.valueobject.ScoreValue;

import java.util.List;
import java.util.Optional;

/**
 * Raw score of one dimension (specv3.md Section 10). The score exists exactly when the status is VALID;
 * an insufficient dimension is never a fabricated number (invariant 24). The score is rounded once to
 * 2 decimals (decision D16).
 */
public record DimensionScore(
        ScoringDimension dimension,
        DimensionStatus status,
        ScoreValue rawScore,
        DimensionCoverage coverage,
        List<DimensionContribution> contributions) {

    public DimensionScore {
        if ((status == DimensionStatus.VALID) != (rawScore != null)) {
            throw new IllegalArgumentException("rawScore is present exactly when the dimension is VALID");
        }
        contributions = List.copyOf(contributions);
    }

    public Optional<ScoreValue> score() {
        return Optional.ofNullable(rawScore);
    }
}
