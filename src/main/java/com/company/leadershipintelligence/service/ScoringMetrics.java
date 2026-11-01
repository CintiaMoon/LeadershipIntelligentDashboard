package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * The counters and the duration timer of a scoring request (specv3.md Section 34.1): scoring_requests_total,
 * scoring_success_total, scoring_failures_total, scoring_duration_seconds and insufficient_data_total. Tags are
 * fixed small sets (the source, the error code); never a tenant, assessment, run or answer. Measuring cannot change a
 * result and an observation failure never hides the original error.
 */
@Component
public class ScoringMetrics {

    private final MeterRegistry meters;
    private final ObservationRegistry observations;

    public ScoringMetrics(MeterRegistry meters, ObservationRegistry observations) {
        this.meters = meters;
        this.observations = observations;
    }

    public <T> T request(String source, Supplier<T> work, java.util.function.Function<T, ScoreStatus> status) {
        meters.counter("scoring.requests", "source", source).increment();
        try {
            T result = Observation.createNotStarted("scoring.duration", observations).lowCardinalityKeyValue("source", source).observe(work);
            meters.counter("scoring.success", "source", source).increment();
            if (status.apply(result) == ScoreStatus.INSUFFICIENT_DATA) {
                meters.counter("insufficient.data", "source", source).increment();
            }
            return result;
        } catch (RuntimeException e) {
            String code = e instanceof ScoringException scoring ? scoring.code().name() : "UNEXPECTED";
            meters.counter("scoring.failures", "source", source, "code", code).increment();
            throw e;
        }
    }

    /** A named, low-cardinality observation (a span when tracing is enabled). */
    public <T> T observe(String name, Supplier<T> work) {
        return Observation.createNotStarted(name, observations).observe(work);
    }
}
