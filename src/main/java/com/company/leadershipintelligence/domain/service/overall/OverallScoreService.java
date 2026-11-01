package com.company.leadershipintelligence.domain.service.overall;

import com.company.leadershipintelligence.domain.enums.DimensionStatus;
import com.company.leadershipintelligence.domain.enums.PenaltyTarget;
import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.model.AdjustedDimension;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.OverallScore;
import com.company.leadershipintelligence.domain.model.PenaltyAggregation;
import com.company.leadershipintelligence.domain.rule.OverallWeights;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;
import com.company.leadershipintelligence.domain.valueobject.ScoreValue;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Overall Leadership Intelligence Score (specv3.md Section 17, decisions D16, D18):
 * adjusted = max(0, raw - dimension penalty); weighted = sum(adjusted x weight), rounded once to 2 decimals;
 * final = clamp(weighted - overall penalty, 0, 100). The overall penalty is applied once, after weighting. Any
 * insufficient dimension makes the score INSUFFICIENT_DATA with no number; partial scoring is disabled.
 */
public class OverallScoreService {

    public OverallScore calculate(List<DimensionScore> dimensionScores, PenaltyAggregation penalties, OverallWeights weights) {
        Map<ScoringDimension, DimensionScore> byDimension = new EnumMap<>(ScoringDimension.class);
        dimensionScores.forEach(s -> byDimension.put(s.dimension(), s));
        List<AdjustedDimension> adjusted = new ArrayList<>();
        BigDecimal weighted = BigDecimal.ZERO;
        boolean complete = true;
        for (ScoringDimension dimension : ScoringDimension.values()) {
            DimensionScore raw = byDimension.get(dimension);
            if (raw == null) {
                throw new IllegalArgumentException("missing dimension score for " + dimension);
            }
            PenaltyAmount penalty = penalties.total(PenaltyTarget.valueOf(dimension.name()));
            ScoreWeight weight = weights.weight(dimension);
            if (raw.status() != DimensionStatus.VALID) {
                complete = false;
                adjusted.add(new AdjustedDimension(dimension, null, penalty, null, weight, null));
                continue;
            }
            ScoreValue adjustedScore = ScoreValue.clamp(raw.rawScore().value().subtract(penalty.value())).rounded();
            BigDecimal contribution = adjustedScore.value().multiply(weight.value());
            weighted = weighted.add(contribution);
            adjusted.add(new AdjustedDimension(dimension, raw.rawScore(), penalty, adjustedScore, weight, contribution));
        }
        if (!complete) {
            return new OverallScore(ScoreStatus.INSUFFICIENT_DATA, adjusted, null, PenaltyAmount.ZERO, null);
        }
        ScoreValue weightedAdjusted = ScoreValue.clamp(weighted).rounded();
        PenaltyAmount overall = penalties.total(PenaltyTarget.OVERALL);
        ScoreValue finalScore = ScoreValue.clamp(weightedAdjusted.value().subtract(overall.value())).rounded();
        return new OverallScore(ScoreStatus.VALID, adjusted, weightedAdjusted, overall, finalScore);
    }
}
