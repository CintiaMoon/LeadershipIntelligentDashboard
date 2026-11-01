package com.company.leadershipintelligence.service.questionnaire;

import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.repository.configuration.ConfigurationMerger;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.company.leadershipintelligence.service.questionnaire.QuestionnaireFixtures.full;
import static org.assertj.core.api.Assertions.assertThat;

/** The generated layer, merged over a platform default, must be accepted by the ordinary configuration parser (decision D47). */
class QuestionnaireGeneratorTest {

    private static final ConfigurationMerger MERGER = new ConfigurationMerger();

    private static QuestionnaireDocument tidy() {
        return QuestionnaireNormalizer.tidy(full());
    }

    private static ScoringConfigurationSnapshot parse(ObjectNode layer) {
        JsonNode merged = MERGER.merge(List.of(ConfigurationSamples.sampleDocument(), layer));
        return ConfigurationSamples.PARSER.parse(merged, new ConfigurationVersion("test"));
    }

    @Test
    void theGeneratedLayerMergedOverAPlatformDefaultPassesTheConfigurationParser() {
        ScoringConfigurationSnapshot snapshot = parse(QuestionnaireGenerator.layer(tidy()));

        // free text is collected, not configured; the unscored multiple choice is configured but mapped to no dimension
        assertThat(snapshot.questions().keySet()).containsExactly("Q01", "Q02", "Q03", "Q04", "Q05");
        assertThat(snapshot.signals().keySet()).containsExactly("SIG_Q01", "SIG_Q02", "SIG_Q03", "SIG_Q04", "SIG_Q05");
        assertThat(snapshot.dimensions().rules()).extracting(r -> r.questionId()).containsExactly("Q01", "Q02", "Q03", "Q04");
        assertThat(snapshot.patterns()).extracting(p -> p.patternId()).containsExactly("X-1");
        assertThat(snapshot.penalties()).extracting(p -> p.penaltyId()).containsExactly("X-P1");
        assertThat(snapshot.recommendations().keySet()).containsExactly("RECOMMEND_X_1");
    }

    @Test
    void nothingOfThePlatformCatalogLeaksIntoAQuestionnaire() {
        ScoringConfigurationSnapshot platform = ConfigurationSamples.sampleSnapshot();
        ScoringConfigurationSnapshot snapshot = parse(QuestionnaireGenerator.layer(tidy()));

        assertThat(snapshot.patterns()).extracting(p -> p.patternId()).doesNotContainAnyElementsOf(
                platform.patterns().stream().map(p -> p.patternId()).toList());
        assertThat(snapshot.penalties()).extracting(p -> p.penaltyId()).doesNotContainAnyElementsOf(
                platform.penalties().stream().map(p -> p.penaltyId()).toList());
        assertThat(snapshot.imbalanceRules()).isEmpty();
        assertThat(snapshot.presentation().patterns().keySet()).containsExactly("X-1");
        assertThat(snapshot.presentation().penalties().keySet()).containsExactly("X-P1");
        assertThat(snapshot.presentation().questions().keySet()).containsExactly("Q01", "Q02", "Q03", "Q04", "Q05");
        // the dimension names of the platform are kept
        assertThat(snapshot.presentation().dimensions()).isEqualTo(platform.presentation().dimensions());
    }

    @Test
    void questionTypesMapToTheEngineTypesWithTheirDirectionAndScores() {
        JsonNode layer = QuestionnaireGenerator.layer(tidy());
        JsonNode q1 = layer.get("questions").get(0);
        assertThat(q1.get("type").asText()).isEqualTo("SCALE");
        assertThat(q1.get("normalization").get("min").asInt()).isEqualTo(1);
        assertThat(q1.get("normalization").get("max").asInt()).isEqualTo(5);
        assertThat(q1.get("normalization").get("direction").asText()).isEqualTo("POSITIVE");

        JsonNode q2 = layer.get("questions").get(1);
        assertThat(q2.get("type").asText()).isEqualTo("MULTIPLE_CHOICE");
        // evenly spaced in the order shown, first 0 and last 100
        assertThat(q2.get("normalization").get("values").get("NEVER").decimalValue()).isEqualByComparingTo("0");
        assertThat(q2.get("normalization").get("values").get("SOMETIMES").decimalValue()).isEqualByComparingTo("50");
        assertThat(q2.get("normalization").get("values").get("ALWAYS").decimalValue()).isEqualByComparingTo("100");

        JsonNode q3 = layer.get("questions").get(2);
        assertThat(q3.get("type").asText()).isEqualTo("BOOLEAN");
        assertThat(q3.get("normalization").get("trueValue").asInt()).isZero();
        assertThat(q3.get("normalization").get("falseValue").asInt()).isEqualTo(100);

        JsonNode q4 = layer.get("questions").get(3);
        assertThat(q4.get("type").asText()).isEqualTo("NUMBER");
        assertThat(q4.get("normalization").get("direction").asText()).isEqualTo("NEGATIVE");
        assertThat(q4.get("normalization").get("outOfRangePolicy").asText()).isEqualTo("CLAMP");

        assertThat(layer.get("questions").get(4).get("type").asText()).isEqualTo("MULTI_SELECT");
        layer.get("questions").forEach(q -> assertThat(q.get("missingPolicy").asText()).isEqualTo("IGNORE"));
    }

    @Test
    void explicitOptionScoresAreKeptAndAnUnevenCountSpacesToTwoDecimals() {
        QuestionnaireDocument document = QuestionnaireNormalizer.tidy(QuestionnaireFixtures.without(full(), "Q02"));
        QuestionnaireDocument four = new QuestionnaireDocument(document.name(), null, List.of(
                QuestionnaireFixtures.singleChoice("Q02", "DELIVERY_STABILITY", "A", "B", "C", "D")), List.of());
        JsonNode values = QuestionnaireGenerator.layer(QuestionnaireNormalizer.tidy(four)).get("questions").get(0).get("normalization").get("values");
        assertThat(values.get("B").decimalValue()).isEqualByComparingTo("33.33");
        assertThat(values.get("C").decimalValue()).isEqualByComparingTo("66.67");

        QuestionnaireDocument scored = new QuestionnaireDocument("n", null, List.of(new QuestionnaireDocument.QuestionSpec("Q02",
                QuestionKind.SINGLE_CHOICE, null, "t", null, false, "DELIVERY_STABILITY", null, null, List.of(
                new QuestionnaireDocument.OptionSpec("A", "a", QuestionnaireFixtures.n("90")),
                new QuestionnaireDocument.OptionSpec("B", "b", QuestionnaireFixtures.n("10"))), null, List.of(), null, null, null)), List.of());
        JsonNode explicit = QuestionnaireGenerator.layer(scored).get("questions").get(0).get("normalization").get("values");
        assertThat(explicit.get("A").decimalValue()).isEqualByComparingTo("90");
        assertThat(explicit.get("B").decimalValue()).isEqualByComparingTo("10");
    }

    @Test
    void theRuleBecomesAPatternOverQuestionSignalsWithItsPenaltyAndRecommendation() {
        JsonNode layer = QuestionnaireGenerator.layer(tidy());
        JsonNode pattern = layer.get("patterns").get(0);
        assertThat(pattern.get("id").asText()).isEqualTo("X-1");
        assertThat(pattern.get("category").asText()).isEqualTo("CROSS_DIMENSIONAL");
        assertThat(pattern.get("penaltyEligible").asBoolean()).isTrue();
        assertThat(pattern.get("evidence").get("minimumSignals").asInt()).isEqualTo(2);
        assertThat(pattern.get("actions").get(0).asText()).isEqualTo("RECOMMEND_X_1");
        assertThat(pattern.get("condition").get("conditions").get(0).get("signal").asText()).isEqualTo("SIG_Q01");

        JsonNode penalty = layer.get("penalties").get(0);
        assertThat(penalty.get("trigger").get("pattern").asText()).isEqualTo("X-1");
        assertThat(penalty.get("target").asText()).isEqualTo("TEAM_HEALTH");
        assertThat(penalty.get("strategy").get("type").asText()).isEqualTo("SEVERITY_BASED");
        assertThat(penalty.get("maximumCumulativeDeduction").asInt()).isEqualTo(20);
        assertThat(penalty.get("stackingPolicy").asText()).isEqualTo("CAPPED_STACK");
    }

    @Test
    void aRuleOverOneDimensionTakesThatDimensionAsItsCategory() {
        QuestionnaireDocument document = QuestionnaireNormalizer.tidy(new QuestionnaireDocument("n", null, full().questions(), List.of(
                QuestionnaireFixtures.rule("Low health", QuestionnaireFixtures.leq("Q01", "25"), null, null))));
        JsonNode pattern = QuestionnaireGenerator.layer(document).get("patterns").get(0);
        assertThat(pattern.get("category").asText()).isEqualTo("TEAM_HEALTH");
        assertThat(pattern.get("penaltyEligible").asBoolean()).isFalse();
    }

    @Test
    void theSameDocumentAlwaysGeneratesTheSameLayer() {
        assertThat(QuestionnaireGenerator.layer(tidy()).toString()).isEqualTo(QuestionnaireGenerator.layer(tidy()).toString());
    }
}
