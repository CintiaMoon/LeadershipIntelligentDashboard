package com.company.leadershipintelligence.support;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records the names and low-cardinality key values of observations started while capturing, to check the span tree. */
public final class RecordingObservationHandler implements ObservationHandler<Observation.Context> {

    public record Started(String name, List<String> keys, List<String> values) {
    }

    private final List<Started> started = new CopyOnWriteArrayList<>();
    private volatile boolean capturing;

    public void start() {
        started.clear();
        capturing = true;
    }

    public List<Started> stop() {
        capturing = false;
        return List.copyOf(started);
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    @Override
    public void onStart(Observation.Context context) {
        if (capturing) {
            List<String> keys = new ArrayList<>();
            List<String> values = new ArrayList<>();
            for (KeyValue kv : context.getLowCardinalityKeyValues()) {
                keys.add(kv.getKey());
                values.add(kv.getValue());
            }
            started.add(new Started(context.getName(), keys, values));
        }
    }
}
