package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.OutOfRangePolicy;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.ScaleConfig;
import org.junit.jupiter.api.Test;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.Fixtures.scale15;
import static org.assertj.core.api.Assertions.assertThat;

class ScaleNormalizationStrategyTest {

    private final ScaleNormalizationStrategy strategy = new ScaleNormalizationStrategy();

    private String normalize(QuestionDefinition q, Object answer) {
        return strategy.normalize(new ScoringInput(q.questionId(), answer), q, q.normalization()).value().value().toPlainString();
    }

    private final QuestionDefinition positive = question("Q01", scale15(ScaleDirection.POSITIVE), MissingDataPolicy.IGNORE);
    private final QuestionDefinition negative = question("Q02", scale15(ScaleDirection.NEGATIVE), MissingDataPolicy.IGNORE);

    @Test
    void positiveScaleBoundariesAndMiddle() {
        assertThat(d(normalize(positive, 1))).isEqualByComparingTo("0");
        assertThat(d(normalize(positive, 3))).isEqualByComparingTo("50");
        assertThat(d(normalize(positive, 4))).isEqualByComparingTo("75");
        assertThat(d(normalize(positive, 5))).isEqualByComparingTo("100");
    }

    @Test
    void negativeScaleReversesDirection() {
        assertThat(d(normalize(negative, 1))).isEqualByComparingTo("100");
        assertThat(d(normalize(negative, 4))).isEqualByComparingTo("25");
        assertThat(d(normalize(negative, 5))).isEqualByComparingTo("0");
    }

    @Test
    void decimalsAreSupportedWhenAllowed() {
        assertThat(d(normalize(positive, new java.math.BigDecimal("2.5")))).isEqualByComparingTo("37.5");
        assertThat(d(normalize(positive, 2.5d))).isEqualByComparingTo("37.5");
    }

    @Test
    void decimalsAreRejectedWhenNotAllowed() {
        QuestionDefinition integers = question("Q03",
                new ScaleConfig(d("1"), d("5"), ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT, false), MissingDataPolicy.IGNORE);
        assertCode(() -> normalize(integers, new java.math.BigDecimal("2.5")), ScoringErrorCode.INVALID_SCALE_VALUE);
        assertThat(d(normalize(integers, new java.math.BigDecimal("2.0")))).isEqualByComparingTo("25");
    }

    @Test
    void nonTerminatingResultUsesScaleTenHalfUp() {
        QuestionDefinition zeroToThree = question("Q04",
                new ScaleConfig(d("0"), d("3"), ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT, true), MissingDataPolicy.IGNORE);
        assertThat(normalize(zeroToThree, 1)).isEqualTo("33.3333333333");
        assertThat(normalize(zeroToThree, 2)).isEqualTo("66.6666666667");
    }

    @Test
    void outOfRangeIsRejectedByDefaultPolicy() {
        assertCode(() -> normalize(positive, 0), ScoringErrorCode.INVALID_SCALE_VALUE);
        assertCode(() -> normalize(positive, 6), ScoringErrorCode.INVALID_SCALE_VALUE);
    }

    @Test
    void outOfRangeIsClampedOnlyWhenConfigured() {
        QuestionDefinition clamp = question("Q05",
                new ScaleConfig(d("1"), d("5"), ScaleDirection.POSITIVE, OutOfRangePolicy.CLAMP, true), MissingDataPolicy.IGNORE);
        assertThat(d(normalize(clamp, 0))).isEqualByComparingTo("0");
        assertThat(d(normalize(clamp, 9))).isEqualByComparingTo("100");
    }

    @Test
    void wrongAnswerTypesAreRejected() {
        assertCode(() -> normalize(positive, "4"), ScoringErrorCode.INVALID_ANSWER_TYPE);
        assertCode(() -> normalize(positive, true), ScoringErrorCode.INVALID_ANSWER_TYPE);
        assertCode(() -> normalize(positive, Double.NaN), ScoringErrorCode.INVALID_SCALE_VALUE);
        assertCode(() -> normalize(positive, Double.POSITIVE_INFINITY), ScoringErrorCode.INVALID_SCALE_VALUE);
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertCode(() -> new ScaleConfig(d("5"), d("5"), ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT, true),
                ScoringErrorCode.INVALID_NORMALIZATION_CONFIGURATION);
        assertCode(() -> new ScaleConfig(d("6"), d("5"), ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT, true),
                ScoringErrorCode.INVALID_NORMALIZATION_CONFIGURATION);
    }
}
