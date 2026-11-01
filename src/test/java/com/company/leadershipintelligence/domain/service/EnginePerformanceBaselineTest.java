package com.company.leadershipintelligence.domain.service;

import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringContext;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.configuration.ConfigurationDocumentParser;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance baseline (V3 Section 35.1 sets no absolute latency target): scoring cost must grow roughly linearly
 * with the number of questions. The measured figures are printed so they can be recorded in the plan.
 */
class EnginePerformanceBaselineTest {

    private static final ScoringContext CONTEXT = new ScoringContext(new TenantId("t"), new OrganizationId("o"), new AssessmentId("a"),
            new AssessmentSnapshotId("s"), new ConfigurationVersion("perf"), "3.0.0", "1.0.0");

    private static ScoringConfigurationSnapshot snapshot(int extra) {
        ObjectNode doc = ConfigurationSamples.sampleDocument();
        ArrayNode questions = (ArrayNode) doc.get("questions");
        ArrayNode mappings = (ArrayNode) doc.get("dimensions").get("mappings");
        String[] dims = {"TEAM_HEALTH", "DELIVERY_STABILITY", "TECHNICAL_RISK", "LEADERSHIP_LOAD"};
        for (int i = 0; i < extra; i++) {
            String id = String.format("X%04d", i);
            ObjectNode q = questions.addObject();
            q.put("id", id).put("type", "SCALE").put("missingPolicy", "IGNORE");
            q.putObject("normalization").put("min", 1).put("max", 5).put("direction", "POSITIVE")
                    .put("outOfRangePolicy", "REJECT").put("allowDecimals", true);
            mappings.addObject().put("question", id).put("dimension", dims[i % 4]).put("weight", 1.0);
        }
        return new ConfigurationDocumentParser().parse(doc, new ConfigurationVersion("perf"));
    }

    private static List<ScoringInput> inputs(int extra) {
        List<ScoringInput> list = new ArrayList<>(List.of(new ScoringInput("Q01", 2), new ScoringInput("Q02", "OFTEN"),
                new ScoringInput("Q03", true), new ScoringInput("Q04", "only one key person"), new ScoringInput("Q05", 80)));
        for (int i = 0; i < extra; i++) {
            list.add(new ScoringInput(String.format("X%04d", i), 1 + (i % 5)));
        }
        return list;
    }

    private static double millisPerRun(int extra, int runs) {
        ScoringConfigurationSnapshot snap = snapshot(extra);
        List<ScoringInput> in = inputs(extra);
        ScoringOrchestrator engine = ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative());
        for (int i = 0; i < Math.max(5, runs / 5); i++) {
            engine.score(CONTEXT, in, snap);
        }
        long start = System.nanoTime();
        for (int i = 0; i < runs; i++) {
            engine.score(CONTEXT, in, snap);
        }
        return (System.nanoTime() - start) / 1_000_000.0 / runs;
    }

    @Test
    void scoringCostGrowsRoughlyLinearlyWithTheNumberOfQuestions() {
        double small = millisPerRun(50, 200);
        double large = millisPerRun(1000, 20);
        System.out.printf("PERF baseline: 55 questions = %.3f ms/run, 1005 questions = %.3f ms/run%n", small, large);
        // 20x the questions; generous headroom for noisy machines, but quadratic growth (about 400x) fails
        assertThat(large / small).as("growth factor for 20x questions").isLessThan(120.0);
        assertThat(small).as("a realistic assessment is scored in well under a second").isLessThan(1000.0);
    }
}
