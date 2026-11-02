package com.company.leadershipintelligence.service.configuration;

import com.company.leadershipintelligence.config.properties.EngineProperties;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.service.ScoringOrchestrator;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.repository.configuration.ConfigurationDocumentParser;
import com.company.leadershipintelligence.service.query.ConfigurationViews.ConfigurationOutcome;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The starter fixtures that ship with the initial platform configuration (decision D56) must hold for it. They are also the
 * regression cases a later edit of that configuration has to keep passing.
 */
class StarterFixturesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ConfigurationEvaluator evaluator = new ConfigurationEvaluator(ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative()),
            new EngineProperties("3.0.0", "1.0.0"));

    static List<FixtureSpec> starter() throws IOException {
        try (InputStream in = StarterFixturesTest.class.getResourceAsStream("/config/initial-platform-default.fixtures.json")) {
            return JSON.readValue(in, new TypeReference<>() {
            });
        }
    }

    static ScoringConfigurationSnapshot bundled() throws IOException {
        try (InputStream in = StarterFixturesTest.class.getResourceAsStream("/config/initial-platform-default.json")) {
            ConfigurationDocumentParser parser = new ConfigurationDocumentParser();
            return parser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), new ConfigurationVersion("initial"));
        }
    }

    @Test
    void thereAreFiveStarterFixturesWithDistinctNames() throws IOException {
        List<FixtureSpec> fixtures = starter();
        assertThat(fixtures).extracting(FixtureSpec::name).doesNotHaveDuplicates().hasSize(5);
    }

    @Test
    void everyStarterFixtureHoldsForTheBundledConfiguration() throws IOException {
        ScoringConfigurationSnapshot configuration = bundled();
        for (FixtureSpec fixture : starter()) {
            ConfigurationOutcome outcome = evaluator.evaluate(configuration, ConfigurationEvaluator.answersOf(fixture));
            assertThat(ConfigurationEvaluator.failures(outcome, fixture.expectations()))
                    .as(fixture.name() + " -> " + outcome).isEmpty();
        }
    }

    @Test
    void theFixturesNameOnlyRealQuestionsAndPatterns() throws IOException {
        ScoringConfigurationSnapshot configuration = bundled();
        var patterns = configuration.patterns().stream().map(p -> p.patternId()).toList();
        for (FixtureSpec fixture : starter()) {
            fixture.answers().forEach(a -> assertThat(configuration.questions()).containsKey(a.questionId()));
            assertThat(patterns).containsAll(fixture.expectations().patternsMatched());
            assertThat(patterns).containsAll(fixture.expectations().patternsNotMatched());
        }
    }

    @Test
    void anExpectationThatIsNotMetIsReportedInWords() throws IOException {
        ScoringConfigurationSnapshot configuration = bundled();
        FixtureSpec best = starter().get(0);
        FixtureSpec.Expectations demanding = new FixtureSpec.Expectations("INSUFFICIENT_DATA", new FixtureSpec.Range(null, new java.math.BigDecimal("50")),
                java.util.Map.of("TEAM_HEALTH", new FixtureSpec.Range(new java.math.BigDecimal("101"), null)), List.of("TH-001"), List.of());
        ConfigurationOutcome outcome = evaluator.evaluate(configuration, ConfigurationEvaluator.answersOf(best));
        assertThat(ConfigurationEvaluator.failures(outcome, demanding)).containsExactly(
                "the status is VALID, expected INSUFFICIENT_DATA",
                "the overall score is 100.00, above the expected 50",
                "the TEAM_HEALTH score is 100.00, below the expected 101",
                "pattern TH-001 was expected and was not detected");
    }

    @Test
    void aCaseTheConfigurationCannotScoreIsAnOutcomeNotAnException() throws IOException {
        ConfigurationOutcome outcome = evaluator.evaluate(bundled(),
                ConfigurationEvaluator.answersOf(new FixtureSpec("x", List.of(new FixtureSpec.Answer("NO_SUCH_QUESTION", 3)), null)));
        assertThat(outcome.status()).isEqualTo(ConfigurationEvaluator.ERROR);
        assertThat(outcome.error()).contains("QUESTION_NOT_CONFIGURED");
        assertThat(ConfigurationEvaluator.failures(outcome, null)).hasSize(1).first().asString().startsWith("it could not be scored");
    }
}
