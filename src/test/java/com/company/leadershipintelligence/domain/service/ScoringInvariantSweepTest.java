package com.company.leadershipintelligence.domain.service;

import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.domain.model.AdjustedDimension;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sweeps many seeded answer sets through the engine and checks the global invariants of specv3.md Section 39.12:
 * scores stay in 0..100, a penalty never raises a score, adjusted never exceeds raw, the final score never exceeds
 * the weighted score, missing answers never become zero scores, and identical input gives an identical result.
 */
class ScoringInvariantSweepTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final ScoringContext CONTEXT = new ScoringContext(new TenantId("t"), new OrganizationId("o"), new AssessmentId("a"),
            new AssessmentSnapshotId("s"), new ConfigurationVersion("sample-1"), "3.0.0", "1.0.0");
    private static final String[] OPTIONS = {"NEVER", "RARELY", "SOMETIMES", "OFTEN", "ALWAYS"};
    private static final String[] TEXTS = {"only one key person", "shared ownership", "", "no concerns"};

    private static List<ScoringInput> random(Random random) {
        List<ScoringInput> inputs = new ArrayList<>();
        if (random.nextInt(10) > 0) {
            inputs.add(new ScoringInput("Q01", BigDecimal.valueOf(100 + random.nextInt(401), 2)));
        }
        if (random.nextInt(10) > 0) {
            inputs.add(new ScoringInput("Q02", OPTIONS[random.nextInt(OPTIONS.length)]));
        }
        if (random.nextInt(10) > 0) {
            inputs.add(new ScoringInput("Q03", random.nextBoolean()));
        }
        if (random.nextInt(10) > 0) {
            inputs.add(new ScoringInput("Q04", TEXTS[random.nextInt(TEXTS.length)]));
        }
        if (random.nextInt(10) > 0) {
            inputs.add(new ScoringInput("Q05", random.nextInt(101)));
        }
        return inputs;
    }

    private static void inRange(BigDecimal value) {
        assertThat(value).isBetween(BigDecimal.ZERO, HUNDRED);
    }

    @Test
    void invariantsHoldForManyInputSetsAndRunsAreReproducible() {
        ScoringOrchestrator engine = ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative());
        Random random = new Random(20261101L);
        int valid = 0;
        int insufficient = 0;
        for (int i = 0; i < 500; i++) {
            List<ScoringInput> inputs = random(random);
            ScoringResult result = engine.score(CONTEXT, inputs, ConfigurationSamples.sampleSnapshot());
            assertThat(engine.score(CONTEXT, inputs, ConfigurationSamples.sampleSnapshot())).as("replay %d", i).isEqualTo(result);

            result.facts().normalizedInputs().forEach(n -> {
                n.value().ifPresent(v -> inRange(v.value()));
            });
            result.facts().dimensionScores().forEach(d -> {
                if (d.rawScore() != null) {
                    inRange(d.rawScore().value());
                }
            });
            var overall = result.facts().overall();
            for (AdjustedDimension d : overall.dimensions()) {
                if (d.rawScore() == null) {
                    // a missing dimension has no score at all, never a zero
                    assertThat(d.adjustedScore()).isNull();
                    continue;
                }
                inRange(d.adjustedScore().value());
                assertThat(d.adjustedScore().value()).isLessThanOrEqualTo(d.rawScore().value());
                assertThat(d.dimensionPenalty().value().signum()).isGreaterThanOrEqualTo(0);
            }
            if (overall.status() == ScoreStatus.VALID) {
                valid++;
                inRange(overall.finalScore().value());
                inRange(overall.weightedAdjustedScore().value());
                assertThat(overall.finalScore().value()).isLessThanOrEqualTo(overall.weightedAdjustedScore().value());
            } else {
                insufficient++;
                assertThat(overall.finalScore()).isNull();
            }
        }
        assertThat(valid).as("the sweep covers scored runs").isGreaterThan(100);
        assertThat(insufficient).as("the sweep covers runs with missing answers").isGreaterThan(20);
    }
}
