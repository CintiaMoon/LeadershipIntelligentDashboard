package com.company.leadershipintelligence.domain.service;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.PenaltyStatus;
import com.company.leadershipintelligence.domain.enums.PenaltyTarget;
import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.AdjustedDimension;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringContext;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.model.ScoringResult;
import com.company.leadershipintelligence.domain.model.TraceStep;
import com.company.leadershipintelligence.domain.rule.Condition;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.rule.ContributionRule;
import com.company.leadershipintelligence.domain.rule.DimensionScoringConfig;
import com.company.leadershipintelligence.domain.rule.DimensionSettings;
import com.company.leadershipintelligence.domain.rule.InterpretationConfig;
import com.company.leadershipintelligence.domain.rule.OverallScoringConfig;
import com.company.leadershipintelligence.domain.rule.OverallWeights;
import com.company.leadershipintelligence.domain.rule.PatternDefinition;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.service.explain.PipelineFixtureAccess.PERCENT;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.cmp;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.dim;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.pattern;
import static com.company.leadershipintelligence.domain.service.penalties.PenaltyTestSupport.pen;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end engine tests. The sample configuration is built from the specv3.md Section 17.6 numbers: each
 * dimension has one 0-100 question, and one always-true pattern per penalised dimension triggers a fixed deduction.
 */
class ScoringOrchestratorTest {

    private final ScoringOrchestrator engine = ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative());

    private static final Map<ScoringDimension, String> QUESTION = Map.of(
            ScoringDimension.TEAM_HEALTH, "T1", ScoringDimension.DELIVERY_STABILITY, "D1",
            ScoringDimension.TECHNICAL_RISK, "R1", ScoringDimension.LEADERSHIP_LOAD, "L1");

    private static ScoringContext context(String configuration) {
        return new ScoringContext(new TenantId("tenant-1"), new OrganizationId("org-1"), new AssessmentId("a-1"),
                new AssessmentSnapshotId("snap-1"), new ConfigurationVersion(configuration), "3.0.0", "1.0.0");
    }

    /** dimension penalties by target, plus an optional overall penalty (0 = none). */
    private static ScoringConfigurationSnapshot snapshot(String configuration, Map<ScoringDimension, String> dimensionPenalties,
                                                         String overallPenalty) {
        Map<String, QuestionDefinition> questions = new LinkedHashMap<>();
        List<ContributionRule> rules = new ArrayList<>();
        QUESTION.forEach((dimension, q) -> {
            questions.put(q, question(q, PERCENT, MissingDataPolicy.IGNORE));
            rules.add(new ContributionRule(q, dimension, new ScoreWeight(d("1"))));
        });
        Map<ScoringDimension, DimensionSettings> settings = new EnumMap<>(ScoringDimension.class);
        Map<ScoringDimension, ScoreWeight> weights = new EnumMap<>(ScoringDimension.class);
        for (ScoringDimension dimension : ScoringDimension.values()) {
            settings.put(dimension, new DimensionSettings(d("0.75")));
            weights.put(dimension, new ScoreWeight(d("0.25")));
        }
        List<PatternDefinition> patterns = new ArrayList<>();
        List<PenaltyDefinition> penalties = new ArrayList<>();
        dimensionPenalties.forEach((dimension, amount) -> {
            String id = "PT-" + dimension;
            patterns.add(pattern(id, 1, Severity.HIGH, present(dimension), 1, "0.5", true, true));
            penalties.add(pen().id("PEN-" + dimension).trigger(id).target(PenaltyTarget.valueOf(dimension.name()))
                    .strategy(new StrategyConfig.Fixed(new PenaltyAmount(d(amount)))).build());
        });
        if (!overallPenalty.equals("0")) {
            patterns.add(pattern("PT-OVERALL", 1, Severity.HIGH, present(ScoringDimension.TEAM_HEALTH), 1, "0.5", true, true));
            penalties.add(pen().id("PEN-OVERALL").trigger("PT-OVERALL").target(PenaltyTarget.OVERALL)
                    .strategy(new StrategyConfig.Fixed(new PenaltyAmount(d(overallPenalty)))).build());
        }
        OverallScoringConfig overall = new OverallScoringConfig(new OverallWeights(weights, d("0")),
                InterpretationConfig.illustrative(), InterpretationConfig.illustrative());
        return new ScoringConfigurationSnapshot(new ConfigurationVersion(configuration), questions, Map.of(),
                new DimensionScoringConfig(settings, rules, 4), patterns, penalties, List.of(), overall);
    }

    private static Condition present(ScoringDimension dimension) {
        return new Condition.Existence(dim(dimension), false);
    }

    private static List<ScoringInput> inputs(String team, String delivery, String technical, String leadership) {
        return List.of(new ScoringInput("T1", Integer.parseInt(team)), new ScoringInput("D1", Integer.parseInt(delivery)),
                new ScoringInput("R1", Integer.parseInt(technical)), new ScoringInput("L1", Integer.parseInt(leadership)));
    }

    private static String adjusted(ScoringResult result) {
        return String.join("/", result.overall().dimensions().stream().map(a -> a.adjustedScore().value().toPlainString()).toList());
    }

    // ---- acceptance numbers ----

    @Test
    void specSection176ExampleEndToEnd() {
        ScoringConfigurationSnapshot config = snapshot("cfg-1", Map.of(ScoringDimension.TEAM_HEALTH, "3",
                ScoringDimension.TECHNICAL_RISK, "8", ScoringDimension.LEADERSHIP_LOAD, "7"), "0");
        ScoringResult result = engine.score(context("cfg-1"), inputs("84", "76", "66", "70"), config);
        assertThat(result.status()).isEqualTo(ScoreStatus.VALID);
        assertThat(result.facts().dimensionScores()).extracting(s -> s.rawScore().value().toPlainString())
                .containsExactly("84.00", "76.00", "66.00", "70.00");
        assertThat(adjusted(result)).isEqualTo("81.00/76.00/58.00/63.00");
        assertThat(result.overall().finalScore().value().toPlainString()).isEqualTo("69.50");
        assertThat(result.facts().lisInterpretation().label()).isEqualTo("HEALTHY");
        assertThat(result.trace().header().scoreStatus()).isEqualTo(ScoreStatus.VALID);
        assertThat(result.trace().step(TraceStep.Penalties.class).aggregation().results())
                .extracting(r -> r.status()).containsOnly(PenaltyStatus.APPLIED);
    }

    @Test
    void legacySection30ExampleEndToEnd() {
        ScoringConfigurationSnapshot config = snapshot("cfg-1", Map.of(ScoringDimension.TECHNICAL_RISK, "5",
                ScoringDimension.LEADERSHIP_LOAD, "4"), "0");
        ScoringResult result = engine.score(context("cfg-1"), inputs("82", "76", "64", "58"), config);
        assertThat(adjusted(result)).isEqualTo("82.00/76.00/59.00/54.00");
        assertThat(result.overall().finalScore().value().toPlainString()).isEqualTo("67.75");
    }

    @Test
    void overallPenaltyIsAppliedOnceAfterWeighting() {
        ScoringConfigurationSnapshot config = snapshot("cfg-1", Map.of(ScoringDimension.TEAM_HEALTH, "3",
                ScoringDimension.TECHNICAL_RISK, "8", ScoringDimension.LEADERSHIP_LOAD, "7"), "5");
        ScoringResult result = engine.score(context("cfg-1"), inputs("84", "76", "66", "70"), config);
        assertThat(adjusted(result)).isEqualTo("81.00/76.00/58.00/63.00");
        assertThat(result.overall().weightedAdjustedScore().value().toPlainString()).isEqualTo("69.50");
        assertThat(result.overall().overallPenaltyApplied().value()).isEqualByComparingTo("5");
        assertThat(result.overall().finalScore().value().toPlainString()).isEqualTo("64.50");
    }

    @Test
    void patternsAndPenaltiesNeverChangeTheRawScores() {
        ScoringResult plain = engine.score(context("cfg-1"), inputs("84", "76", "66", "70"), snapshot("cfg-1", Map.of(), "0"));
        ScoringResult penalised = engine.score(context("cfg-1"), inputs("84", "76", "66", "70"),
                snapshot("cfg-1", Map.of(ScoringDimension.TEAM_HEALTH, "3", ScoringDimension.TECHNICAL_RISK, "8"), "5"));
        assertThat(penalised.facts().dimensionScores()).isEqualTo(plain.facts().dimensionScores());
        assertThat(plain.overall().finalScore().value().toPlainString()).isEqualTo("74.00");
        // adjusted 81/76/58/70 = 71.25, less the overall penalty of 5
        assertThat(penalised.overall().finalScore().value().toPlainString()).isEqualTo("66.25");
    }

    // ---- insufficient data and invalid input ----

    @Test
    void missingDimensionGivesInsufficientDataWithAFullTrace() {
        ScoringConfigurationSnapshot config = snapshot("cfg-1", Map.of(ScoringDimension.TEAM_HEALTH, "3"), "0");
        ScoringResult result = engine.score(context("cfg-1"),
                List.of(new ScoringInput("T1", 84), new ScoringInput("D1", 76), new ScoringInput("L1", 70)), config);
        assertThat(result.status()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        assertThat(result.overall().finalScore()).isNull();
        assertThat(result.facts().lisInterpretation()).isNull();
        assertThat(result.facts().dimensionScores().get(2).rawScore()).isNull();
        assertThat(result.trace().steps()).hasSize(8);
        assertThat(result.trace().header().scoreStatus()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
    }

    @Test
    void invalidInputsAbortTheRunWithoutAPartialResult() {
        ScoringConfigurationSnapshot config = snapshot("cfg-1", Map.of(), "0");
        assertCode(() -> engine.score(context("cfg-1"), List.of(new ScoringInput("NOPE", 1)), config), ScoringErrorCode.QUESTION_NOT_CONFIGURED);
        assertCode(() -> engine.score(context("cfg-1"), List.of(new ScoringInput("T1", 1), new ScoringInput("T1", 2)), config),
                ScoringErrorCode.DUPLICATE_QUESTION_INPUT);
        assertCode(() -> engine.score(context("cfg-1"), List.of(new ScoringInput("T1", "high")), config), ScoringErrorCode.INVALID_ANSWER_TYPE);
        assertCode(() -> engine.score(context("cfg-1"), List.of(new ScoringInput("T1", 250)), config), ScoringErrorCode.INVALID_SCALE_VALUE);
    }

    // ---- configuration guards ----

    @Test
    void theContextAndSnapshotMustAgreeOnTheConfigurationVersion() {
        assertThatThrownBy(() -> engine.score(context("cfg-OTHER"), inputs("84", "76", "66", "70"), snapshot("cfg-1", Map.of(), "0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anIncompleteConfigurationIsRejectedBeforeScoring() {
        ScoringConfigurationSnapshot noDimensions = new ScoringConfigurationSnapshot(new ConfigurationVersion("cfg-1"), Map.of(), Map.of());
        assertCode(() -> engine.score(context("cfg-1"), List.of(), noDimensions), ScoringErrorCode.INVALID_COVERAGE_CONFIGURATION);
        ScoringConfigurationSnapshot full = snapshot("cfg-1", Map.of(), "0");
        ScoringConfigurationSnapshot noOverall = new ScoringConfigurationSnapshot(full.version(), full.questions(), full.signals(),
                full.dimensions(), full.patterns(), full.penalties(), full.imbalanceRules(), OverallScoringConfig.none());
        assertCode(() -> engine.score(context("cfg-1"), inputs("84", "76", "66", "70"), noOverall), ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION);
    }

    // ---- determinism and reproducibility ----

    @Test
    void identicalInputsGiveAnIdenticalResultAndTrace() {
        ScoringConfigurationSnapshot config = snapshot("cfg-1", Map.of(ScoringDimension.TEAM_HEALTH, "3"), "2");
        assertThat(engine.score(context("cfg-1"), inputs("84", "76", "66", "70"), config))
                .isEqualTo(engine.score(context("cfg-1"), inputs("84", "76", "66", "70"), config));
    }

    @Test
    void resultDoesNotDependOnTheOrderInputsAreSupplied() {
        ScoringConfigurationSnapshot config = snapshot("cfg-1", Map.of(ScoringDimension.TEAM_HEALTH, "3",
                ScoringDimension.TECHNICAL_RISK, "8"), "2");
        List<ScoringInput> base = inputs("84", "76", "66", "70");
        ScoringResult expected = engine.score(context("cfg-1"), base, config);
        for (int seed = 0; seed < 25; seed++) {
            List<ScoringInput> shuffled = new ArrayList<>(base);
            Collections.shuffle(shuffled, new Random(seed));
            assertThat(engine.score(context("cfg-1"), shuffled, config)).isEqualTo(expected);
        }
    }

    @Test
    void anOldConfigurationVersionStillReproducesItsOwnResultAfterANewVersionExists() {
        ScoringConfigurationSnapshot v1 = snapshot("cfg-1", Map.of(ScoringDimension.TEAM_HEALTH, "3"), "0");
        ScoringConfigurationSnapshot v2 = snapshot("cfg-2", Map.of(ScoringDimension.TEAM_HEALTH, "10"), "0");
        ScoringResult first = engine.score(context("cfg-1"), inputs("84", "76", "66", "70"), v1);
        ScoringResult newer = engine.score(context("cfg-2"), inputs("84", "76", "66", "70"), v2);
        assertThat(newer.overall().finalScore()).isNotEqualTo(first.overall().finalScore());
        assertThat(engine.score(context("cfg-1"), inputs("84", "76", "66", "70"), v1)).isEqualTo(first);
        assertThat(first.trace().header().configurationVersion()).isEqualTo("cfg-1");
        assertThat(newer.trace().header().configurationVersion()).isEqualTo("cfg-2");
    }

    @Test
    void finalAndAdjustedScoresStayInRange() {
        ScoringConfigurationSnapshot heavy = snapshot("cfg-1", Map.of(ScoringDimension.TEAM_HEALTH, "50",
                ScoringDimension.TECHNICAL_RISK, "50"), "90");
        ScoringResult result = engine.score(context("cfg-1"), inputs("0", "100", "10", "100"), heavy);
        assertThat(result.overall().finalScore().value()).isBetween(d("0"), d("100"));
        for (AdjustedDimension a : result.overall().dimensions()) {
            assertThat(a.adjustedScore().value()).isBetween(d("0"), a.rawScore().value());
        }
        DimensionScore team = result.facts().dimensionScores().get(0);
        assertThat(team.rawScore().value()).isEqualByComparingTo("0");
        assertThat(ConditionOperator.values()).isNotEmpty();
    }
}
