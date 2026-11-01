package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Dimension weights of the overall score (specv3.md Sections 17.2, 17.4). All four dimensions need a
 * non-negative weight and the weights must sum to 1.0 within {@code tolerance}. Weights are configuration,
 * never constants. Sample values are illustrative and unvalidated.
 */
public record OverallWeights(Map<ScoringDimension, ScoreWeight> weights, BigDecimal tolerance) {

    public OverallWeights {
        if (weights == null || tolerance == null || tolerance.signum() < 0) {
            throw invalid("weights and a non-negative tolerance are required");
        }
        Map<ScoringDimension, ScoreWeight> copy = new EnumMap<>(ScoringDimension.class);
        copy.putAll(weights);
        BigDecimal sum = BigDecimal.ZERO;
        for (ScoringDimension dimension : ScoringDimension.values()) {
            ScoreWeight weight = copy.get(dimension);
            if (weight == null) {
                throw invalid("no overall weight for " + dimension);
            }
            sum = sum.add(weight.value());
        }
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(tolerance) > 0) {
            throw invalid("overall weights sum to " + sum.toPlainString() + ", expected 1.0 within " + tolerance.toPlainString());
        }
        weights = Collections.unmodifiableMap(copy);
    }

    public ScoreWeight weight(ScoringDimension dimension) {
        return weights.get(dimension);
    }

    private static ScoringException invalid(String message) {
        return new ScoringException(ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION, message);
    }
}
