package com.company.leadershipintelligence.domain.service.aggregation;

import com.company.leadershipintelligence.domain.strategy.AggregationConfiguration;
import com.company.leadershipintelligence.domain.strategy.AggregationResult;
import com.company.leadershipintelligence.domain.strategy.AggregationStrategy;
import com.company.leadershipintelligence.domain.strategy.WeightedContribution;
import com.company.leadershipintelligence.domain.valueobject.Decimals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Weighted average (specv3.md Section 10.3, decision D15).
 *
 * <p>The score is the closed form sum(value x weight) / sum(weight) as one division at scale 10, so
 * rounding ties at two decimals are decided on an exact value. Each share is
 * value x weight / sum(weight) at scale 10; the sub-10^-9 rounding residual is added to the largest
 * share so that the shares sum exactly to the score. {@code normalizedWeight} is weight / sum(weight)
 * at scale 10.
 */
public class WeightedAverageStrategy implements AggregationStrategy {

    @Override
    public AggregationResult aggregate(List<WeightedContribution> contributions, AggregationConfiguration configuration) {
        if (contributions.isEmpty()) {
            throw new IllegalArgumentException("at least one contribution is required");
        }
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal numerator = BigDecimal.ZERO;
        for (WeightedContribution c : contributions) {
            totalWeight = totalWeight.add(c.weight().value());
            numerator = numerator.add(c.value().value().multiply(c.weight().value()));
        }
        BigDecimal score = Decimals.divide(numerator, totalWeight);

        BigDecimal[] normalizedWeights = new BigDecimal[contributions.size()];
        BigDecimal[] amounts = new BigDecimal[contributions.size()];
        BigDecimal sum = BigDecimal.ZERO;
        int largest = 0;
        for (int i = 0; i < contributions.size(); i++) {
            WeightedContribution c = contributions.get(i);
            normalizedWeights[i] = Decimals.divide(c.weight().value(), totalWeight);
            amounts[i] = Decimals.divide(c.value().value().multiply(c.weight().value()), totalWeight);
            sum = sum.add(amounts[i]);
            if (amounts[i].compareTo(amounts[largest]) > 0) {
                largest = i;
            }
        }
        amounts[largest] = amounts[largest].add(score.subtract(sum));

        List<AggregationResult.Share> shares = new ArrayList<>();
        for (int i = 0; i < contributions.size(); i++) {
            shares.add(new AggregationResult.Share(contributions.get(i).questionId(), normalizedWeights[i], amounts[i]));
        }
        return new AggregationResult(score, shares);
    }
}
