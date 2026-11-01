package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.TraceStage;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** One ordered stage of the trace. Every step wraps facts that the earlier stages already recorded. */
public sealed interface TraceStep {

    TraceStage stage();

    record Normalization(List<NormalizedInput> inputs) implements TraceStep {
        public Normalization {
            inputs = List.copyOf(inputs);
        }

        @Override
        public TraceStage stage() {
            return TraceStage.NORMALIZATION;
        }
    }

    record Signals(List<DerivedSignal> signals) implements TraceStep {
        public Signals {
            signals = List.copyOf(signals);
        }

        @Override
        public TraceStage stage() {
            return TraceStage.SIGNALS;
        }
    }

    record RawDimensions(List<DimensionTrace> dimensions) implements TraceStep {
        public RawDimensions {
            dimensions = List.copyOf(dimensions);
        }

        @Override
        public TraceStage stage() {
            return TraceStage.RAW_DIMENSIONS;
        }
    }

    record Imbalances(List<ImbalanceResult> imbalances) implements TraceStep {
        public Imbalances {
            imbalances = List.copyOf(imbalances);
        }

        @Override
        public TraceStage stage() {
            return TraceStage.IMBALANCES;
        }
    }

    record Patterns(List<PatternEvaluation> patterns) implements TraceStep {
        public Patterns {
            patterns = List.copyOf(patterns);
        }

        @Override
        public TraceStage stage() {
            return TraceStage.PATTERNS;
        }
    }

    record Penalties(PenaltyAggregation aggregation) implements TraceStep {
        @Override
        public TraceStage stage() {
            return TraceStage.PENALTIES;
        }
    }

    record AdjustedDimensions(List<AdjustedDimension> dimensions, Map<ScoringDimension, InterpretationResult> labels)
            implements TraceStep {
        public AdjustedDimensions {
            dimensions = List.copyOf(dimensions);
            Map<ScoringDimension, InterpretationResult> copy = new EnumMap<>(ScoringDimension.class);
            copy.putAll(labels);
            labels = Collections.unmodifiableMap(copy);
        }

        @Override
        public TraceStage stage() {
            return TraceStage.ADJUSTED_DIMENSIONS;
        }
    }

    /** {@code interpretation} is null when no numeric score exists. */
    record Overall(OverallScore overall, InterpretationResult interpretation) implements TraceStep {
        @Override
        public TraceStage stage() {
            return TraceStage.OVERALL;
        }
    }
}
