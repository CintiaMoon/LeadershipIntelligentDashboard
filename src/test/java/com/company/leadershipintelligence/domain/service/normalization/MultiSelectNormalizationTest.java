package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.NormalizationStatus;
import com.company.leadershipintelligence.domain.enums.UnknownOptionPolicy;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.NormalizedInput;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.MultiSelectConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.nv;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.Fixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MULTI_SELECT (specv3.md Section 4.2.1, decision D49): the mean of the chosen options' values. */
class MultiSelectNormalizationTest {

    private final NormalizationService service = new NormalizationService(List.of(
            new ScaleNormalizationStrategy(), new MultipleChoiceNormalizationStrategy(), new MultiSelectNormalizationStrategy(),
            new BooleanNormalizationStrategy(), new NumberNormalizationStrategy(), new TextNormalizationStrategy()));

    private static MultiSelectConfig options(UnknownOptionPolicy policy) {
        Map<String, com.company.leadershipintelligence.domain.valueobject.NormalizedValue> values = new LinkedHashMap<>();
        values.put("MONITORING", nv("100"));
        values.put("ON_CALL", nv("50"));
        values.put("RUNBOOKS", nv("80"));
        values.put("NONE", nv("0"));
        return new MultiSelectConfig(values, policy);
    }

    private ScoringConfigurationSnapshot config(UnknownOptionPolicy policy, MissingDataPolicy missing) {
        return snapshot(List.of(question("Q", options(policy), missing)), List.of());
    }

    private NormalizedInput normalize(Object answer, UnknownOptionPolicy policy, MissingDataPolicy missing) {
        return service.normalize(List.of(new ScoringInput("Q", answer)), config(policy, missing)).get(0);
    }

    private NormalizedInput normalize(Object answer) {
        return normalize(answer, UnknownOptionPolicy.REJECT, MissingDataPolicy.IGNORE);
    }

    @Test
    void theValueIsTheMeanOfTheChosenOptions() {
        assertThat(normalize(List.of("MONITORING", "ON_CALL")).normalizedValue().value()).isEqualByComparingTo("75");
        assertThat(normalize(List.of("MONITORING", "ON_CALL", "RUNBOOKS")).normalizedValue().value()).isEqualByComparingTo("76.6666666667");
    }

    @Test
    void oneChosenOptionKeepsItsOwnValue() {
        NormalizedInput one = normalize(List.of("RUNBOOKS"));
        assertThat(one.normalizedValue().value()).isEqualByComparingTo("80");
        assertThat(one.status()).isEqualTo(NormalizationStatus.NORMALIZED);
    }

    @Test
    void theOrderDoesNotMatterAndARepeatedOptionCountsOnce() {
        List<String> chosen = new ArrayList<>(List.of("MONITORING", "ON_CALL", "RUNBOOKS"));
        NormalizedInput expected = normalize(chosen);
        Random random = new Random(3);
        for (int i = 0; i < 10; i++) {
            Collections.shuffle(chosen, random);
            assertThat(normalize(chosen)).isEqualTo(expected);
        }
        assertThat(normalize(List.of("MONITORING", "MONITORING", "ON_CALL")).normalizedValue().value()).isEqualByComparingTo("75");
        assertThat(normalize(Set.of("ON_CALL")).normalizedValue().value()).isEqualByComparingTo("50");
    }

    @Test
    void anOptionWorthZeroStillCountsAsAnAnswer() {
        assertThat(normalize(List.of("NONE")).normalizedValue().value()).isEqualByComparingTo("0");
        assertThat(normalize(List.of("NONE")).status()).isEqualTo(NormalizationStatus.NORMALIZED);
        assertThat(normalize(List.of("NONE", "MONITORING")).normalizedValue().value()).isEqualByComparingTo("50");
    }

    @Test
    void anEmptySelectionIsMissingNeverZero() {
        assertThat(normalize(List.of()).status()).isEqualTo(NormalizationStatus.MISSING_IGNORED);
        assertThat(normalize(List.of()).value()).isEmpty();
        assertThat(normalize(null).status()).isEqualTo(NormalizationStatus.MISSING_IGNORED);
    }

    @Test
    void anEmptySelectionFollowsTheQuestionsMissingDataPolicy() {
        MultiSelectConfig neutral = options(UnknownOptionPolicy.REJECT);
        ScoringConfigurationSnapshot config = snapshot(List.of(new com.company.leadershipintelligence.domain.model.QuestionDefinition(
                "Q", neutral.questionType(), neutral, MissingDataPolicy.NEUTRAL, nv("50"), null)), List.of());
        NormalizedInput result = service.normalize(List.of(new ScoringInput("Q", List.of())), config).get(0);
        assertThat(result.status()).isEqualTo(NormalizationStatus.NEUTRAL_APPLIED);
        assertCode(() -> service.normalize(List.of(new ScoringInput("Q", List.of())), config(UnknownOptionPolicy.REJECT, MissingDataPolicy.REQUIRED)),
                ScoringErrorCode.MISSING_REQUIRED_INPUT);
    }

    @Test
    void anUnknownOptionIsRejectedOrIgnoredAccordingToThePolicy() {
        assertCode(() -> normalize(List.of("MONITORING", "TELEPORT")), ScoringErrorCode.INVALID_MULTIPLE_CHOICE_VALUE);
        NormalizedInput ignored = normalize(List.of("MONITORING", "TELEPORT"), UnknownOptionPolicy.TREAT_AS_MISSING, MissingDataPolicy.IGNORE);
        assertThat(ignored.normalizedValue().value()).isEqualByComparingTo("100");
        NormalizedInput none = normalize(List.of("TELEPORT"), UnknownOptionPolicy.TREAT_AS_MISSING, MissingDataPolicy.IGNORE);
        assertThat(none.status()).isEqualTo(NormalizationStatus.MISSING_IGNORED);
    }

    @Test
    void anythingThatIsNotAListOfOptionsIsRejected() {
        assertCode(() -> normalize("MONITORING"), ScoringErrorCode.INVALID_ANSWER_TYPE);
        assertCode(() -> normalize(3), ScoringErrorCode.INVALID_ANSWER_TYPE);
        assertCode(() -> normalize(true), ScoringErrorCode.INVALID_ANSWER_TYPE);
        assertCode(() -> normalize(List.of("MONITORING", 3)), ScoringErrorCode.INVALID_ANSWER_TYPE);
        assertCode(() -> normalize(List.of(List.of("MONITORING"))), ScoringErrorCode.INVALID_ANSWER_TYPE);
    }

    @Test
    void aSelectionNeedsAtLeastOneConfiguredOption() {
        assertThatThrownBy(() -> new MultiSelectConfig(Map.of(), UnknownOptionPolicy.REJECT)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new MultiSelectConfig(Map.of("A", nv("1")), null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void aMultiSelectQuestionIsNotTheSameTypeAsMultipleChoice() {
        // a string option that is valid for MULTIPLE_CHOICE is not a valid MULTI_SELECT answer
        assertCode(() -> normalize("ON_CALL"), ScoringErrorCode.INVALID_ANSWER_TYPE);
    }
}
