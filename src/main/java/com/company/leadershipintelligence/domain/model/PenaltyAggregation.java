package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.PenaltyTarget;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** All penalty results plus the effective total per target (specv3.md Section 16). */
public record PenaltyAggregation(List<PenaltyResult> results, Map<PenaltyTarget, PenaltyAmount> effectiveByTarget,
                                 String tieBreakVersion) {

    public PenaltyAggregation {
        results = List.copyOf(results);
        Map<PenaltyTarget, PenaltyAmount> copy = new EnumMap<>(PenaltyTarget.class);
        for (PenaltyTarget target : PenaltyTarget.values()) {
            copy.put(target, effectiveByTarget.getOrDefault(target, PenaltyAmount.ZERO));
        }
        effectiveByTarget = Collections.unmodifiableMap(copy);
    }

    public PenaltyAmount total(PenaltyTarget target) {
        return effectiveByTarget.get(target);
    }
}
