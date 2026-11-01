package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.PenaltyStrategyType;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Strategy configuration for the five penalty strategies (specv3.md Section 15.3). Sample values are unvalidated. */
public sealed interface StrategyConfig {

    PenaltyStrategyType type();

    /** FIXED: deduct a configured number of points. */
    record Fixed(PenaltyAmount deduction) implements StrategyConfig {
        public Fixed {
            PenaltyChecks.require(deduction != null, "FIXED needs a deduction");
        }

        @Override
        public PenaltyStrategyType type() {
            return PenaltyStrategyType.FIXED;
        }
    }

    /** PROPORTIONAL: deduction = score base x rate, rate in [0, 1]. */
    record Proportional(ScoreBase base, BigDecimal rate) implements StrategyConfig {
        public Proportional {
            PenaltyChecks.require(base != null, "PROPORTIONAL needs an explicit score base");
            PenaltyChecks.require(rate != null && rate.signum() >= 0 && rate.compareTo(BigDecimal.ONE) <= 0,
                    "PROPORTIONAL rate must be between 0 and 1");
        }

        @Override
        public PenaltyStrategyType type() {
            return PenaltyStrategyType.PROPORTIONAL;
        }
    }

    /** SEVERITY_BASED: deduction = base deduction x multiplier of the triggering pattern's severity. */
    record SeverityBased(PenaltyAmount baseDeduction, Map<Severity, BigDecimal> multipliers) implements StrategyConfig {
        public SeverityBased {
            PenaltyChecks.require(baseDeduction != null && multipliers != null, "SEVERITY_BASED needs a base and multipliers");
            Map<Severity, BigDecimal> copy = new EnumMap<>(Severity.class);
            copy.putAll(multipliers);
            for (Severity severity : Severity.values()) {
                BigDecimal m = copy.get(severity);
                PenaltyChecks.require(m != null && m.signum() >= 0, "SEVERITY_BASED needs a non-negative multiplier for " + severity);
            }
            multipliers = Collections.unmodifiableMap(copy);
        }

        @Override
        public PenaltyStrategyType type() {
            return PenaltyStrategyType.SEVERITY_BASED;
        }
    }

    /** THRESHOLD_BASED: a piecewise schedule over an explicit metric; bands must not overlap. */
    record ThresholdBased(ScoreBase metric, List<ThresholdBand> bands) implements StrategyConfig {
        public ThresholdBased {
            PenaltyChecks.require(metric != null && bands != null && !bands.isEmpty(), "THRESHOLD_BASED needs a metric and bands");
            List<ThresholdBand> sorted = new ArrayList<>(bands);
            sorted.sort(Comparator.comparing(ThresholdBand::from));
            for (int i = 0; i < sorted.size(); i++) {
                for (int j = i + 1; j < sorted.size(); j++) {
                    PenaltyChecks.require(!sorted.get(i).overlaps(sorted.get(j)), "THRESHOLD_BASED bands overlap ambiguously");
                }
            }
            bands = List.copyOf(sorted);
        }

        @Override
        public PenaltyStrategyType type() {
            return PenaltyStrategyType.THRESHOLD_BASED;
        }
    }

    /** CONDITIONAL: when the condition holds, delegate to another (non-conditional) strategy. */
    record Conditional(Condition condition, StrategyConfig delegate) implements StrategyConfig {
        public Conditional {
            PenaltyChecks.require(condition != null && delegate != null, "CONDITIONAL needs a condition and a delegate");
            PenaltyChecks.require(!(delegate instanceof Conditional), "CONDITIONAL cannot delegate to another CONDITIONAL");
        }

        @Override
        public PenaltyStrategyType type() {
            return PenaltyStrategyType.CONDITIONAL;
        }
    }
}
