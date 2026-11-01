package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;
import com.company.leadershipintelligence.domain.valueobject.ScoreValue;

import java.util.List;
import java.util.Optional;

/**
 * Leadership Intelligence Score result (specv3.md Section 17). VALID results have a weighted adjusted score
 * (the "LIS before overall penalty") and a final score; INSUFFICIENT_DATA results have neither. The overall
 * penalty is applied exactly once, after weighting; when no score is produced none is applied. PARTIAL is
 * never produced because partial scoring is disabled.
 */
public record OverallScore(
        ScoreStatus status,
        List<AdjustedDimension> dimensions,
        ScoreValue weightedAdjustedScore,
        PenaltyAmount overallPenaltyApplied,
        ScoreValue finalScore) {

    public OverallScore {
        boolean valid = status == ScoreStatus.VALID;
        if (valid != (finalScore != null) || valid != (weightedAdjustedScore != null)) {
            throw new IllegalArgumentException("scores exist exactly when the status is VALID");
        }
        if (status == ScoreStatus.PARTIAL) {
            throw new IllegalArgumentException("partial scoring is not enabled");
        }
        dimensions = List.copyOf(dimensions);
    }

    public Optional<ScoreValue> score() {
        return Optional.ofNullable(finalScore);
    }
}
