package com.company.leadershipintelligence.domain.model;

import java.util.List;

/**
 * A raw dimension with its coverage and contributions (the score record itself) plus driver lists taken from
 * the recorded contributions: positive drivers have a normalized value above the dimension score, negative
 * ones below it, each ordered by value and then question id (specv3.md Section 19.2). No new score is computed.
 */
public record DimensionTrace(DimensionScore score, List<String> positiveDrivers, List<String> negativeDrivers) {

    public DimensionTrace {
        positiveDrivers = List.copyOf(positiveDrivers);
        negativeDrivers = List.copyOf(negativeDrivers);
    }
}
