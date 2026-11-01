package com.company.leadershipintelligence.domain.valueobject;

import java.math.BigDecimal;

/** A non-negative deduction in score points (specv3.md Section 21.1). */
public record PenaltyAmount(BigDecimal value) {

    public PenaltyAmount {
        if (value == null) {
            throw new IllegalArgumentException("PenaltyAmount must not be null");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("PenaltyAmount must not be negative but was " + value);
        }
    }

    public static final PenaltyAmount ZERO = new PenaltyAmount(BigDecimal.ZERO);

    public PenaltyAmount plus(PenaltyAmount other) {
        return new PenaltyAmount(value.add(other.value));
    }

    public PenaltyAmount min(PenaltyAmount other) {
        return value.compareTo(other.value) <= 0 ? this : other;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PenaltyAmount that && Decimals.sameValue(value, that.value);
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
