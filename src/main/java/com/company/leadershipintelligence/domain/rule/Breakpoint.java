package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;

import java.math.BigDecimal;

/** One point of a piecewise-linear NUMBER mapping. */
public record Breakpoint(BigDecimal input, NormalizedValue output) {

    public Breakpoint {
        ConfigChecks.require(input != null && output != null, "breakpoint input and output are required");
    }
}
