package com.company.leadershipintelligence.repository.configuration;

import com.company.leadershipintelligence.domain.enums.PatternStatus;
import com.company.leadershipintelligence.domain.enums.PenaltyStatus;
import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.ImbalanceResult;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringContext;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.model.ScoringResult;
import com.company.leadershipintelligence.domain.rule.Condition;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.service.ScoringOrchestrator;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.company.leadershipintelligence.repository.configuration.ConfigurationSamples.PARSER;
import static com.company.leadershipintelligence.repository.configuration.ConfigurationSamples.json;
import static com.company.leadershipintelligence.repository.configuration.ConfigurationSamples.sampleDocument;
import static com.company.leadershipintelligence.repository.configuration.ConfigurationSamples.sampleSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationDocumentParserTest {

    private static final ConfigurationVersion VERSION = new ConfigurationVersion("sample-1");

    private static ScoringException failure(JsonNode document) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> PARSER.parse(document, VERSION));
        assertThat(thrown).isInstanceOf(ScoringException.class);
        return (ScoringException) thrown;
    }

    private static ScoringException failure(String text) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> PARSER.parse(text, VERSION));
        assertThat(thrown).isInstanceOf(ScoringException.class);
        return (ScoringException) thrown;
    }

    private static ObjectNode pattern(ObjectNode document) {
        return (ObjectNode) ((ArrayNode) document.get("patterns")).get(0);
    }

    private static ObjectNode penalty(ObjectNode document) {
        return (ObjectNode) ((ArrayNode) document.get("penalties")).get(0);
    }

    private static ScoringConfigurationSnapshot withCondition(String condition) {
        ObjectNode document = sampleDocument();
        ObjectNode pattern = pattern(document);
        pattern.set("condition", json(condition));
        ((ObjectNode) pattern.get("evidence")).put("minimumSignals", 1);
        return PARSER.parse(document, VERSION);
    }

    private static ScoringConfigurationSnapshot withStrategy(String strategy) {
        ObjectNode document = sampleDocument();
        penalty(document).set("strategy", json(strategy));
        return PARSER.parse(document, VERSION);
    }

    // ---- the sample ----

    @Test
    void sampleParsesIntoTheExpectedSnapshot() {
        ScoringConfigurationSnapshot s = sampleSnapshot();
        assertThat(s.questions()).containsOnlyKeys("Q01", "Q02", "Q03", "Q04", "Q05");
        assertThat(s.signals()).containsOnlyKeys("SINGLE_POINT_OF_FAILURE", "TEAM_SUSTAINABILITY", "WORKLOAD_PRESSURE");
        assertThat(s.patterns()).hasSize(1);
        assertThat(s.penalties()).hasSize(1);
        assertThat(s.imbalanceRules()).hasSize(1);
        assertThat(s.dimensions().isConfigured()).isTrue();
        assertThat(s.overall().isConfigured()).isTrue();
        assertThat(s.signals().get("SINGLE_POINT_OF_FAILURE").reliability()).isEqualByComparingTo("0.8");
    }

    @Test
    void parsingTwiceGivesEqualSnapshots() {
        assertThat(sampleSnapshot()).isEqualTo(sampleSnapshot());
    }

    @Test
    void sampleScoresEndToEndThroughTheEngine() {
        ScoringContext context = new ScoringContext(new TenantId("t"), new OrganizationId("o"), new AssessmentId("a"),
                new AssessmentSnapshotId("s"), VERSION, "3.0.0", "1.0.0");
        List<ScoringInput> inputs = List.of(new ScoringInput("Q01", 2), new ScoringInput("Q02", "OFTEN"),
                new ScoringInput("Q03", true), new ScoringInput("Q04", "only one key person"), new ScoringInput("Q05", 80));
        ScoringResult result = ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative()).score(context, inputs, sampleSnapshot());

        assertThat(result.status()).isEqualTo(ScoreStatus.VALID);
        assertThat(result.facts().dimensionScores()).extracting(d -> d.rawScore().value().toPlainString())
                .containsExactly("25.00", "75.00", "100.00", "80.00");
        assertThat(result.facts().patterns().get(0).status()).isEqualTo(PatternStatus.MATCHED);
        assertThat(result.facts().penalties().results().get(0).status()).isEqualTo(PenaltyStatus.APPLIED);
        assertThat(result.facts().penalties().results().get(0).effectiveDeduction().value()).isEqualByComparingTo("12");
        ImbalanceResult imbalance = result.facts().imbalances().get(0);
        assertThat(imbalance.gap()).isEqualByComparingTo("-25");
        // team 25 - 12 = 13; (13 + 75 + 100 + 80) / 4 = 67.00
        assertThat(result.overall().finalScore().value().toPlainString()).isEqualTo("67.00");
        assertThat(result.facts().lisInterpretation().label()).isEqualTo("HEALTHY");
    }

    // ---- conditions: every operator ----

    @Test
    void everyLeafOperatorAndGroupParsesToItsCondition() {
        assertThat(withCondition("{\"operator\":\"EQ\",\"signal\":\"WORKLOAD_PRESSURE\",\"value\":50}").patterns().get(0).condition())
                .isInstanceOf(Condition.NumericComparison.class);
        assertThat(withCondition("{\"operator\":\"EQ\",\"signal\":\"SINGLE_POINT_OF_FAILURE\",\"value\":true}").patterns().get(0).condition())
                .isInstanceOf(Condition.BooleanComparison.class);
        for (String op : List.of("NE", "GT", "GTE", "LT", "LTE")) {
            assertThat(withCondition("{\"operator\":\"" + op + "\",\"signal\":\"WORKLOAD_PRESSURE\",\"value\":50}").patterns().get(0).condition())
                    .isInstanceOf(Condition.NumericComparison.class);
        }
        assertThat(withCondition("{\"operator\":\"BETWEEN\",\"dimension\":\"TEAM_HEALTH\",\"low\":10,\"high\":20}").patterns().get(0).condition())
                .isInstanceOf(Condition.Between.class);
        assertThat(withCondition("{\"operator\":\"IN\",\"signal\":\"WORKLOAD_PRESSURE\",\"values\":[10,20]}").patterns().get(0).condition())
                .isInstanceOf(Condition.InList.class);
        assertThat(withCondition("{\"operator\":\"NOT_IN\",\"signal\":\"WORKLOAD_PRESSURE\",\"values\":[10,20]}").patterns().get(0).condition().operator().name())
                .isEqualTo("NOT_IN");
        assertThat(withCondition("{\"operator\":\"EXISTS\",\"signal\":\"WORKLOAD_PRESSURE\"}").patterns().get(0).condition())
                .isInstanceOf(Condition.Existence.class);
        assertThat(withCondition("{\"operator\":\"NOT_EXISTS\",\"dimension\":\"TEAM_HEALTH\"}").patterns().get(0).condition().operator().name())
                .isEqualTo("NOT_EXISTS");
        assertThat(withCondition("{\"operator\":\"CONTAINS\",\"question\":\"Q04\",\"signal\":\"SINGLE_POINT_OF_FAILURE\"}").patterns().get(0).condition())
                .isInstanceOf(Condition.Contains.class);
        for (String op : List.of("GAP_GT", "GAP_GTE")) {
            assertThat(withCondition("{\"operator\":\"" + op + "\",\"left\":{\"dimension\":\"DELIVERY_STABILITY\"},"
                    + "\"right\":{\"dimension\":\"TECHNICAL_RISK\"},\"direction\":\"ABSOLUTE\",\"value\":30}").patterns().get(0).condition())
                    .isInstanceOf(Condition.Gap.class);
        }
        Condition nested = withCondition("{\"operator\":\"AND\",\"conditions\":[{\"operator\":\"OR\",\"conditions\":["
                + "{\"operator\":\"GT\",\"signal\":\"WORKLOAD_PRESSURE\",\"value\":1},{\"operator\":\"LT\",\"signal\":\"TEAM_SUSTAINABILITY\",\"value\":99}]},"
                + "{\"operator\":\"NOT\",\"condition\":{\"operator\":\"EXISTS\",\"signal\":\"SINGLE_POINT_OF_FAILURE\"}}]}")
                .patterns().get(0).condition();
        assertThat(nested).isInstanceOf(Condition.And.class);
        assertThat(nested.children().get(0)).isInstanceOf(Condition.Or.class);
        assertThat(nested.children().get(1)).isInstanceOf(Condition.Not.class);
    }

    @Test
    void conditionProblemsAreReportedWithTheirPath() {
        ObjectNode doc = sampleDocument();
        pattern(doc).set("condition", json("{\"operator\":\"GT\",\"signal\":\"NOPE\",\"value\":1}"));
        assertThat(failure(doc).code()).isEqualTo(ScoringErrorCode.INVALID_CONDITION_EXPRESSION);

        doc = sampleDocument();
        pattern(doc).set("condition", json("{\"operator\":\"GT\",\"signal\":\"WORKLOAD_PRESSURE\",\"dimension\":\"TEAM_HEALTH\",\"value\":1}"));
        assertThat(failure(doc).getMessage()).contains("exactly one of signal, dimension or question");

        doc = sampleDocument();
        pattern(doc).set("condition", json("{\"operator\":\"WOBBLY\",\"signal\":\"WORKLOAD_PRESSURE\",\"value\":1}"));
        assertThat(failure(doc).getMessage()).contains("$.patterns[0].condition.operator").contains("WOBBLY");

        doc = sampleDocument();
        pattern(doc).set("condition", json("{\"operator\":\"NOT\"}"));
        assertThat(failure(doc).getMessage()).contains("condition is required");
    }

    // ---- strategies: all five ----

    @Test
    void everyPenaltyStrategyParses() {
        assertThat(withStrategy("{\"type\":\"FIXED\",\"deduction\":5}").penalties().get(0).strategy()).isInstanceOf(StrategyConfig.Fixed.class);
        assertThat(withStrategy("{\"type\":\"PROPORTIONAL\",\"base\":{\"type\":\"RAW_DIMENSION\",\"dimension\":\"TEAM_HEALTH\"},\"rate\":0.1}")
                .penalties().get(0).strategy()).isInstanceOf(StrategyConfig.Proportional.class);
        assertThat(withStrategy("{\"type\":\"SEVERITY_BASED\",\"baseDeduction\":10,\"multipliers\":{\"LOW\":0,\"MEDIUM\":0.5,\"HIGH\":0.8,\"CRITICAL\":1}}")
                .penalties().get(0).strategy()).isInstanceOf(StrategyConfig.SeverityBased.class);
        StrategyConfig threshold = withStrategy("{\"type\":\"THRESHOLD_BASED\",\"metric\":{\"type\":\"DIMENSION_GAP\",\"left\":\"DELIVERY_STABILITY\","
                + "\"right\":\"TECHNICAL_RISK\",\"direction\":\"ABSOLUTE\"},\"bands\":[{\"from\":10,\"fromInclusive\":true,\"to\":20,\"toInclusive\":false,\"deduction\":3},"
                + "{\"from\":20,\"fromInclusive\":true,\"deduction\":6}]}").penalties().get(0).strategy();
        assertThat(threshold).isInstanceOf(StrategyConfig.ThresholdBased.class);
        assertThat(((StrategyConfig.ThresholdBased) threshold).bands()).hasSize(2);
        StrategyConfig conditional = withStrategy("{\"type\":\"CONDITIONAL\",\"condition\":{\"operator\":\"LT\",\"dimension\":\"TEAM_HEALTH\",\"value\":50},"
                + "\"then\":{\"type\":\"FIXED\",\"deduction\":4}}").penalties().get(0).strategy();
        assertThat(conditional).isInstanceOf(StrategyConfig.Conditional.class);
    }

    // ---- strictness ----

    @Test
    void unknownFieldsAreRejectedAtEveryLevel() {
        ObjectNode doc = sampleDocument();
        doc.put("surprise", 1);
        assertThat(failure(doc).getMessage()).contains("$.surprise is not a known field");

        doc = sampleDocument();
        ((ObjectNode) ((ArrayNode) doc.get("questions")).get(0)).put("colour", "red");
        assertThat(failure(doc).getMessage()).contains("$.questions[0].colour");

        doc = sampleDocument();
        ((ObjectNode) penalty(doc).get("strategy")).put("extra", true);
        assertThat(failure(doc).getMessage()).contains("$.penalties[0].strategy.extra");

        doc = sampleDocument();
        ((ObjectNode) pattern(doc).get("condition")).put("stray", 1);
        assertThat(failure(doc).getMessage()).contains("stray");
    }

    @Test
    void malformedJsonDuplicateKeysAndTrailingContentAreRejected() {
        assertThat(failure("{ not json").code()).isEqualTo(ScoringErrorCode.CONFIGURATION_SNAPSHOT_INVALID);
        assertThat(failure("{\"questions\": [], \"questions\": []}").getMessage()).contains("not valid JSON");
        assertThat(failure(sampleDocument() + " {}").getMessage()).contains("not valid JSON");
        assertThat(failure("[1,2]").getMessage()).contains("must be an object");
    }

    @Test
    void missingRequiredFieldsWrongTypesAndUnknownValuesAreRejected() {
        ObjectNode doc = sampleDocument();
        doc.remove("overall");
        assertThat(failure(doc).getMessage()).contains("$.overall is required");

        doc = sampleDocument();
        ((ObjectNode) ((ArrayNode) doc.get("questions")).get(0)).put("type", "NUMEROLOGY");
        assertThat(failure(doc).getMessage()).contains("unknown value 'NUMEROLOGY'");

        doc = sampleDocument();
        ((ObjectNode) ((ObjectNode) ((ArrayNode) doc.get("questions")).get(0)).get("normalization")).put("min", "one");
        assertThat(failure(doc).getMessage()).contains(".normalization.min must be a number");

        doc = sampleDocument();
        ((ObjectNode) doc.get("dimensions")).set("settings", json("{\"BOGUS_DIMENSION\":{\"requiredCoverage\":0.5}}"));
        assertThat(failure(doc).getMessage()).contains("unknown dimension 'BOGUS_DIMENSION'");

        doc = sampleDocument();
        pattern(doc).put("enabled", "yes");
        assertThat(failure(doc).getMessage()).contains("enabled must be true or false");
    }

    @Test
    void duplicateIdsAreRejected() {
        ObjectNode doc = sampleDocument();
        ArrayNode questions = (ArrayNode) doc.get("questions");
        questions.add(questions.get(0).deepCopy());
        assertThat(failure(doc).getMessage()).contains("duplicate question Q01");
    }

    @Test
    void domainValidationRunsOnTheParsedConfiguration() {
        ObjectNode doc = sampleDocument();
        ((ObjectNode) doc.get("overall").get("weights")).put("TEAM_HEALTH", 0.5);
        assertThat(failure(doc).code()).isEqualTo(ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION);

        doc = sampleDocument();
        ((ObjectNode) ((ArrayNode) doc.get("overall").get("lisInterpretation")).get(1)).put("min", 45);
        assertThat(failure(doc).code()).isEqualTo(ScoringErrorCode.INVALID_INTERPRETATION_CONFIGURATION);

        doc = sampleDocument();
        ((ObjectNode) ((ObjectNode) ((ArrayNode) doc.get("questions")).get(1)).get("normalization").get("values")).put("ALWAYS", 150);
        assertThat(failure(doc).code()).isEqualTo(ScoringErrorCode.CONFIGURATION_SNAPSHOT_INVALID);

        doc = sampleDocument();
        ((ObjectNode) penalty(doc).get("trigger")).put("pattern", "TH-999");
        assertThat(failure(doc).code()).isEqualTo(ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);

        doc = sampleDocument();
        penalty(doc).put("stackingPolicy", "CAPPED_STACK");
        assertThat(failure(doc).code()).isEqualTo(ScoringErrorCode.INVALID_PENALTY_CONFIGURATION);
        assertThat(PARSER.parse(sampleText(), VERSION)).isNotNull();
    }

    private static String sampleText() {
        return ConfigurationSamples.sampleText();
    }
}
