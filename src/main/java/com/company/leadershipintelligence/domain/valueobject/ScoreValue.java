package com.company.leadershipintelligence.domain.valueobject;

import java.math.BigDecimal;

/** A dimension score or LIS in [0, 100] (specv3.md Section 21.1). */
public record ScoreValue(BigDecimal value) {

    public ScoreValue {
        value = Decimals.requireInRange("ScoreValue", value, new BigDecimal("0"), new BigDecimal("100"));
    }

    public static ScoreValue of(String value) {
        return new ScoreValue(new BigDecimal(value));
    }

    /** Rounds to the persisted scale (decision D16). */
    public ScoreValue rounded() {
        return new ScoreValue(Decimals.roundScore(value));
    }

    /** Clamps an arbitrary value into [0, 100]. */
    public static ScoreValue clamp(BigDecimal raw) {
        if (raw.signum() < 0) {
            return new ScoreValue(BigDecimal.ZERO);
        }
        if (raw.compareTo(new BigDecimal("100")) > 0) {
            return new ScoreValue(new BigDecimal("100"));
        }
        return new ScoreValue(raw);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ScoreValue that && Decimals.sameValue(value, that.value);
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
