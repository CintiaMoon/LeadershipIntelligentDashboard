package com.company.leadershipintelligence.domain.service;

import java.util.function.Supplier;

/**
 * Lets the engine report how long each stage took without knowing how it is measured (specv3.md Section 34).
 * Timing must never change a result (Section 34.3): the supplier's value is returned untouched.
 */
public interface StageTimer {

    String NORMALIZATION = "normalization";
    String SIGNALS = "signals";
    String DIMENSIONS = "dimension-scoring";
    String IMBALANCES = "imbalance-detection";
    String PATTERNS = "pattern-evaluation";
    String PENALTIES = "penalty-evaluation";
    String OVERALL = "overall-score";
    String EXPLAINABILITY = "explainability";

    StageTimer NOOP = new StageTimer() {
        @Override
        public <T> T time(String stage, Supplier<T> work) {
            return work.get();
        }
    };

    <T> T time(String stage, Supplier<T> work);
}
