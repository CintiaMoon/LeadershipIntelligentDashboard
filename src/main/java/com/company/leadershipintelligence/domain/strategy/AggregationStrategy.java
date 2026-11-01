package com.company.leadershipintelligence.domain.strategy;

import java.util.List;

/** Dimension aggregation algorithm (specv3.md Section 10.5). New strategies need an algorithm version and tests. */
public interface AggregationStrategy {

    AggregationResult aggregate(List<WeightedContribution> contributions, AggregationConfiguration configuration);
}
