package com.company.leadershipintelligence.domain.service.responses;

import com.company.leadershipintelligence.domain.enums.AggregationMethod;
import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.NormalizationStatus;
import com.company.leadershipintelligence.domain.enums.OutOfRangePolicy;
import com.company.leadershipintelligence.domain.enums.ResponseScope;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.enums.SignalDataType;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.NormalizedInput;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ResponseSummary;
import com.company.leadershipintelligence.domain.model.ScopedResponse;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.DimensionScoringConfig;
import com.company.leadershipintelligence.domain.rule.ResponseSettings;
import com.company.leadershipintelligence.domain.rule.ScaleConfig;
import com.company.leadershipintelligence.domain.rule.SignalDefinition;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.Fixtures.normalizationService;
import static com.company.leadershipintelligence.domain.Fixtures.nv;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.Fixtures.singlePointOfFailureText;
import static org.assertj.core.api.Assertions.assertThat;

class ResponseAggregationServiceTest {

    private static final ScaleConfig PERCENT = new ScaleConfig(d("0"), d("100"), ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT, true);
    private static final ScaleConfig NEGATIVE = new ScaleConfig(d("0"), d("100"), ScaleDirection.NEGATIVE, OutOfRangePolicy.REJECT, true);

    private final ResponseAggregationService service = new ResponseAggregationService(normalizationService());

    private static ScoringConfigurationSnapshot snapshot(ResponseSettings settings) {
        Map<String, QuestionDefinition> questions = new LinkedHashMap<>();
        questions.put("Q1", question("Q1", PERCENT, MissingDataPolicy.IGNORE));
        questions.put("Q2", question("Q2", NEGATIVE, MissingDataPolicy.IGNORE));
        questions.put("Q3", new QuestionDefinition("Q3", PERCENT.questionType(), PERCENT, MissingDataPolicy.IGNORE, null, null, AggregationMethod.MEAN));
        questions.put("Q4", question("Q4", singlePointOfFailureText(), MissingDataPolicy.IGNORE));
        questions.put("N1", new QuestionDefinition("N1", PERCENT.questionType(), PERCENT, MissingDataPolicy.NEUTRAL, nv("50"), null));
        Map<String, SignalDefinition> signals = Map.of("SINGLE_POINT_OF_FAILURE",
                new SignalDefinition("SINGLE_POINT_OF_FAILURE", SignalDataType.BOOLEAN, Set.of("Q4"), null, "1"));
        return new ScoringConfigurationSnapshot(new ConfigurationVersion("test-1"), questions, signals, DimensionScoringConfig.none(),
                List.of(), List.of(), List.of(), com.company.leadershipintelligence.domain.rule.OverallScoringConfig.none(),
                Map.of(), com.company.leadershipintelligence.domain.rule.PresentationCatalog.empty(), settings);
    }

    private static ResponseSettings settings(int min) {
        return new ResponseSettings(AggregationMethod.MEDIAN, min, new BigDecimal("0.5"), Math.max(min, 10));
    }

    private static List<ScopedResponse> members(String question, String team, String... values) {
        List<ScopedResponse> list = new ArrayList<>();
        for (String value : values) {
            list.add(ScopedResponse.member(question, d(value), team));
        }
        return list;
    }

    private NormalizedInput input(ResponseAggregationService.Outcome outcome, String questionId) {
        return outcome.inputs().stream().filter(i -> i.questionId().equals(questionId)).findFirst().orElseThrow();
    }

    @Test
    void medianOfAnOddNumberOfMemberAnswers() {
        var outcome = service.aggregate(members("Q1", null, "10", "90", "40"), null, snapshot(settings(3)));
        assertThat(input(outcome, "Q1").normalizedValue().value()).isEqualByComparingTo("40");
        assertThat(input(outcome, "Q1").status()).isEqualTo(NormalizationStatus.NORMALIZED);
    }

    @Test
    void medianOfAnEvenNumberAveragesTheMiddleTwo() {
        var outcome = service.aggregate(members("Q1", null, "10", "20", "30", "100"), null, snapshot(settings(4)));
        assertThat(input(outcome, "Q1").normalizedValue().value()).isEqualByComparingTo("25");
    }

    @Test
    void questionOverrideSelectsTheMean() {
        var outcome = service.aggregate(members("Q3", null, "10", "20", "90"), null, snapshot(settings(3)));
        assertThat(input(outcome, "Q3").normalizedValue().value()).isEqualByComparingTo("40");
        assertThat(outcome.summaries().getFirst().method()).isEqualTo(AggregationMethod.MEAN);
    }

    @Test
    void directionIsAppliedBeforeCombining() {
        // NEGATIVE scale: raw 100 is the worst, so normalized 0; raw 0 -> 100; median of {0, 100, 100} = 100
        var outcome = service.aggregate(members("Q2", null, "100", "0", "0"), null, snapshot(settings(3)));
        assertThat(input(outcome, "Q2").normalizedValue().value()).isEqualByComparingTo("100");
    }

    @Test
    void resultDoesNotDependOnAnswerOrder() {
        List<ScopedResponse> answers = members("Q1", "t1", "5", "17", "80", "33", "62", "99", "41");
        var expected = service.aggregate(answers, null, snapshot(settings(5)));
        Random random = new Random(7);
        for (int i = 0; i < 20; i++) {
            List<ScopedResponse> shuffled = new ArrayList<>(answers);
            Collections.shuffle(shuffled, random);
            assertThat(service.aggregate(shuffled, null, snapshot(settings(5)))).isEqualTo(expected);
        }
    }

    @Test
    void identicalAnswersCombineToTheSingleValue() {
        var outcome = service.aggregate(members("Q1", null, "37.5", "37.5", "37.5", "37.5", "37.5"), null, snapshot(ResponseSettings.defaults()));
        assertThat(input(outcome, "Q1").normalizedValue().value()).isEqualByComparingTo("37.5");
    }

    @Test
    void oneOrganizationAnswerPassesThroughLikeTheEarlierForm() {
        ScoringConfigurationSnapshot cfg = snapshot(ResponseSettings.defaults());
        var outcome = service.aggregate(List.of(ScopedResponse.organization("Q1", d("72"))), null, cfg);
        NormalizedInput expected = normalizationService().normalize(List.of(new ScoringInput("Q1", d("72"))), cfg).stream()
                .filter(i -> i.questionId().equals("Q1")).findFirst().orElseThrow();
        assertThat(input(outcome, "Q1")).isEqualTo(expected);
        ResponseSummary summary = outcome.summaries().getFirst();
        assertThat(summary.participation()).isEqualTo(ResponseSummary.Participation.UNKNOWN);
        assertThat(summary.respondents()).isNull();
        assertThat(summary.belowMinimum()).isFalse();
        assertThat(outcome.participation()).isEmpty();
    }

    @Test
    void fewerMemberAnswersThanTheMinimumMakeTheQuestionMissing() {
        var outcome = service.aggregate(members("Q1", null, "10", "20", "30", "40"), null, snapshot(ResponseSettings.defaults()));
        assertThat(input(outcome, "Q1").status()).isEqualTo(NormalizationStatus.MISSING_IGNORED);
        assertThat(input(outcome, "Q1").value()).isEmpty();
        ResponseSummary summary = outcome.summaries().getFirst();
        assertThat(summary.belowMinimum()).isTrue();
        assertThat(summary.respondents()).isEqualTo(4);
        assertThat(outcome.participation()).isEmpty();
    }

    @Test
    void theQuestionsMissingPolicyAppliesOnceWhenBelowTheMinimum() {
        var outcome = service.aggregate(members("N1", null, "10", "20"), null, snapshot(ResponseSettings.defaults()));
        assertThat(input(outcome, "N1").status()).isEqualTo(NormalizationStatus.NEUTRAL_APPLIED);
        assertThat(input(outcome, "N1").normalizedValue().value()).isEqualByComparingTo("50");
    }

    @Test
    void blankAnswersAreNotCountedAsRespondents() {
        List<ScopedResponse> answers = new ArrayList<>(members("Q1", null, "10", "20", "30"));
        answers.add(ScopedResponse.member("Q1", null, null));
        answers.add(ScopedResponse.member("Q1", "", null));
        var outcome = service.aggregate(answers, null, snapshot(settings(3)));
        assertThat(outcome.summaries().getFirst().respondents()).isEqualTo(3);
        assertThat(input(outcome, "Q1").normalizedValue().value()).isEqualByComparingTo("20");
    }

    @Test
    void sufficientMembersGiveObservedParticipation() {
        var outcome = service.aggregate(members("Q1", null, "10", "20", "30", "40", "50"), null, snapshot(ResponseSettings.defaults()));
        assertThat(outcome.summaries().getFirst().participation()).isEqualTo(ResponseSummary.Participation.OBSERVED);
        assertThat(outcome.participation()).isPresent();
        assertThat(outcome.participation().get().respondentsByQuestion()).containsEntry("Q1", 5);
    }

    @Test
    void declaredCountBelowTheMinimumMakesATeamFigureMissing() {
        var outcome = service.aggregate(List.of(ScopedResponse.team("Q1", d("80"), "t1", 3)), "t1", snapshot(ResponseSettings.defaults()));
        assertThat(input(outcome, "Q1").status()).isEqualTo(NormalizationStatus.MISSING_IGNORED);
        assertThat(outcome.summaries().getFirst().participation()).isEqualTo(ResponseSummary.Participation.DECLARED);
    }

    @Test
    void declaredCountAtTheMinimumIsAccepted() {
        var outcome = service.aggregate(List.of(ScopedResponse.team("Q1", d("80"), "t1", 5)), "t1", snapshot(ResponseSettings.defaults()));
        assertThat(input(outcome, "Q1").normalizedValue().value()).isEqualByComparingTo("80");
        assertThat(outcome.participation().get().respondentsByQuestion()).containsEntry("Q1", 5);
    }

    @Test
    void teamFiguresCombineAcrossTeamsInTheOrganizationUnitAndSumTheirCounts() {
        List<ScopedResponse> answers = List.of(ScopedResponse.team("Q1", d("60"), "t1", 4), ScopedResponse.team("Q1", d("80"), "t2", 6));
        var outcome = service.aggregate(answers, null, snapshot(ResponseSettings.defaults()));
        assertThat(input(outcome, "Q1").normalizedValue().value()).isEqualByComparingTo("70");
        assertThat(outcome.summaries().getFirst().respondents()).isEqualTo(10);
    }

    @Test
    void teamUnitUsesOnlyThatTeamsAnswers() {
        List<ScopedResponse> answers = new ArrayList<>();
        answers.addAll(members("Q1", "t1", "10", "10", "10", "10", "10"));
        answers.addAll(members("Q1", "t2", "90", "90", "90", "90", "90"));
        answers.addAll(members("Q1", null, "50", "50", "50", "50", "50"));
        answers.add(ScopedResponse.organization("Q3", d("1")));
        var team = service.aggregate(answers, "t1", snapshot(ResponseSettings.defaults()));
        assertThat(input(team, "Q1").normalizedValue().value()).isEqualByComparingTo("10");
        assertThat(input(team, "Q3").status()).isEqualTo(NormalizationStatus.MISSING_IGNORED);

        var organization = service.aggregate(answers, null, snapshot(ResponseSettings.defaults()));
        // 5 x 10, 5 x 90, 5 x 50: median 50
        assertThat(input(organization, "Q1").normalizedValue().value()).isEqualByComparingTo("50");
    }

    @Test
    void textSignalNeedsTheConfiguredShareOfResponses() {
        List<ScopedResponse> three = new ArrayList<>();
        for (String text : new String[]{"only one person", "key person", "single owner", "fine", "fine"}) {
            three.add(ScopedResponse.member("Q4", text, null));
        }
        var present = service.aggregate(three, null, snapshot(ResponseSettings.defaults()));
        assertThat(input(present, "Q4").detectedSignals()).contains("SINGLE_POINT_OF_FAILURE");

        List<ScopedResponse> two = new ArrayList<>();
        for (String text : new String[]{"only one person", "key person", "fine", "fine", "fine"}) {
            two.add(ScopedResponse.member("Q4", text, null));
        }
        var absent = service.aggregate(two, null, snapshot(ResponseSettings.defaults()));
        assertThat(input(absent, "Q4").detectedSignals()).isEmpty();
        assertThat(input(absent, "Q4").status()).isEqualTo(NormalizationStatus.SIGNALS_ONLY);
    }

    @Test
    void mixingScopesForOneQuestionIsRejected() {
        List<ScopedResponse> answers = new ArrayList<>(members("Q1", "t1", "10", "20"));
        answers.add(ScopedResponse.team("Q1", d("50"), "t1", 5));
        assertCode(() -> service.aggregate(answers, null, snapshot(ResponseSettings.defaults())), ScoringErrorCode.MIXED_RESPONSE_SCOPES);
    }

    @Test
    void twoOrganizationFiguresForOneQuestionAreADuplicate() {
        List<ScopedResponse> answers = List.of(ScopedResponse.organization("Q1", d("1")), ScopedResponse.organization("Q1", d("2")));
        assertCode(() -> service.aggregate(answers, null, snapshot(ResponseSettings.defaults())), ScoringErrorCode.DUPLICATE_QUESTION_INPUT);
    }

    @Test
    void twoFiguresForOneTeamAndQuestionAreRejected() {
        List<ScopedResponse> answers = List.of(ScopedResponse.team("Q1", d("1"), "t1", 5), ScopedResponse.team("Q1", d("2"), "t1", 5));
        assertCode(() -> service.aggregate(answers, null, snapshot(ResponseSettings.defaults())), ScoringErrorCode.DUPLICATE_TEAM_SCOPE);
    }

    @Test
    void unknownQuestionIsRejected() {
        assertCode(() -> service.aggregate(members("ZZ", null, "1"), null, snapshot(ResponseSettings.defaults())),
                ScoringErrorCode.QUESTION_NOT_CONFIGURED);
    }

    @Test
    void everyConfiguredQuestionIsPresentInTheResultInOrder() {
        var outcome = service.aggregate(members("Q1", null, "10", "20", "30", "40", "50"), null, snapshot(ResponseSettings.defaults()));
        assertThat(outcome.inputs()).extracting(NormalizedInput::questionId).containsExactly("N1", "Q1", "Q2", "Q3", "Q4");
    }

    @Test
    void scopedResponseValidation() {
        assertCode(() -> new ScopedResponse("Q1", 1, ResponseScope.TEAM, null, null), ScoringErrorCode.INVALID_RESPONSE_SCOPE);
        assertCode(() -> new ScopedResponse("Q1", 1, ResponseScope.ORGANIZATION, "t1", null), ScoringErrorCode.INVALID_RESPONSE_SCOPE);
        assertCode(() -> new ScopedResponse("Q1", 1, ResponseScope.MEMBER, "t1", 3), ScoringErrorCode.INVALID_RESPONSE_SCOPE);
        assertCode(() -> new ScopedResponse("Q1", 1, ResponseScope.TEAM, "t1", 0), ScoringErrorCode.INVALID_RESPONSE_SCOPE);
        assertCode(() -> new ScopedResponse("Q1", 1, ResponseScope.MEMBER, "bad team!", null), ScoringErrorCode.INVALID_RESPONSE_SCOPE);
    }

    @Test
    void responseSettingsValidation() {
        assertCode(() -> new ResponseSettings(AggregationMethod.MEAN, 0, new BigDecimal("0.5"), 10), ScoringErrorCode.INVALID_RESPONSE_CONFIGURATION);
        assertCode(() -> new ResponseSettings(AggregationMethod.MEAN, 5, BigDecimal.ZERO, 10), ScoringErrorCode.INVALID_RESPONSE_CONFIGURATION);
        assertCode(() -> new ResponseSettings(AggregationMethod.MEAN, 5, new BigDecimal("1.1"), 10), ScoringErrorCode.INVALID_RESPONSE_CONFIGURATION);
        assertCode(() -> new ResponseSettings(AggregationMethod.MEAN, 5, new BigDecimal("0.5"), 4), ScoringErrorCode.INVALID_RESPONSE_CONFIGURATION);
        assertThat(ResponseSettings.defaults().minResponses()).isEqualTo(5);
    }
}
