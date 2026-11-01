package com.company.leadershipintelligence.domain.service;

import com.company.leadershipintelligence.domain.model.ScoringContext;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.model.ScoringResult;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageTimerTest {

    private static final ScoringContext CONTEXT = new ScoringContext(new TenantId("t"), new OrganizationId("o"), new AssessmentId("a"),
            new AssessmentSnapshotId("s"), new ConfigurationVersion("sample-1"), "3.0.0", "1.0.0");
    private static final List<ScoringInput> INPUTS = List.of(new ScoringInput("Q01", 2), new ScoringInput("Q02", "OFTEN"),
            new ScoringInput("Q03", true), new ScoringInput("Q04", "only one key person"), new ScoringInput("Q05", 80));

    private static final class Recording implements StageTimer {
        final List<String> stages = new ArrayList<>();

        @Override
        public <T> T time(String stage, Supplier<T> work) {
            stages.add(stage);
            return work.get();
        }
    }

    @Test
    void everyStageIsTimedOnceInPipelineOrder() {
        Recording timer = new Recording();
        ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative(), timer).score(CONTEXT, INPUTS, ConfigurationSamples.sampleSnapshot());
        assertThat(timer.stages).containsExactly(StageTimer.NORMALIZATION, StageTimer.SIGNALS, StageTimer.DIMENSIONS,
                StageTimer.IMBALANCES, StageTimer.PATTERNS, StageTimer.PENALTIES, StageTimer.OVERALL, StageTimer.EXPLAINABILITY);
    }

    @Test
    void measuringNeverChangesTheResult() {
        ScoringResult plain = ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative())
                .score(CONTEXT, INPUTS, ConfigurationSamples.sampleSnapshot());
        ScoringResult timed = ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative(), new Recording())
                .score(CONTEXT, INPUTS, ConfigurationSamples.sampleSnapshot());
        assertThat(timed).isEqualTo(plain);
    }

    @Test
    void errorsPassThroughTheTimerUnchanged() {
        StageTimer failing = new StageTimer() {
            @Override
            public <T> T time(String stage, Supplier<T> work) {
                return work.get();
            }
        };
        List<ScoringInput> bad = List.of(new ScoringInput("NOPE", 1));
        assertThatThrownBy(() -> ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative(), failing)
                .score(CONTEXT, bad, ConfigurationSamples.sampleSnapshot()))
                .isInstanceOf(com.company.leadershipintelligence.domain.exception.ScoringException.class);
    }
}
