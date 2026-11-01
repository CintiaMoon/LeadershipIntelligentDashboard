package com.company.leadershipintelligence.domain.service.aggregation;

import com.company.leadershipintelligence.domain.strategy.AggregationConfiguration;
import com.company.leadershipintelligence.domain.strategy.AggregationResult;
import com.company.leadershipintelligence.domain.strategy.WeightedContribution;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.Fixtures.nv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightedAverageStrategyTest {

    private final WeightedAverageStrategy strategy = new WeightedAverageStrategy();
    private final AggregationConfiguration config = new AggregationConfiguration("1");

    private static WeightedContribution wc(String q, String value, String weight) {
        return new WeightedContribution(q, nv(value), new ScoreWeight(d(weight)));
    }

    @Test
    void sharesSumToTheScore() {
        AggregationResult result = strategy.aggregate(List.of(wc("A", "80", "0.7"), wc("B", "60", "0.3")), config);
        assertThat(result.score()).isEqualByComparingTo("74");
        assertThat(result.shares().get(0).contribution()).isEqualByComparingTo("56");
        assertThat(result.shares().get(1).contribution()).isEqualByComparingTo("18");
        assertThat(result.shares().get(0).normalizedWeight()).isEqualByComparingTo("0.7");
    }

    @Test
    void weightsAreNormalizedByTheirSum() {
        AggregationResult result = strategy.aggregate(List.of(wc("A", "100", "2"), wc("B", "0", "6")), config);
        assertThat(result.score()).isEqualByComparingTo("25");
        assertThat(result.shares().get(1).normalizedWeight()).isEqualByComparingTo("0.75");
    }

    @Test
    void singleContributionIsItsOwnValue() {
        assertThat(strategy.aggregate(List.of(wc("A", "42.5", "0.3")), config).score()).isEqualByComparingTo("42.5");
    }

    @Test
    void nonTerminatingWeightsStillSumExactlyToTheScore() {
        AggregationResult result = strategy.aggregate(List.of(wc("A", "10", "1"), wc("B", "20", "1"), wc("C", "60", "1")), config);
        BigDecimal sum = result.shares().stream().map(AggregationResult.Share::contribution).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(result.score());
        assertThat(result.score()).isEqualByComparingTo("30");
        // each share is within 10^-9 of its exact value (the residual goes to the largest share)
        assertThat(result.shares().get(2).contribution().subtract(d("20")).abs()).isLessThan(d("0.000000001"));
        assertThat(result.shares().get(0).contribution().subtract(d("3.3333333333")).abs()).isLessThan(d("0.000000001"));
    }

    @Test
    void roundingTiesAreDecidedOnTheExactValue() {
        // exact average 16.665: must stay a tie at scale 10, not drift below it
        AggregationResult result = strategy.aggregate(List.of(wc("A", "16.665", "1"), wc("B", "16.665", "2"), wc("C", "16.665", "3")), config);
        assertThat(result.score()).isEqualByComparingTo("16.665");
        assertThat(com.company.leadershipintelligence.domain.valueobject.Decimals.roundScore(result.score()).toPlainString())
                .isEqualTo("16.67");
    }

    @Test
    void rejectsEmptyInputAndZeroWeights() {
        assertThatThrownBy(() -> strategy.aggregate(List.of(), config)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wc("A", "50", "0")).isInstanceOf(IllegalArgumentException.class);
    }
}
