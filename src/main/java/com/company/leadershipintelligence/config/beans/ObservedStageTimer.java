package com.company.leadershipintelligence.config.beans;

import com.company.leadershipintelligence.domain.service.StageTimer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Times engine stages as observations: each becomes a duration timer and, when a tracing bridge is present, a span
 * (specv3.md Section 34). Stage names map to the metric names of Section 34.1; other stages share one timer with a
 * {@code stage} tag. Only fixed, low-cardinality values are recorded: never a tenant, assessment or answer.
 */
public class ObservedStageTimer implements StageTimer {

    private static final Map<String, String> NAMES = Map.of(
            NORMALIZATION, "normalization.duration",
            DIMENSIONS, "dimension.scoring.duration",
            PATTERNS, "pattern.evaluation.duration",
            PENALTIES, "penalty.evaluation.duration",
            EXPLAINABILITY, "explainability.duration");

    private final ObservationRegistry registry;

    public ObservedStageTimer(ObservationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <T> T time(String stage, Supplier<T> work) {
        String name = NAMES.getOrDefault(stage, "scoring.stage.duration");
        return Observation.createNotStarted(name, registry).lowCardinalityKeyValue("stage", stage).observe(work);
    }
}
