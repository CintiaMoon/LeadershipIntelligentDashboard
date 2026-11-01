package com.company.leadershipintelligence.domain.service;

import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ResponseSummary;
import com.company.leadershipintelligence.domain.model.ScopedResponse;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringContext;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.model.ScoringResult;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.rule.ContributionRule;
import com.company.leadershipintelligence.domain.rule.DimensionScoringConfig;
import com.company.leadershipintelligence.domain.rule.DimensionSettings;
import com.company.leadershipintelligence.domain.rule.InterpretationConfig;
import com.company.leadershipintelligence.domain.rule.OverallScoringConfig;
import com.company.leadershipintelligence.domain.rule.OverallWeights;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.service.explain.PipelineFixtureAccess.PERCENT;
import static org.assertj.core.api.Assertions.assertThat;

/** The engine with answers from several respondents (decision D45): scoring units, minimums, and the legacy form. */
class ResponseScopesEngineTest {

    private final ScoringOrchestrator engine = ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative());

    private static final Map<ScoringDimension, String> QUESTION = Map.of(
            ScoringDimension.TEAM_HEALTH, "T1", ScoringDimension.DELIVERY_STABILITY, "D1",
            ScoringDimension.TECHNICAL_RISK, "R1", ScoringDimension.LEADERSHIP_LOAD, "L1");
    private static final List<String> QUESTIONS = List.of("T1", "D1", "R1", "L1");

    private static ScoringContext context() {
        return new ScoringContext(new TenantId("tenant-1"), new OrganizationId("org-1"), new AssessmentId("a-1"),
                new AssessmentSnapshotId("snap-1"), new ConfigurationVersion("cfg-1"), "3.0.0", "1.0.0");
    }

    private static ScoringConfigurationSnapshot snapshot() {
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
        OverallScoringConfig overall = new OverallScoringConfig(new OverallWeights(weights, d("0")),
                InterpretationConfig.illustrative(), InterpretationConfig.illustrative());
        return new ScoringConfigurationSnapshot(new ConfigurationVersion("cfg-1"), questions, Map.of(),
                new DimensionScoringConfig(settings, rules, 4), List.of(), List.of(), List.of(), overall);
    }

    /** {@code count} members of {@code team}, each answering the same value for every question. */
    private static List<ScopedResponse> members(String team, int count, int... valuesPerQuestion) {
        List<ScopedResponse> list = new ArrayList<>();
        for (int member = 0; member < count; member++) {
            for (int q = 0; q < QUESTIONS.size(); q++) {
                list.add(ScopedResponse.member(QUESTIONS.get(q), valuesPerQuestion[q], team));
            }
        }
        return list;
    }

    @Test
    void oneAnswerPerQuestionScoresExactlyLikeTheEarlierForm() {
        ScoringConfigurationSnapshot config = snapshot();
        List<ScoringInput> legacy = List.of(new ScoringInput("T1", 84), new ScoringInput("D1", 76),
                new ScoringInput("R1", 66), new ScoringInput("L1", 70));
        List<ScopedResponse> scoped = legacy.stream().map(i -> ScopedResponse.organization(i.questionId(), i.answer())).toList();

        ScoringResult before = engine.score(context(), legacy, config);
        ScoringResult after = engine.scoreResponses(context(), scoped, null, config);

        assertThat(after.facts().dimensionScores()).isEqualTo(before.facts().dimensionScores());
        assertThat(after.overall()).isEqualTo(before.overall());
        assertThat(after.facts().normalizedInputs()).isEqualTo(before.facts().normalizedInputs());
        assertThat(after.facts().versions().confidenceAlgorithmVersion()).isEqualTo("1");
        assertThat(after.facts().responseSummaries()).hasSize(4)
                .allSatisfy(s -> assertThat(s.participation()).isEqualTo(ResponseSummary.Participation.UNKNOWN));
        assertThat(before.facts().responseSummaries()).isEmpty();
    }

    @Test
    void memberAnswersAreCombinedAndRecordParticipation() {
        List<ScopedResponse> answers = new ArrayList<>();
        answers.addAll(members("alpha", 5, 80, 70, 60, 50));
        ScoringResult result = engine.scoreResponses(context(), answers, null, snapshot());

        assertThat(result.status()).isEqualTo(ScoreStatus.VALID);
        assertThat(result.overall().finalScore().value()).isEqualByComparingTo("65");
        assertThat(result.facts().versions().confidenceAlgorithmVersion()).isEqualTo("2");
        assertThat(result.facts().responseSummaries()).hasSize(4)
                .allSatisfy(s -> {
                    assertThat(s.respondents()).isEqualTo(5);
                    assertThat(s.participation()).isEqualTo(ResponseSummary.Participation.OBSERVED);
                });
    }

    @Test
    void anOrganizationAndItsTeamsGetTheirOwnResultsFromTheSameAnswers() {
        List<ScopedResponse> answers = new ArrayList<>();
        answers.addAll(members("alpha", 5, 90, 90, 90, 90));
        answers.addAll(members("beta", 5, 50, 50, 50, 50));
        answers.addAll(members("gamma", 5, 10, 10, 10, 10));
        ScoringConfigurationSnapshot config = snapshot();

        assertThat(engine.scoreResponses(context(), answers, "alpha", config).overall().finalScore().value()).isEqualByComparingTo("90");
        assertThat(engine.scoreResponses(context(), answers, "beta", config).overall().finalScore().value()).isEqualByComparingTo("50");
        assertThat(engine.scoreResponses(context(), answers, "gamma", config).overall().finalScore().value()).isEqualByComparingTo("10");
        // 15 answers per question: five 90, five 50, five 10; the median is 50
        assertThat(engine.scoreResponses(context(), answers, null, config).overall().finalScore().value()).isEqualByComparingTo("50");
    }

    @Test
    void aTeamBelowTheMinimumHasNoScoreAndNeverZero() {
        List<ScopedResponse> answers = new ArrayList<>();
        answers.addAll(members("alpha", 5, 90, 90, 90, 90));
        answers.addAll(members("tiny", 3, 20, 20, 20, 20));
        ScoringConfigurationSnapshot config = snapshot();

        ScoringResult tiny = engine.scoreResponses(context(), answers, "tiny", config);
        assertThat(tiny.status()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        assertThat(tiny.overall().finalScore()).isNull();
        assertThat(tiny.facts().responseSummaries()).allSatisfy(s -> assertThat(s.belowMinimum()).isTrue());

        // the small team still counts toward the organization's own pooled result
        ScoringResult organization = engine.scoreResponses(context(), answers, null, config);
        assertThat(organization.status()).isEqualTo(ScoreStatus.VALID);
    }

    @Test
    void identicalAnswersGiveIdenticalResultsRegardlessOfOrder() {
        List<ScopedResponse> answers = new ArrayList<>();
        answers.addAll(members("alpha", 6, 61, 72, 83, 94));
        answers.addAll(members("beta", 7, 15, 25, 35, 45));
        ScoringResult first = engine.scoreResponses(context(), answers, null, snapshot());
        List<ScopedResponse> reversed = new ArrayList<>(answers);
        java.util.Collections.reverse(reversed);
        ScoringResult second = engine.scoreResponses(context(), reversed, null, snapshot());
        assertThat(second.facts()).isEqualTo(first.facts());
        assertThat(second.trace()).isEqualTo(first.trace());
    }
}
