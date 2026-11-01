package com.company.leadershipintelligence.repository.persistence.mapper;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.ScoringContext;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.model.ScoringResult;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.service.ScoringOrchestrator;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceJsonTest {

    private static ScoringResult run() {
        ScoringContext context = new ScoringContext(new TenantId("t"), new OrganizationId("o"), new AssessmentId("a"),
                new AssessmentSnapshotId("s"), new ConfigurationVersion("sample-1"), "3.0.0", "1.0.0");
        return ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative()).score(context,
                List.of(new ScoringInput("Q01", 2), new ScoringInput("Q02", "OFTEN"), new ScoringInput("Q03", true),
                        new ScoringInput("Q04", "only one key person"), new ScoringInput("Q05", 80)), ConfigurationSamples.sampleSnapshot());
    }

    private static JsonNode parse(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }

    @Test
    void stepsAreTaggedWithTheirStageInOrder() throws Exception {
        JsonNode trace = parse(TraceJson.write(run().trace()));
        assertThat(trace.get("traceVersion").asText()).isEqualTo("1");
        assertThat(StreamSupport.stream(trace.get("steps").spliterator(), false).map(s -> s.get("stage").asText()))
                .containsExactly("NORMALIZATION", "SIGNALS", "RAW_DIMENSIONS", "IMBALANCES", "PATTERNS", "PENALTIES",
                        "ADJUSTED_DIMENSIONS", "OVERALL");
        assertThat(trace.get("header").get("configurationVersion").asText()).isEqualTo("sample-1");
        assertThat(trace.get("header").get("scoreStatus").asText()).isEqualTo("VALID");
    }

    @Test
    void valueObjectsAreWrittenAsPlainDecimalsNotObjects() {
        String json = TraceJson.write(run().trace());
        assertThat(json).contains("\"finalScore\":67.00").contains("\"weightedAdjustedScore\":");
        assertThat(json).doesNotContain("\"value\":{").doesNotContain("E-");
    }

    @Test
    void penaltiesAreFlatWithoutTheirDefinitionAndEvidenceIsKept() throws Exception {
        JsonNode trace = parse(TraceJson.write(run().trace()));
        JsonNode penalties = trace.get("steps").get(5).get("aggregation");
        JsonNode penalty = penalties.get("results").get(0);
        assertThat(penalty.get("penaltyId").asText()).isEqualTo("TH-P01");
        assertThat(penalty.get("status").asText()).isEqualTo("APPLIED");
        assertThat(new BigDecimal(penalty.get("effectiveDeduction").asText())).isEqualByComparingTo("12");
        assertThat(penalty.get("triggerPatternId").asText()).isEqualTo("TH-001");
        assertThat(penalty.has("definition")).isFalse();
        assertThat(penalty.get("evidenceRefs")).isNotEmpty();
        assertThat(penalties.get("tieBreakVersion").asText()).isEqualTo("1");
        JsonNode pattern = trace.get("steps").get(4).get("patterns").get(0);
        assertThat(pattern.get("status").asText()).isEqualTo("MATCHED");
        assertThat(pattern.get("conditionResult").get("outcome").asText()).isEqualTo("TRUE");
        assertThat(pattern.get("evidence")).hasSize(2);
    }

    @Test
    void theTraceContainsNoRawAnswers() {
        assertThat(TraceJson.write(run().trace())).doesNotContain("only one key person").doesNotContain("key person");
    }

    @Test
    void outputIsByteForByteRepeatable() {
        assertThat(TraceJson.write(run().trace())).isEqualTo(TraceJson.write(run().trace()));
    }

    @Test
    void plainValuesUseTheSameSettings() {
        assertThat(TraceJson.writeValue(List.of("a", "b"))).isEqualTo("[\"a\",\"b\"]");
    }

    @Test
    void storedAnswersRoundTripAsNumbersTextBooleansAndNull() {
        assertThat(AnswerJson.read(AnswerJson.write("Q", 4))).isEqualTo(new BigDecimal("4"));
        assertThat(AnswerJson.read(AnswerJson.write("Q", 2.5d))).isEqualTo(new BigDecimal("2.5"));
        assertThat((BigDecimal) AnswerJson.read(AnswerJson.write("Q", new BigDecimal("3.10")))).isEqualByComparingTo("3.10");
        assertThat(AnswerJson.read(AnswerJson.write("Q", "OFTEN \"quoted\""))).isEqualTo("OFTEN \"quoted\"");
        assertThat(AnswerJson.read(AnswerJson.write("Q", false))).isEqualTo(false);
        assertThat(AnswerJson.read(AnswerJson.write("Q", null))).isNull();
    }

    @Test
    void unsupportedOrNonFiniteAnswersAreRejected() {
        assertThatThrownBy(() -> AnswerJson.write("Q7", List.of(1))).isInstanceOfSatisfying(ScoringException.class,
                e -> assertThat(e.code()).isEqualTo(ScoringErrorCode.INVALID_ANSWER_TYPE));
        assertThatThrownBy(() -> AnswerJson.write("Q7", Double.NaN)).isInstanceOf(ScoringException.class);
    }

    @Test
    void hashOfAnswersIsUnchangedByStorage() {
        List<ScoringInput> original = List.of(new ScoringInput("Q1", 4), new ScoringInput("Q2", 2.5d), new ScoringInput("Q3", "x"),
                new ScoringInput("Q4", true), new ScoringInput("Q5", null));
        List<ScoringInput> reloaded = original.stream()
                .map(i -> new ScoringInput(i.questionId(), AnswerJson.read(AnswerJson.write(i.questionId(), i.answer())))).toList();
        assertThat(com.company.leadershipintelligence.domain.service.InputHasher.answersHash(reloaded))
                .isEqualTo(com.company.leadershipintelligence.domain.service.InputHasher.answersHash(original));
    }
}
