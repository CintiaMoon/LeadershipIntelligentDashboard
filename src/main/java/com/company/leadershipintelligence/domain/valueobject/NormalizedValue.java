package com.company.leadershipintelligence.domain.valueobject;

import java.math.BigDecimal;

/** A normalized signal value in [0, 100] (specv3.md Section 8.1). */
public record NormalizedValue(BigDecimal value) {

    public NormalizedValue {
        value = Decimals.requireInRange("NormalizedValue", value, new BigDecimal("0"), new BigDecimal("100"));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NormalizedValue that && Decimals.sameValue(value, that.value);
    }

    @Override
    public int hashCode() {
        return Decimals.hash(value);
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
