package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decimal interpretation ranges (decision D6, specv3.md Section 18). Validation requires the ranges to start
 * at 0, end at 100, touch without gaps or overlaps, and use unique labels, so every score in 0..100 maps to
 * exactly one label. LIS and dimensions may use separate configurations. Sample labels are unvalidated.
 */
public record InterpretationConfig(List<InterpretationRange> ranges) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public InterpretationConfig {
        if (ranges == null || ranges.isEmpty()) {
            throw invalid("at least one range is required");
        }
        List<InterpretationRange> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparing(InterpretationRange::min));
        if (sorted.get(0).min().signum() != 0) {
            throw invalid("the first range must start at 0");
        }
        if (sorted.get(sorted.size() - 1).max().compareTo(HUNDRED) != 0) {
            throw invalid("the last range must end at 100");
        }
        Set<String> labels = new HashSet<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (!labels.add(sorted.get(i).label())) {
                throw invalid("duplicate label " + sorted.get(i).label());
            }
            if (i > 0 && sorted.get(i).min().compareTo(sorted.get(i - 1).max()) != 0) {
                throw invalid("ranges must touch exactly: gap or overlap before " + sorted.get(i).label());
            }
        }
        ranges = List.copyOf(sorted);
    }

    public Optional<InterpretationRange> find(BigDecimal score) {
        for (int i = 0; i < ranges.size(); i++) {
            InterpretationRange range = ranges.get(i);
            boolean last = i == ranges.size() - 1;
            boolean aboveMin = score.compareTo(range.min()) >= 0;
            boolean belowMax = score.compareTo(range.max()) < 0 || (last && score.compareTo(range.max()) == 0);
            if (aboveMin && belowMax) {
                return Optional.of(range);
            }
        }
        return Optional.empty();
    }

    /** V3 Section 18 illustrative ranges as half-open decimals: 0-40, 40-60, 60-80, 80-100. */
    public static InterpretationConfig illustrative() {
        return new InterpretationConfig(List.of(
                new InterpretationRange(new BigDecimal("0"), new BigDecimal("40"), "CRITICAL"),
                new InterpretationRange(new BigDecimal("40"), new BigDecimal("60"), "AT_RISK"),
                new InterpretationRange(new BigDecimal("60"), new BigDecimal("80"), "HEALTHY"),
                new InterpretationRange(new BigDecimal("80"), HUNDRED, "STRONG")));
    }

    private static ScoringException invalid(String message) {
        return new ScoringException(ScoringErrorCode.INVALID_INTERPRETATION_CONFIGURATION, message);
    }
}
