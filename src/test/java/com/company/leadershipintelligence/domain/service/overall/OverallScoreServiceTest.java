package com.company.leadershipintelligence.domain.service.overall;

import com.company.leadershipintelligence.domain.enums.DimensionStatus;
import com.company.leadershipintelligence.domain.enums.PenaltyTarget;
import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.AdjustedDimension;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.OverallScore;
import com.company.leadershipintelligence.domain.model.PenaltyAggregation;
import com.company.leadershipintelligence.domain.rule.OverallWeights;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.insufficient;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.score;
import static org.assertj.core.api.Assertions.assertThat;

class OverallScoreServiceTest {

    private final OverallScoreService service = new OverallScoreService();

    private static OverallWeights equalWeights() {
        return weights("0.25", "0.25", "0.25", "0.25", "0");
    }

    private static OverallWeights weights(String team, String delivery, String technical, String leadership, String tolerance) {
        Map<ScoringDimension, ScoreWeight> map = new EnumMap<>(ScoringDimension.class);
        map.put(ScoringDimension.TEAM_HEALTH, new ScoreWeight(d(team)));
        map.put(ScoringDimension.DELIVERY_STABILITY, new ScoreWeight(d(delivery)));
        map.put(ScoringDimension.TECHNICAL_RISK, new ScoreWeight(d(technical)));
        map.put(ScoringDimension.LEADERSHIP_LOAD, new ScoreWeight(d(leadership)));
        return new OverallWeights(map, d(tolerance));
    }

    private static List<DimensionScore> raw(String team, String delivery, String technical, String leadership) {
        return List.of(score(ScoringDimension.TEAM_HEALTH, team), score(ScoringDimension.DELIVERY_STABILITY, delivery),
                score(ScoringDimension.TECHNICAL_RISK, technical), score(ScoringDimension.LEADERSHIP_LOAD, leadership));
    }

    private static PenaltyAggregation penalties(String team, String delivery, String technical, String leadership, String overall) {
        Map<PenaltyTarget, PenaltyAmount> map = new EnumMap<>(PenaltyTarget.class);
        map.put(PenaltyTarget.TEAM_HEALTH, new PenaltyAmount(d(team)));
        map.put(PenaltyTarget.DELIVERY_STABILITY, new PenaltyAmount(d(delivery)));
        map.put(PenaltyTarget.TECHNICAL_RISK, new PenaltyAmount(d(technical)));
        map.put(PenaltyTarget.LEADERSHIP_LOAD, new PenaltyAmount(d(leadership)));
        map.put(PenaltyTarget.OVERALL, new PenaltyAmount(d(overall)));
        return new PenaltyAggregation(List.of(), map, "1");
    }

    private static AdjustedDimension of(OverallScore score, ScoringDimension dimension) {
        return score.dimensions().stream().filter(a -> a.dimension() == dimension).findFirst().orElseThrow();
    }

    @Test
    void specSection176Example() {
        OverallScore result = service.calculate(raw("84", "76", "66", "70"), penalties("3", "0", "8", "7", "0"), equalWeights());
        assertThat(result.status()).isEqualTo(ScoreStatus.VALID);
        assertThat(result.dimensions()).extracting(a -> a.adjustedScore().value().toPlainString())
                .containsExactly("81.00", "76.00", "58.00", "63.00");
        assertThat(result.weightedAdjustedScore().value().toPlainString()).isEqualTo("69.50");
        assertThat(result.finalScore().value().toPlainString()).isEqualTo("69.50");
        assertThat(result.overallPenaltyApplied().value()).isEqualByComparingTo("0");
    }

    @Test
    void legacySection30ExampleStillHolds() {
        OverallScore result = service.calculate(raw("82", "76", "64", "58"), penalties("0", "0", "5", "4", "0"), equalWeights());
        assertThat(result.dimensions()).extracting(a -> a.adjustedScore().value().toPlainString())
                .containsExactly("82.00", "76.00", "59.00", "54.00");
        assertThat(result.finalScore().value().toPlainString()).isEqualTo("67.75");
        OverallScore noPenalties = service.calculate(raw("82", "76", "64", "58"), penalties("0", "0", "0", "0", "0"), equalWeights());
        assertThat(noPenalties.finalScore().value().toPlainString()).isEqualTo("70.00");
    }

    @Test
    void overallPenaltyIsAppliedOnceAfterWeighting() {
        OverallScore result = service.calculate(raw("84", "76", "66", "70"), penalties("3", "0", "8", "7", "5"), equalWeights());
        assertThat(result.weightedAdjustedScore().value().toPlainString()).isEqualTo("69.50");
        assertThat(result.overallPenaltyApplied().value()).isEqualByComparingTo("5");
        assertThat(result.finalScore().value().toPlainString()).isEqualTo("64.50");
        // dimension scores are untouched by the overall penalty
        assertThat(result.dimensions()).extracting(a -> a.adjustedScore().value().toPlainString())
                .containsExactly("81.00", "76.00", "58.00", "63.00");
    }

    @Test
    void adjustedScoreNeverGoesBelowZeroNorAboveRaw() {
        OverallScore result = service.calculate(raw("5", "50", "50", "50"), penalties("8", "0", "0", "0", "0"), equalWeights());
        AdjustedDimension team = of(result, ScoringDimension.TEAM_HEALTH);
        assertThat(team.adjustedScore().value()).isEqualByComparingTo("0");
        for (AdjustedDimension a : result.dimensions()) {
            assertThat(a.adjustedScore().value()).isLessThanOrEqualTo(a.rawScore().value());
        }
    }

    @Test
    void finalScoreIsClampedToZero() {
        OverallScore result = service.calculate(raw("10", "10", "10", "10"), penalties("0", "0", "0", "0", "30"), equalWeights());
        assertThat(result.weightedAdjustedScore().value()).isEqualByComparingTo("10");
        assertThat(result.finalScore().value()).isEqualByComparingTo("0");
    }

    @Test
    void customWeightsAreHonoured() {
        OverallScore result = service.calculate(raw("100", "50", "0", "0"), penalties("0", "0", "0", "0", "0"),
                weights("0.4", "0.3", "0.2", "0.1", "0"));
        // 40 + 15
        assertThat(result.finalScore().value().toPlainString()).isEqualTo("55.00");
        assertThat(of(result, ScoringDimension.DELIVERY_STABILITY).weightedContribution()).isEqualByComparingTo("15");
    }

    @Test
    void weightedContributionsSumToTheWeightedScore() {
        OverallScore result = service.calculate(raw("84", "76", "66", "70"), penalties("3", "0", "8", "7", "0"), equalWeights());
        BigDecimal sum = result.dimensions().stream().map(AdjustedDimension::weightedContribution).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(result.weightedAdjustedScore().value());
    }

    @Test
    void weightedScoreIsRoundedHalfUpToTwoDecimals() {
        // (67.74 + 67.75) / 2 = 67.745 -> 67.75
        OverallScore result = service.calculate(raw("67.74", "67.75", "0", "0"), penalties("0", "0", "0", "0", "0"),
                weights("0.5", "0.5", "0", "0", "0"));
        assertThat(result.finalScore().value().toPlainString()).isEqualTo("67.75");
    }

    @Test
    void insufficientDimensionMakesTheScoreInsufficientNotANumber() {
        List<DimensionScore> scores = new ArrayList<>(raw("84", "76", "66", "70"));
        scores.set(2, insufficient(ScoringDimension.TECHNICAL_RISK));
        OverallScore result = service.calculate(scores, penalties("3", "0", "0", "7", "5"), equalWeights());
        assertThat(result.status()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        assertThat(result.finalScore()).isNull();
        assertThat(result.weightedAdjustedScore()).isNull();
        assertThat(result.score()).isEmpty();
        assertThat(result.overallPenaltyApplied().value()).isEqualByComparingTo("0");
        AdjustedDimension technical = of(result, ScoringDimension.TECHNICAL_RISK);
        assertThat(technical.rawScore()).isNull();
        assertThat(technical.adjustedScore()).isNull();
        assertThat(of(result, ScoringDimension.TEAM_HEALTH).adjustedScore().value()).isEqualByComparingTo("81");
    }

    @Test
    void everyDimensionInsufficientGivesNoNumbersAtAll() {
        List<DimensionScore> none = List.of(insufficient(ScoringDimension.TEAM_HEALTH), insufficient(ScoringDimension.DELIVERY_STABILITY),
                insufficient(ScoringDimension.TECHNICAL_RISK), insufficient(ScoringDimension.LEADERSHIP_LOAD));
        OverallScore result = service.calculate(none, penalties("0", "0", "0", "0", "0"), equalWeights());
        assertThat(result.status()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        assertThat(result.dimensions()).allSatisfy(a -> assertThat(a.rawScore()).isNull());
        assertThat(DimensionStatus.valueOf("INSUFFICIENT_DATA")).isNotNull();
    }

    @Test
    void identicalInputsGiveIdenticalResults() {
        assertThat(service.calculate(raw("84", "76", "66", "70"), penalties("3", "0", "8", "7", "2"), equalWeights()))
                .isEqualTo(service.calculate(raw("84", "76", "66", "70"), penalties("3", "0", "8", "7", "2"), equalWeights()));
    }

    // ---- weight validation ----

    @Test
    void weightsMustCoverAllDimensionsAndSumToOneWithinTolerance() {
        assertCode(() -> weights("0.25", "0.25", "0.25", "0.30", "0"), ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION);
        assertCode(() -> weights("0.25", "0.25", "0.25", "0.20", "0"), ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION);
        assertCode(() -> new OverallWeights(Map.of(ScoringDimension.TEAM_HEALTH, new ScoreWeight(d("1"))), d("0")),
                ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION);
        assertCode(() -> weights("0.25", "0.25", "0.25", "0.25", "-0.1"), ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION);
    }

    @Test
    void toleranceBoundaryIsInclusive() {
        assertThat(weights("0.25", "0.25", "0.25", "0.2499", "0.0001")).isNotNull();
        assertThat(weights("0.25", "0.25", "0.25", "0.2501", "0.0001")).isNotNull();
        assertCode(() -> weights("0.25", "0.25", "0.25", "0.2498", "0.0001"), ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION);
    }
}
