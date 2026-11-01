package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.TraceStage;

import java.util.List;

/**
 * The complete, ordered calculation record of one run (specv3.md Sections 4.9, 19; decision D24). It holds no
 * raw answers. The explanation layer serializes this and never recalculates. All eight stages must be present
 * exactly once, in stage order.
 */
public record ScoringTrace(String traceVersion, TraceHeader header, List<TraceStep> steps) {

    public ScoringTrace {
        steps = List.copyOf(steps);
        TraceStage[] stages = TraceStage.values();
        if (steps.size() != stages.length) {
            throw new IllegalArgumentException("a trace needs exactly " + stages.length + " steps");
        }
        for (int i = 0; i < stages.length; i++) {
            if (steps.get(i).stage() != stages[i]) {
                throw new IllegalArgumentException("trace step " + i + " must be " + stages[i]);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends TraceStep> T step(Class<T> type) {
        return steps.stream().filter(type::isInstance).map(s -> (T) s).findFirst().orElseThrow();
    }
}
