package com.company.leadershipintelligence.domain.valueobject;

import java.math.BigDecimal;

/** Evidence-quality confidence in [0, 1] (specv3.md Section 14.2). */
public record ConfidenceValue(BigDecimal value) {

    public ConfidenceValue {
        value = Decimals.requireInRange("ConfidenceValue", value, new BigDecimal("0"), new BigDecimal("1"));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ConfidenceValue that && Decimals.sameValue(value, that.value);
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
