package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.OutOfRangePolicy;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.enums.UnknownOptionPolicy;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.Breakpoint;
import com.company.leadershipintelligence.domain.rule.NumberConfig;
import com.company.leadershipintelligence.domain.strategy.NormalizationResult;
import com.company.leadershipintelligence.domain.strategy.NormalizationStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.Fixtures.frequency;
import static com.company.leadershipintelligence.domain.Fixtures.nv;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.Fixtures.singlePointOfFailureText;
import static com.company.leadershipintelligence.domain.Fixtures.yesIsGood;
import static org.assertj.core.api.Assertions.assertThat;

class OtherStrategiesTest {

    private static NormalizationResult run(NormalizationStrategy strategy, QuestionDefinition q, Object answer) {
        return strategy.normalize(new ScoringInput(q.questionId(), answer), q, q.normalization());
    }

    // ---- NUMBER ----
    private final NumberNormalizationStrategy number = new NumberNormalizationStrategy();

    private QuestionDefinition numberQuestion(ScaleDirection dir, OutOfRangePolicy policy) {
        return question("N1", new NumberConfig(d("0"), d("100"), dir, policy, null), MissingDataPolicy.IGNORE);
    }

    @Test
    void numberLinearPositiveAndNegative() {
        QuestionDefinition pos = numberQuestion(ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT);
        QuestionDefinition neg = numberQuestion(ScaleDirection.NEGATIVE, OutOfRangePolicy.REJECT);
        assertThat(run(number, pos, 0).value().value()).isEqualByComparingTo("0");
        assertThat(run(number, pos, 42.5d).value().value()).isEqualByComparingTo("42.5");
        assertThat(run(number, pos, 100L).value().value()).isEqualByComparingTo("100");
        assertThat(run(number, neg, 30).value().value()).isEqualByComparingTo("70");
    }

    @Test
    void numberOutOfRangeFollowsPolicy() {
        assertCode(() -> run(number, numberQuestion(ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT), 101),
                ScoringErrorCode.INVALID_NUMBER_VALUE);
        assertThat(run(number, numberQuestion(ScaleDirection.POSITIVE, OutOfRangePolicy.CLAMP), 250).value().value())
                .isEqualByComparingTo("100");
        assertCode(() -> run(number, numberQuestion(ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT), "12"),
                ScoringErrorCode.INVALID_ANSWER_TYPE);
    }

    @Test
    void numberPiecewiseInterpolates() {
        NumberConfig config = new NumberConfig(d("0"), d("10"), ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT,
                List.of(new Breakpoint(d("0"), nv("100")), new Breakpoint(d("2"), nv("60")), new Breakpoint(d("10"), nv("0"))));
        QuestionDefinition q = question("N2", config, MissingDataPolicy.IGNORE);
        assertThat(run(number, q, 0).value().value()).isEqualByComparingTo("100");
        assertThat(run(number, q, 1).value().value()).isEqualByComparingTo("80");
        assertThat(run(number, q, 2).value().value()).isEqualByComparingTo("60");
        assertThat(run(number, q, 6).value().value()).isEqualByComparingTo("30");
        assertThat(run(number, q, 10).value().value()).isEqualByComparingTo("0");
    }

    @Test
    void numberBreakpointsAreValidated() {
        assertCode(() -> new NumberConfig(d("0"), d("10"), ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT,
                        List.of(new Breakpoint(d("0"), nv("0")))), ScoringErrorCode.INVALID_NORMALIZATION_CONFIGURATION);
        assertCode(() -> new NumberConfig(d("0"), d("10"), ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT,
                        List.of(new Breakpoint(d("1"), nv("0")), new Breakpoint(d("10"), nv("100")))),
                ScoringErrorCode.INVALID_NORMALIZATION_CONFIGURATION);
        assertCode(() -> new NumberConfig(d("0"), d("10"), ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT,
                        List.of(new Breakpoint(d("0"), nv("0")), new Breakpoint(d("5"), nv("50")),
                                new Breakpoint(d("5"), nv("60")), new Breakpoint(d("10"), nv("100")))),
                ScoringErrorCode.INVALID_NORMALIZATION_CONFIGURATION);
    }

    // ---- MULTIPLE_CHOICE ----
    private final MultipleChoiceNormalizationStrategy choice = new MultipleChoiceNormalizationStrategy();

    @Test
    void multipleChoiceMapsEveryConfiguredOption() {
        QuestionDefinition q = question("M1", frequency(UnknownOptionPolicy.REJECT), MissingDataPolicy.IGNORE);
        assertThat(run(choice, q, "NEVER").value().value()).isEqualByComparingTo("0");
        assertThat(run(choice, q, "SOMETIMES").value().value()).isEqualByComparingTo("50");
        assertThat(run(choice, q, "ALWAYS").value().value()).isEqualByComparingTo("100");
    }

    @Test
    void unknownOptionIsNeverZero() {
        QuestionDefinition reject = question("M1", frequency(UnknownOptionPolicy.REJECT), MissingDataPolicy.IGNORE);
        assertCode(() -> run(choice, reject, "MAYBE"), ScoringErrorCode.INVALID_MULTIPLE_CHOICE_VALUE);
        assertCode(() -> run(choice, reject, "often"), ScoringErrorCode.INVALID_MULTIPLE_CHOICE_VALUE);
        QuestionDefinition asMissing = question("M2", frequency(UnknownOptionPolicy.TREAT_AS_MISSING), MissingDataPolicy.IGNORE);
        NormalizationResult result = run(choice, asMissing, "MAYBE");
        assertThat(result.treatAsMissing()).isTrue();
        assertThat(result.value()).isNull();
        assertCode(() -> run(choice, reject, 3), ScoringErrorCode.INVALID_ANSWER_TYPE);
    }

    // ---- BOOLEAN ----
    private final BooleanNormalizationStrategy bool = new BooleanNormalizationStrategy();

    @Test
    void booleanMapsTrueAndFalseAndHonoursReversal() {
        QuestionDefinition q = question("B1", yesIsGood(), MissingDataPolicy.IGNORE);
        assertThat(run(bool, q, true).value().value()).isEqualByComparingTo("100");
        assertThat(run(bool, q, false).value().value()).isEqualByComparingTo("0");
        QuestionDefinition reversed = question("B2", new com.company.leadershipintelligence.domain.rule.BooleanConfig(nv("0"), nv("100")),
                MissingDataPolicy.IGNORE);
        assertThat(run(bool, reversed, true).value().value()).isEqualByComparingTo("0");
        assertThat(run(bool, reversed, false).value().value()).isEqualByComparingTo("100");
    }

    @Test
    void booleanRejectsOtherTypes() {
        QuestionDefinition q = question("B1", yesIsGood(), MissingDataPolicy.IGNORE);
        assertCode(() -> run(bool, q, "true"), ScoringErrorCode.INVALID_ANSWER_TYPE);
        assertCode(() -> run(bool, q, 1), ScoringErrorCode.INVALID_ANSWER_TYPE);
    }

    // ---- TEXT ----
    private final TextNormalizationStrategy text = new TextNormalizationStrategy();
    private final QuestionDefinition textQuestion = question("Q04", singlePointOfFailureText(), MissingDataPolicy.IGNORE);

    @Test
    void textRaisesFlagsAndNeverAValue() {
        NormalizationResult hit = run(text, textQuestion, "We frequently depend on one senior engineer, a KEY PERSON.");
        assertThat(hit.value()).isNull();
        assertThat(hit.signals()).containsExactly("SINGLE_POINT_OF_FAILURE");
        assertThat(run(text, textQuestion, "Work is shared across the whole team.").signals()).isEmpty();
    }

    @Test
    void textMatchesWholeWordsAndPhrasesOnly() {
        assertThat(run(text, textQuestion, "We feel lonely sometimes").signals()).isEmpty();
        assertThat(run(text, textQuestion, "Only one deploys.").signals()).containsExactly("SINGLE_POINT_OF_FAILURE");
        assertThat(run(text, textQuestion, "depends on one person").signals()).containsExactly("SINGLE_POINT_OF_FAILURE");
        assertThat(run(text, textQuestion, "one senior person").signals()).isEmpty();
    }

    @Test
    void textMatchingIsDeterministic() {
        String answer = "only a single key person";
        assertThat(run(text, textQuestion, answer).signals()).isEqualTo(run(text, textQuestion, answer).signals());
        assertCode(() -> run(text, textQuestion, 5), ScoringErrorCode.INVALID_ANSWER_TYPE);
    }
}
