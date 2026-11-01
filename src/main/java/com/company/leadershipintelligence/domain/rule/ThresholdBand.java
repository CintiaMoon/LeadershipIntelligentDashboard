package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;

import java.math.BigDecimal;

/**
 * One band of a THRESHOLD_BASED schedule. Every boundary states whether it is inclusive (specv3.md
 * Section 15.3). {@code to} may be null for an open-ended top band.
 */
public record ThresholdBand(BigDecimal from, boolean fromInclusive, BigDecimal to, boolean toInclusive, PenaltyAmount deduction) {

    public ThresholdBand {
        PenaltyChecks.require(from != null && deduction != null, "band needs a lower bound and a deduction");
        if (to != null) {
            int cmp = from.compareTo(to);
            PenaltyChecks.require(cmp < 0 || (cmp == 0 && fromInclusive && toInclusive), "band bounds are empty or reversed");
        } else {
            PenaltyChecks.require(!toInclusive, "an open-ended band cannot have an inclusive upper bound");
        }
    }

    public boolean contains(BigDecimal value) {
        int low = value.compareTo(from);
        if (low < 0 || (low == 0 && !fromInclusive)) {
            return false;
        }
        if (to == null) {
            return true;
        }
        int high = value.compareTo(to);
        return high < 0 || (high == 0 && toInclusive);
    }

    /** True when the two bands share at least one value. */
    public boolean overlaps(ThresholdBand other) {
        return startsBeforeEnd(this, other) && startsBeforeEnd(other, this);
    }

    private static boolean startsBeforeEnd(ThresholdBand a, ThresholdBand b) {
        if (b.to == null) {
            return true;
        }
        int cmp = a.from.compareTo(b.to);
        return cmp < 0 || (cmp == 0 && a.fromInclusive && b.toInclusive);
    }
}
