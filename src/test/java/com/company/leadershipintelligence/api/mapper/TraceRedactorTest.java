package com.company.leadershipintelligence.api.mapper;

import com.company.leadershipintelligence.domain.model.ScoringContext;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.service.ScoringOrchestrator;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.company.leadershipintelligence.repository.persistence.mapper.TraceJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class TraceRedactorTest {

    private static JsonNode trace() throws Exception {
        ScoringContext context = new ScoringContext(new TenantId("t"), new OrganizationId("o"), new AssessmentId("a"),
                new AssessmentSnapshotId("s"), new ConfigurationVersion("sample-1"), "3.0.0", "1.0.0");
        String json = TraceJson.write(ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative()).score(context,
                List.of(new ScoringInput("Q01", 2), new ScoringInput("Q02", "OFTEN"), new ScoringInput("Q03", true),
                        new ScoringInput("Q04", "only one key person"), new ScoringInput("Q05", 80)),
                ConfigurationSamples.sampleSnapshot()).trace());
        return new ObjectMapper().readTree(json);
    }

    private static List<String> stages(JsonNode trace) {
        return StreamSupport.stream(trace.get("steps").spliterator(), false).map(s -> s.get("stage").asText()).toList();
    }

    private static JsonNode step(JsonNode trace, String stage) {
        for (JsonNode step : trace.get("steps")) {
            if (stage.equals(step.get("stage").asText())) {
                return step;
            }
        }
        throw new AssertionError(stage);
    }

    @Test
    void perQuestionAndPerSignalStepsAreRemoved() throws Exception {
        JsonNode redacted = TraceRedactor.trace(trace());
        assertThat(stages(redacted)).containsExactly("RAW_DIMENSIONS", "IMBALANCES", "PATTERNS", "PENALTIES", "ADJUSTED_DIMENSIONS", "OVERALL");
        assertThat(redacted.get("header").get("scoreStatus").asText()).isEqualTo("VALID");
    }

    @Test
    void contributionsAndDriversAreRemovedFromDimensions() throws Exception {
        JsonNode dimensions = step(TraceRedactor.trace(trace()), "RAW_DIMENSIONS").get("dimensions");
        for (JsonNode d : dimensions) {
            assertThat(d.has("positiveDrivers")).isFalse();
            assertThat(d.has("negativeDrivers")).isFalse();
            assertThat(d.get("score").has("contributions")).isFalse();
            assertThat(d.get("score").get("rawScore")).isNotNull();
            assertThat(d.get("score").get("coverage")).isNotNull();
        }
    }

    @Test
    void evidenceValuesAndConditionDetailsAreRemovedButOutcomesStay() throws Exception {
        JsonNode pattern = step(TraceRedactor.trace(trace()), "PATTERNS").get("patterns").get(0);
        assertThat(pattern.get("status").asText()).isEqualTo("MATCHED");
        assertThat(pattern.get("evidence")).hasSize(2);
        for (JsonNode evidence : pattern.get("evidence")) {
            assertThat(evidence.has("numericValue")).isFalse();
            assertThat(evidence.has("flag")).isFalse();
            assertThat(evidence.has("sourceQuestionIds")).isFalse();
            assertThat(evidence.get("operandKey").asText()).startsWith("signal:");
        }
        JsonNode condition = pattern.get("conditionResult");
        assertThat(condition.get("outcome").asText()).isEqualTo("TRUE");
        assertThat(condition.has("detail")).isFalse();
        for (JsonNode child : condition.get("children")) {
            assertThat(child.has("detail")).isFalse();
            assertThat(child.get("outcome").asText()).isEqualTo("TRUE");
        }
    }

    @Test
    void penaltiesKeepTheirAmountsAndLoseOnlyTheScoreBaseValue() throws Exception {
        JsonNode penalty = step(TraceRedactor.trace(trace()), "PENALTIES").get("aggregation").get("results").get(0);
        assertThat(penalty.has("scoreBaseValue")).isFalse();
        assertThat(penalty.get("status").asText()).isEqualTo("APPLIED");
        assertThat(penalty.get("effectiveDeduction").decimalValue()).isEqualByComparingTo("12");
    }

    @Test
    void theOriginalIsNeverModified() throws Exception {
        JsonNode original = trace();
        JsonNode before = original.deepCopy();
        TraceRedactor.trace(original);
        TraceRedactor.dimensions(step(original, "RAW_DIMENSIONS").get("dimensions"));
        TraceRedactor.patterns(step(original, "PATTERNS").get("patterns"));
        assertThat(original).isEqualTo(before);
        assertThat(stages(original)).hasSize(8);
    }

    @Test
    void theRedactedTraceCarriesNoRawAnswersOrQuestionValues() throws Exception {
        String text = TraceRedactor.trace(trace()).toString();
        assertThat(text).doesNotContain("key person").doesNotContain("\"questionId\"").doesNotContain("sourceQuestionIds");
    }
}
