package com.company.leadershipintelligence.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Numeric policy (decision D16, specv3.md Section 31): scale 10 HALF_UP for division, scale 2
 * HALF_UP at defined rounding stages, no other intermediate rounding.
 */
public final class Decimals {

    public static final int DIVISION_SCALE = 10;
    public static final int SCORE_SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final String ROUNDING_POLICY_VERSION = "1";

    private Decimals() {
    }

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, DIVISION_SCALE, ROUNDING);
    }

    public static BigDecimal roundScore(BigDecimal value) {
        return value.setScale(SCORE_SCALE, ROUNDING);
    }

    /** Numeric equality independent of scale (2.0 equals 2.00). */
    static boolean sameValue(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) == 0;
    }

    static int hash(BigDecimal value) {
        return value.signum() == 0 ? 0 : value.stripTrailingZeros().hashCode();
    }

    static BigDecimal requireInRange(String name, BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + " but was " + value);
        }
        return value;
    }
}
