package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.NormalizationStatus;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.enums.UnknownOptionPolicy;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.NormalizedInput;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.booleanSignal;
import static com.company.leadershipintelligence.domain.Fixtures.frequency;
import static com.company.leadershipintelligence.domain.Fixtures.normalizationService;
import static com.company.leadershipintelligence.domain.Fixtures.nv;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.Fixtures.scale15;
import static com.company.leadershipintelligence.domain.Fixtures.singlePointOfFailureText;
import static com.company.leadershipintelligence.domain.Fixtures.snapshot;
import static com.company.leadershipintelligence.domain.Fixtures.yesIsGood;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizationServiceTest {

    /** Specv3 Section 7.3 example input set: Q01 scale, Q02 multiple choice, Q03 boolean, Q04 text. */
    private final ScoringConfigurationSnapshot sample = snapshot(List.of(
            question("Q01", scale15(ScaleDirection.POSITIVE), MissingDataPolicy.IGNORE),
            question("Q02", frequency(UnknownOptionPolicy.REJECT), MissingDataPolicy.IGNORE),
            question("Q03", yesIsGood(), MissingDataPolicy.IGNORE),
            question("Q04", singlePointOfFailureText(), MissingDataPolicy.IGNORE)),
            List.of(booleanSignal("SINGLE_POINT_OF_FAILURE", "Q04")));

    private final List<ScoringInput> sampleInputs = List.of(
            new ScoringInput("Q01", 4),
            new ScoringInput("Q02", "OFTEN"),
            new ScoringInput("Q03", true),
            new ScoringInput("Q04", "We frequently depend on one senior engineer."));

    @Test
    void mixedQuestionTypesAreNormalizedByTheirOwnStrategies() {
        List<NormalizedInput> result = normalizationService().normalize(sampleInputs, sample);

        assertThat(result).extracting(NormalizedInput::questionId).containsExactly("Q01", "Q02", "Q03", "Q04");
        assertThat(result).extracting(NormalizedInput::strategyId)
                .containsExactly("scale", "multiple-choice", "boolean", "text");
        assertThat(result.get(0).normalizedValue()).isEqualTo(nv("75"));
        assertThat(result.get(1).normalizedValue()).isEqualTo(nv("75"));
        assertThat(result.get(2).normalizedValue()).isEqualTo(nv("100"));
        assertThat(result.get(3).value()).isEmpty();
        assertThat(result.get(3).status()).isEqualTo(NormalizationStatus.SIGNALS_ONLY);
        // "one senior engineer" does not contain a configured keyword phrase: no flag, not a risk-free claim
        assertThat(result.get(3).detectedSignals()).isEmpty();
    }

    @Test
    void outputDoesNotDependOnTheOrderInputsAreSupplied() {
        List<ScoringInput> reversed = new java.util.ArrayList<>(sampleInputs);
        java.util.Collections.reverse(reversed);
        assertThat(normalizationService().normalize(reversed, sample)).isEqualTo(normalizationService().normalize(sampleInputs, sample));
    }

    @Test
    void sameInputsGiveIdenticalOutput() {
        assertThat(normalizationService().normalize(sampleInputs, sample))
                .isEqualTo(normalizationService().normalize(sampleInputs, sample));
    }

    @Test
    void unansweredConfiguredQuestionsAreIncludedAndOutputIsInQuestionIdOrder() {
        List<NormalizedInput> result = normalizationService().normalize(
                List.of(new ScoringInput("Q03", false)), sample);
        assertThat(result).extracting(NormalizedInput::questionId).containsExactly("Q01", "Q02", "Q03", "Q04");
        assertThat(result).filteredOn(n -> !n.questionId().equals("Q03")).extracting(NormalizedInput::status)
                .containsOnly(NormalizationStatus.MISSING_IGNORED);
    }

    @Test
    void unknownQuestionIsRejected() {
        assertCode(() -> normalizationService().normalize(List.of(new ScoringInput("Q99", 1)), sample),
                ScoringErrorCode.QUESTION_NOT_CONFIGURED);
    }

    @Test
    void duplicateAnswersAreRejected() {
        assertCode(() -> normalizationService().normalize(
                        List.of(new ScoringInput("Q01", 1), new ScoringInput("Q01", 2)), sample),
                ScoringErrorCode.DUPLICATE_QUESTION_INPUT);
    }

    @Test
    void errorsNeverContainTheRawAnswer() {
        assertThatThrownBy(() -> normalizationService().normalize(List.of(new ScoringInput("Q01", 9876)), sample))
                .hasMessageNotContaining("9876");
    }

    // ---- missing data policies ----

    private ScoringConfigurationSnapshot withPolicy(MissingDataPolicy policy) {
        QuestionDefinition q = switch (policy) {
            case NEUTRAL -> new QuestionDefinition("Q01", QuestionType.SCALE, scale15(ScaleDirection.POSITIVE),
                    policy, nv("40"), null);
            case RISK_SIGNAL -> new QuestionDefinition("Q01", QuestionType.SCALE, scale15(ScaleDirection.POSITIVE),
                    policy, null, "DATA_GAP");
            default -> question("Q01", scale15(ScaleDirection.POSITIVE), policy);
        };
        return snapshot(List.of(q), policy == MissingDataPolicy.RISK_SIGNAL ? List.of(booleanSignal("DATA_GAP", "Q01")) : List.of());
    }

    @Test
    void nullAnswerFollowsIgnorePolicyAndIsNotZero() {
        NormalizedInput result = normalizationService().normalize(List.of(new ScoringInput("Q01", null)),
                withPolicy(MissingDataPolicy.IGNORE)).get(0);
        assertThat(result.status()).isEqualTo(NormalizationStatus.MISSING_IGNORED);
        assertThat(result.value()).isEmpty();
    }

    @Test
    void nullAnswerFollowsNeutralPolicyWithConfiguredValue() {
        NormalizedInput result = normalizationService().normalize(List.of(new ScoringInput("Q01", null)),
                withPolicy(MissingDataPolicy.NEUTRAL)).get(0);
        assertThat(result.status()).isEqualTo(NormalizationStatus.NEUTRAL_APPLIED);
        assertThat(result.normalizedValue()).isEqualTo(nv("40"));
    }

    @Test
    void nullAnswerFollowsRiskSignalPolicy() {
        NormalizedInput result = normalizationService().normalize(List.of(new ScoringInput("Q01", null)),
                withPolicy(MissingDataPolicy.RISK_SIGNAL)).get(0);
        assertThat(result.status()).isEqualTo(NormalizationStatus.MISSING_RISK_SIGNAL);
        assertThat(result.value()).isEmpty();
        assertThat(result.detectedSignals()).containsExactly("DATA_GAP");
    }

    @Test
    void requiredAnswerMissingFailsScoring() {
        assertCode(() -> normalizationService().normalize(List.of(new ScoringInput("Q01", null)),
                withPolicy(MissingDataPolicy.REQUIRED)), ScoringErrorCode.MISSING_REQUIRED_INPUT);
        assertCode(() -> normalizationService().normalize(List.of(), withPolicy(MissingDataPolicy.REQUIRED)),
                ScoringErrorCode.MISSING_REQUIRED_INPUT);
    }

    @Test
    void blankTextIsMissingNotAbsenceOfRisk() {
        NormalizedInput result = normalizationService().normalize(List.of(new ScoringInput("Q04", "   ")), sample).stream()
                .filter(n -> n.questionId().equals("Q04")).findFirst().orElseThrow();
        assertThat(result.status()).isEqualTo(NormalizationStatus.MISSING_IGNORED);
    }

    @Test
    void zeroAndFalseAreRealAnswersNotMissing() {
        ScoringConfigurationSnapshot zeroOk = snapshot(List.of(
                question("Q01", new com.company.leadershipintelligence.domain.rule.ScaleConfig(
                        new java.math.BigDecimal("0"), new java.math.BigDecimal("10"), ScaleDirection.POSITIVE,
                        com.company.leadershipintelligence.domain.enums.OutOfRangePolicy.REJECT, true), MissingDataPolicy.REQUIRED),
                question("Q03", yesIsGood(), MissingDataPolicy.REQUIRED)), List.of());
        List<NormalizedInput> result = normalizationService().normalize(
                List.of(new ScoringInput("Q01", 0), new ScoringInput("Q03", false)), zeroOk);
        assertThat(result).extracting(NormalizedInput::status).containsOnly(NormalizationStatus.NORMALIZED);
    }

    @Test
    void unknownMultipleChoiceOptionCanBeRoutedToMissingPolicy() {
        ScoringConfigurationSnapshot snap = snapshot(List.of(
                question("Q02", frequency(UnknownOptionPolicy.TREAT_AS_MISSING), MissingDataPolicy.IGNORE)), List.of());
        NormalizedInput result = normalizationService().normalize(List.of(new ScoringInput("Q02", "MAYBE")), snap).get(0);
        assertThat(result.status()).isEqualTo(NormalizationStatus.MISSING_IGNORED);
    }

    @Test
    void unsupportedQuestionTypeIsReported() {
        com.company.leadershipintelligence.domain.service.normalization.NormalizationService scaleOnly =
                new com.company.leadershipintelligence.domain.service.normalization.NormalizationService(List.of(
                        new com.company.leadershipintelligence.domain.service.normalization.ScaleNormalizationStrategy()));
        assertCode(() -> scaleOnly.normalize(List.of(new ScoringInput("Q03", true)), sample),
                ScoringErrorCode.UNSUPPORTED_QUESTION_TYPE);
    }

    @Test
    void registeringTwoStrategiesForOneTypeIsRejected() {
        assertThatThrownBy(() -> new com.company.leadershipintelligence.domain.service.normalization.NormalizationService(List.of(
                new com.company.leadershipintelligence.domain.service.normalization.ScaleNormalizationStrategy(),
                new com.company.leadershipintelligence.domain.service.normalization.ScaleNormalizationStrategy())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outputValuesAlwaysStayInRange() {
        for (int x = 1; x <= 5; x++) {
            NormalizedInput n = normalizationService().normalize(List.of(new ScoringInput("Q01", x)), sample).get(0);
            assertThat(n.normalizedValue().value()).isBetween(new java.math.BigDecimal("0"), new java.math.BigDecimal("100"));
        }
    }
}
