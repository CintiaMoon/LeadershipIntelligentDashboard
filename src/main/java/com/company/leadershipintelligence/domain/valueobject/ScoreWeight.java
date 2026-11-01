package com.company.leadershipintelligence.domain.valueobject;

import java.math.BigDecimal;

/** A non-negative weight (specv3.md Section 21.1). */
public record ScoreWeight(BigDecimal value) {

    public ScoreWeight {
        if (value == null) {
            throw new IllegalArgumentException("ScoreWeight must not be null");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("ScoreWeight must not be negative but was " + value);
        }
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ScoreWeight that && Decimals.sameValue(value, that.value);
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
