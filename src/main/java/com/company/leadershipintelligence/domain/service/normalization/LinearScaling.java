package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.valueobject.Decimals;

import java.math.BigDecimal;

/** specv3.md Section 8.5 formulas, single division at scale 10 (decision D16). */
final class LinearScaling {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private LinearScaling() {
    }

    static BigDecimal apply(BigDecimal x, BigDecimal min, BigDecimal max, ScaleDirection direction) {
        BigDecimal positive = Decimals.divide(x.subtract(min).multiply(HUNDRED), max.subtract(min));
        return direction == ScaleDirection.POSITIVE ? positive : HUNDRED.subtract(positive);
    }
}
