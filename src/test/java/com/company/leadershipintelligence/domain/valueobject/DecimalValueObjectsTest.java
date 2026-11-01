package com.company.leadershipintelligence.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecimalValueObjectsTest {

    @Test
    void scoreValueAcceptsBoundaries() {
        assertThat(ScoreValue.of("0").value()).isEqualByComparingTo("0");
        assertThat(ScoreValue.of("100").value()).isEqualByComparingTo("100");
    }

    @Test
    void scoreValueRejectsOutOfRangeAndNull() {
        assertThatThrownBy(() -> ScoreValue.of("-0.01")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScoreValue.of("100.01")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoreValue(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityIgnoresScale() {
        assertThat(ScoreValue.of("69.5")).isEqualTo(ScoreValue.of("69.50"));
        assertThat(ScoreValue.of("69.5")).hasSameHashCodeAs(ScoreValue.of("69.50"));
        assertThat(ScoreValue.of("0")).isEqualTo(ScoreValue.of("0.00")).hasSameHashCodeAs(ScoreValue.of("0.00"));
        assertThat(ScoreValue.of("69.5")).isNotEqualTo(ScoreValue.of("69.51"));
    }

    @Test
    void roundingIsHalfUpAtTwoDecimals() {
        assertThat(ScoreValue.of("59.996").rounded().value()).isEqualTo(new BigDecimal("60.00"));
        assertThat(ScoreValue.of("0.005").rounded().value()).isEqualTo(new BigDecimal("0.01"));
        assertThat(ScoreValue.of("0.004").rounded().value()).isEqualTo(new BigDecimal("0.00"));
        assertThat(ScoreValue.of("67.745").rounded().value()).isEqualTo(new BigDecimal("67.75"));
        assertThat(ScoreValue.of("99.999").rounded().value()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void clampKeepsValuesInRange() {
        assertThat(ScoreValue.clamp(new BigDecimal("-5"))).isEqualTo(ScoreValue.of("0"));
        assertThat(ScoreValue.clamp(new BigDecimal("105"))).isEqualTo(ScoreValue.of("100"));
        assertThat(ScoreValue.clamp(new BigDecimal("42.5"))).isEqualTo(ScoreValue.of("42.5"));
    }

    @Test
    void normalizedValueIsBoundedToZeroHundred() {
        assertThat(new NormalizedValue(new BigDecimal("100")).value()).isEqualByComparingTo("100");
        assertThatThrownBy(() -> new NormalizedValue(new BigDecimal("100.5"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NormalizedValue(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confidenceIsBoundedToZeroOne() {
        assertThat(new ConfidenceValue(new BigDecimal("0.70")).value()).isEqualByComparingTo("0.7");
        assertThat(new ConfidenceValue(BigDecimal.ONE).value()).isEqualByComparingTo("1");
        assertThatThrownBy(() -> new ConfidenceValue(new BigDecimal("1.01"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfidenceValue(new BigDecimal("-0.01"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void penaltyAmountMustNotBeNegative() {
        assertThat(new PenaltyAmount(BigDecimal.ZERO)).isEqualTo(PenaltyAmount.ZERO);
        assertThat(new PenaltyAmount(new BigDecimal("1000")).value()).isEqualByComparingTo("1000");
        assertThatThrownBy(() -> new PenaltyAmount(new BigDecimal("-0.01"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PenaltyAmount(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void penaltyAmountArithmeticStaysNonNegative() {
        PenaltyAmount three = new PenaltyAmount(new BigDecimal("3"));
        PenaltyAmount eight = new PenaltyAmount(new BigDecimal("8"));
        assertThat(three.plus(eight)).isEqualTo(new PenaltyAmount(new BigDecimal("11")));
        assertThat(three.min(eight)).isEqualTo(three);
        assertThat(eight.min(three)).isEqualTo(three);
    }

    @Test
    void weightMustNotBeNegativeAndDetectsZero() {
        assertThat(new ScoreWeight(BigDecimal.ZERO).isZero()).isTrue();
        assertThat(new ScoreWeight(new BigDecimal("0.25")).isZero()).isFalse();
        assertThatThrownBy(() -> new ScoreWeight(new BigDecimal("-0.1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void divisionUsesScaleTenHalfUp() {
        assertThat(Decimals.divide(BigDecimal.ONE, new BigDecimal("3"))).isEqualTo(new BigDecimal("0.3333333333"));
        assertThat(Decimals.divide(new BigDecimal("2"), new BigDecimal("3"))).isEqualTo(new BigDecimal("0.6666666667"));
        assertThatThrownBy(() -> Decimals.divide(BigDecimal.ONE, BigDecimal.ZERO)).isInstanceOf(ArithmeticException.class);
    }
}
