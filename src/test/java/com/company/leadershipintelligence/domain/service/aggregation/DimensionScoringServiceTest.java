package com.company.leadershipintelligence.domain.service.aggregation;

import com.company.leadershipintelligence.domain.enums.ContributionStatus;
import com.company.leadershipintelligence.domain.enums.CoverageStatus;
import com.company.leadershipintelligence.domain.enums.DimensionStatus;
import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.OutOfRangePolicy;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.UnknownOptionPolicy;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.NormalizedInput;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.ContributionRule;
import com.company.leadershipintelligence.domain.rule.DimensionScoringConfig;
import com.company.leadershipintelligence.domain.rule.DimensionSettings;
import com.company.leadershipintelligence.domain.rule.ScaleConfig;
import com.company.leadershipintelligence.domain.service.coverage.CoverageValidationService;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.Fixtures.frequency;
import static com.company.leadershipintelligence.domain.Fixtures.normalizationService;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.Fixtures.scale15;
import static com.company.leadershipintelligence.domain.Fixtures.singlePointOfFailureText;
import static com.company.leadershipintelligence.domain.Fixtures.yesIsGood;
import static org.assertj.core.api.Assertions.assertThat;

class DimensionScoringServiceTest {

    private final DimensionScoringService service =
            new DimensionScoringService(new WeightedAverageStrategy(), new CoverageValidationService());

    private static final ScaleConfig PERCENT = new ScaleConfig(d("0"), d("100"), ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT, true);

    /** Builds a snapshot where each question is a 0-100 scale question mapped as given. */
    private static ScoringConfigurationSnapshot snapshot(String requiredCoverage, List<ContributionRule> rules,
                                                         List<QuestionDefinition> extraQuestions) {
        Map<String, QuestionDefinition> questions = new LinkedHashMap<>();
        for (ContributionRule rule : rules) {
            questions.computeIfAbsent(rule.questionId(), id -> question(id, PERCENT, MissingDataPolicy.IGNORE));
        }
        extraQuestions.forEach(q -> questions.put(q.questionId(), q));
        Map<ScoringDimension, DimensionSettings> settings = new EnumMap<>(ScoringDimension.class);
        for (ScoringDimension dim : ScoringDimension.values()) {
            settings.put(dim, new DimensionSettings(d(requiredCoverage)));
        }
        return new ScoringConfigurationSnapshot(new ConfigurationVersion("t"), questions, Map.of(),
                new DimensionScoringConfig(settings, rules, 4));
    }

    private static ContributionRule rule(String q, ScoringDimension dim, String weight) {
        return new ContributionRule(q, dim, new ScoreWeight(d(weight)));
    }

    /** One question per dimension so every dimension is configured. */
    private static List<ContributionRule> base() {
        return new ArrayList<>(List.of(
                rule("T1", ScoringDimension.TEAM_HEALTH, "1"),
                rule("D1", ScoringDimension.DELIVERY_STABILITY, "1"),
                rule("R1", ScoringDimension.TECHNICAL_RISK, "1"),
                rule("L1", ScoringDimension.LEADERSHIP_LOAD, "1")));
    }

    private List<DimensionScore> run(ScoringConfigurationSnapshot snap, ScoringInput... inputs) {
        List<NormalizedInput> normalized = normalizationService().normalize(List.of(inputs), snap);
        return service.score(normalized, snap);
    }

    private static DimensionScore of(List<DimensionScore> scores, ScoringDimension dim) {
        return scores.stream().filter(s -> s.dimension() == dim).findFirst().orElseThrow();
    }

    private static ScoringInput in(String q, Object v) {
        return new ScoringInput(q, v);
    }

    @Test
    void returnsFourDimensionsInEnumOrder() {
        List<DimensionScore> scores = run(snapshot("0.75", base(), List.of()),
                in("T1", 80), in("D1", 70), in("R1", 60), in("L1", 50));
        assertThat(scores).extracting(DimensionScore::dimension).containsExactly(ScoringDimension.values());
        assertThat(scores).extracting(s -> s.rawScore().value().toPlainString()).containsExactly("80.00", "70.00", "60.00", "50.00");
        assertThat(scores).extracting(DimensionScore::status).containsOnly(DimensionStatus.VALID);
    }

    @Test
    void multipleWeightedContributionsAndContributionDetail() {
        List<ContributionRule> rules = base();
        rules.add(rule("T2", ScoringDimension.TEAM_HEALTH, "3"));
        List<DimensionScore> scores = run(snapshot("0.75", rules, List.of()),
                in("T1", 80), in("T2", 60), in("D1", 1), in("R1", 1), in("L1", 1));
        DimensionScore team = of(scores, ScoringDimension.TEAM_HEALTH);
        assertThat(team.rawScore().value()).isEqualByComparingTo("65");
        assertThat(team.contributions()).extracting(c -> c.questionId()).containsExactly("T1", "T2");
        assertThat(team.contributions().get(0).contribution()).isEqualByComparingTo("20");
        assertThat(team.contributions().get(1).contribution()).isEqualByComparingTo("45");
        assertThat(team.contributions()).extracting(c -> c.status()).containsOnly(ContributionStatus.APPLIED);
    }

    @Test
    void oneQuestionCanFeedTwoDimensionsWithItsOwnWeightEach() {
        List<ContributionRule> rules = base();
        rules.add(rule("Q15", ScoringDimension.DELIVERY_STABILITY, "0.7"));
        rules.add(rule("Q15", ScoringDimension.LEADERSHIP_LOAD, "0.3"));
        List<DimensionScore> scores = run(snapshot("0.75", rules, List.of()),
                in("T1", 1), in("D1", 100), in("Q15", 50), in("R1", 1), in("L1", 0));
        // delivery: (100*1 + 50*0.7)/1.7 ; leadership: (0*1 + 50*0.3)/1.3
        assertThat(of(scores, ScoringDimension.DELIVERY_STABILITY).rawScore().value()).isEqualByComparingTo("79.41");
        assertThat(of(scores, ScoringDimension.LEADERSHIP_LOAD).rawScore().value()).isEqualByComparingTo("11.54");
    }

    @Test
    void mixedAnswerTypesFeedOneDimension() {
        List<ContributionRule> rules = base();
        rules.add(rule("S", ScoringDimension.TEAM_HEALTH, "1"));
        rules.add(rule("M", ScoringDimension.TEAM_HEALTH, "1"));
        rules.add(rule("B", ScoringDimension.TEAM_HEALTH, "1"));
        List<QuestionDefinition> extras = List.of(
                question("S", scale15(ScaleDirection.POSITIVE), MissingDataPolicy.IGNORE),
                question("M", frequency(UnknownOptionPolicy.REJECT), MissingDataPolicy.IGNORE),
                question("B", yesIsGood(), MissingDataPolicy.IGNORE));
        List<DimensionScore> scores = run(snapshot("0.75", rules, extras),
                in("T1", 100), in("S", 4), in("M", "SOMETIMES"), in("B", true),
                in("D1", 1), in("R1", 1), in("L1", 1));
        // (100 + 75 + 50 + 100) / 4
        assertThat(of(scores, ScoringDimension.TEAM_HEALTH).rawScore().value()).isEqualByComparingTo("81.25");
    }

    @Test
    void scoreBoundsAreZeroAndHundred() {
        List<DimensionScore> low = run(snapshot("0.75", base(), List.of()), in("T1", 0), in("D1", 0), in("R1", 0), in("L1", 0));
        List<DimensionScore> high = run(snapshot("0.75", base(), List.of()), in("T1", 100), in("D1", 100), in("R1", 100), in("L1", 100));
        assertThat(low).allSatisfy(s -> assertThat(s.rawScore().value()).isEqualByComparingTo("0"));
        assertThat(high).allSatisfy(s -> assertThat(s.rawScore().value()).isEqualByComparingTo("100"));
    }

    @Test
    void scoreIsRoundedOnceToTwoDecimalsHalfUp() {
        List<ContributionRule> rules = base();
        rules.add(rule("T2", ScoringDimension.TEAM_HEALTH, "1"));
        rules.add(rule("T3", ScoringDimension.TEAM_HEALTH, "1"));
        List<DimensionScore> scores = run(snapshot("0.75", rules, List.of()),
                in("T1", 10), in("T2", 20), in("T3", 20), in("D1", 1), in("R1", 1), in("L1", 1));
        // 50/3 = 16.666... -> 16.67
        assertThat(of(scores, ScoringDimension.TEAM_HEALTH).rawScore().value().toPlainString()).isEqualTo("16.67");
    }

    @Test
    void thirdsOfOneHundredDoNotOverflowTheScoreRange() {
        List<ContributionRule> rules = base();
        rules.add(rule("T2", ScoringDimension.TEAM_HEALTH, "1"));
        rules.add(rule("T3", ScoringDimension.TEAM_HEALTH, "1"));
        List<DimensionScore> scores = run(snapshot("0.75", rules, List.of()),
                in("T1", 100), in("T2", 100), in("T3", 100), in("D1", 1), in("R1", 1), in("L1", 1));
        assertThat(of(scores, ScoringDimension.TEAM_HEALTH).rawScore().value().toPlainString()).isEqualTo("100.00");
    }

    @Test
    void zeroWeightContributionIsExcludedFromAggregateAndCoverage() {
        List<ContributionRule> rules = base();
        rules.add(rule("ZERO", ScoringDimension.TEAM_HEALTH, "0"));
        List<DimensionScore> scores = run(snapshot("1", rules, List.of()),
                in("T1", 80), in("ZERO", 0), in("D1", 1), in("R1", 1), in("L1", 1));
        DimensionScore team = of(scores, ScoringDimension.TEAM_HEALTH);
        assertThat(team.rawScore().value()).isEqualByComparingTo("80");
        assertThat(team.coverage().expectedWeight()).isEqualByComparingTo("1");
        assertThat(team.coverage().applicableQuestionCount()).isEqualTo(1);
        assertThat(team.contributions()).extracting(c -> c.status())
                .containsExactly(ContributionStatus.APPLIED, ContributionStatus.EXCLUDED_ZERO_WEIGHT);
    }

    @Test
    void missingAnswerIsExcludedNotZeroAndLowersCoverage() {
        List<ContributionRule> rules = base();
        rules.add(rule("T2", ScoringDimension.TEAM_HEALTH, "1"));
        List<DimensionScore> scores = run(snapshot("0.5", rules, List.of()),
                in("T1", 80), in("T2", null), in("D1", 1), in("R1", 1), in("L1", 1));
        DimensionScore team = of(scores, ScoringDimension.TEAM_HEALTH);
        assertThat(team.rawScore().value()).isEqualByComparingTo("80");
        assertThat(team.coverage().coverage()).isEqualByComparingTo("0.5");
        assertThat(team.coverage().answeredQuestionCount()).isEqualTo(1);
        assertThat(team.contributions()).extracting(c -> c.status())
                .containsExactly(ContributionStatus.APPLIED, ContributionStatus.EXCLUDED_NO_VALUE);
    }

    @Test
    void coverageBoundaryAtTheRequiredMinimum() {
        List<ContributionRule> rules = base();
        rules.add(rule("T2", ScoringDimension.TEAM_HEALTH, "1"));
        rules.add(rule("T3", ScoringDimension.TEAM_HEALTH, "1"));
        rules.add(rule("T4", ScoringDimension.TEAM_HEALTH, "1"));
        ScoringConfigurationSnapshot snap = snapshot("0.75", rules, List.of());

        DimensionScore threeOfFour = of(run(snap, in("T1", 80), in("T2", 80), in("T3", 80), in("T4", null),
                in("D1", 1), in("R1", 1), in("L1", 1)), ScoringDimension.TEAM_HEALTH);
        assertThat(threeOfFour.coverage().coverage()).isEqualByComparingTo("0.75");
        assertThat(threeOfFour.coverage().status()).isEqualTo(CoverageStatus.SUFFICIENT);
        assertThat(threeOfFour.status()).isEqualTo(DimensionStatus.VALID);

        DimensionScore twoOfFour = of(run(snap, in("T1", 80), in("T2", 80), in("T3", null), in("T4", null),
                in("D1", 1), in("R1", 1), in("L1", 1)), ScoringDimension.TEAM_HEALTH);
        assertThat(twoOfFour.coverage().status()).isEqualTo(CoverageStatus.INSUFFICIENT);
        assertThat(twoOfFour.status()).isEqualTo(DimensionStatus.INSUFFICIENT_DATA);
        assertThat(twoOfFour.score()).isEmpty();
        assertThat(twoOfFour.contributions()).extracting(c -> c.status())
                .containsExactly(ContributionStatus.NOT_SCORED, ContributionStatus.NOT_SCORED,
                        ContributionStatus.EXCLUDED_NO_VALUE, ContributionStatus.EXCLUDED_NO_VALUE);
    }

    @Test
    void noValidInputIsInsufficientDataNotZeroOrFifty() {
        List<DimensionScore> scores = run(snapshot("0", base(), List.of()),
                in("T1", null), in("D1", 1), in("R1", 1), in("L1", 1));
        DimensionScore team = of(scores, ScoringDimension.TEAM_HEALTH);
        assertThat(team.status()).isEqualTo(DimensionStatus.INSUFFICIENT_DATA);
        assertThat(team.score()).isEmpty();
    }

    @Test
    void emptyInputMakesEveryDimensionInsufficient() {
        List<DimensionScore> scores = run(snapshot("0.75", base(), List.of()));
        assertThat(scores).extracting(DimensionScore::status).containsOnly(DimensionStatus.INSUFFICIENT_DATA);
    }

    @Test
    void neutralValueCountsAsAnAppliedContribution() {
        QuestionDefinition neutral = new QuestionDefinition("T1", QuestionType.SCALE, PERCENT, MissingDataPolicy.NEUTRAL,
                new com.company.leadershipintelligence.domain.valueobject.NormalizedValue(d("40")), null);
        List<DimensionScore> scores = run(snapshot("1", base(), List.of(neutral)),
                in("T1", null), in("D1", 1), in("R1", 1), in("L1", 1));
        DimensionScore team = of(scores, ScoringDimension.TEAM_HEALTH);
        assertThat(team.rawScore().value()).isEqualByComparingTo("40");
        assertThat(team.coverage().coverage()).isEqualByComparingTo("1");
    }

    @Test
    void identicalInputsGiveIdenticalScores() {
        ScoringConfigurationSnapshot snap = snapshot("0.75", base(), List.of());
        assertThat(run(snap, in("T1", 33), in("D1", 66), in("R1", 99), in("L1", 1)))
                .isEqualTo(run(snap, in("T1", 33), in("D1", 66), in("R1", 99), in("L1", 1)));
    }

    // ---- configuration validation ----

    @Test
    void textQuestionsCannotBeMapped() {
        List<ContributionRule> rules = base();
        rules.add(rule("TXT", ScoringDimension.TEAM_HEALTH, "1"));
        Map<ScoringDimension, DimensionSettings> settings = new EnumMap<>(ScoringDimension.class);
        for (ScoringDimension dim : ScoringDimension.values()) {
            settings.put(dim, new DimensionSettings(d("0.75")));
        }
        Map<String, QuestionDefinition> questions = new LinkedHashMap<>();
        for (ContributionRule r : rules) {
            questions.computeIfAbsent(r.questionId(), id -> question(id, PERCENT, MissingDataPolicy.IGNORE));
        }
        questions.put("TXT", question("TXT", singlePointOfFailureText(), MissingDataPolicy.IGNORE));
        Map<String, com.company.leadershipintelligence.domain.rule.SignalDefinition> signals = Map.of(
                "SINGLE_POINT_OF_FAILURE", com.company.leadershipintelligence.domain.Fixtures.booleanSignal("SINGLE_POINT_OF_FAILURE", "TXT"));
        assertCode(() -> new ScoringConfigurationSnapshot(new ConfigurationVersion("t"), questions, signals,
                new DimensionScoringConfig(settings, rules, 4)), ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION);
    }

    @Test
    void mappingToAnUnknownQuestionIsRejected() {
        Map<ScoringDimension, DimensionSettings> settings = new EnumMap<>(ScoringDimension.class);
        for (ScoringDimension dim : ScoringDimension.values()) {
            settings.put(dim, new DimensionSettings(d("0.75")));
        }
        assertCode(() -> new ScoringConfigurationSnapshot(new ConfigurationVersion("t"), Map.of(), Map.of(),
                new DimensionScoringConfig(settings, base(), 4)), ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION);
    }

    @Test
    void duplicateMappingsAndMissingSettingsAreRejected() {
        List<ContributionRule> dup = base();
        dup.add(rule("T1", ScoringDimension.TEAM_HEALTH, "2"));
        Map<ScoringDimension, DimensionSettings> all = new EnumMap<>(ScoringDimension.class);
        for (ScoringDimension dim : ScoringDimension.values()) {
            all.put(dim, new DimensionSettings(d("0.75")));
        }
        assertCode(() -> new DimensionScoringConfig(all, dup, 4), ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION);

        Map<ScoringDimension, DimensionSettings> partial = new EnumMap<>(ScoringDimension.class);
        partial.put(ScoringDimension.TEAM_HEALTH, new DimensionSettings(d("0.75")));
        assertCode(() -> new DimensionScoringConfig(partial, base(), 4), ScoringErrorCode.INVALID_COVERAGE_CONFIGURATION);
    }

    @Test
    void dimensionNeedsAPositiveWeightMappingAndQuestionFanOutIsLimited() {
        Map<ScoringDimension, DimensionSettings> all = new EnumMap<>(ScoringDimension.class);
        for (ScoringDimension dim : ScoringDimension.values()) {
            all.put(dim, new DimensionSettings(d("0.75")));
        }
        List<ContributionRule> zeroOnly = base();
        zeroOnly.set(0, rule("T1", ScoringDimension.TEAM_HEALTH, "0"));
        assertCode(() -> new DimensionScoringConfig(all, zeroOnly, 4), ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION);

        List<ContributionRule> wide = base();
        wide.add(rule("T1", ScoringDimension.DELIVERY_STABILITY, "1"));
        assertCode(() -> new DimensionScoringConfig(all, wide, 1), ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION);
    }

    @Test
    void coverageSettingsMustBeBetweenZeroAndOne() {
        assertCode(() -> new DimensionSettings(d("1.01")), ScoringErrorCode.INVALID_COVERAGE_CONFIGURATION);
        assertCode(() -> new DimensionSettings(d("-0.1")), ScoringErrorCode.INVALID_COVERAGE_CONFIGURATION);
    }

    @Test
    void scoringWithoutDimensionConfigurationFails() {
        ScoringConfigurationSnapshot none = new ScoringConfigurationSnapshot(new ConfigurationVersion("t"), Map.of(), Map.of());
        assertCode(() -> service.score(List.of(), none), ScoringErrorCode.INVALID_COVERAGE_CONFIGURATION);
    }
}
