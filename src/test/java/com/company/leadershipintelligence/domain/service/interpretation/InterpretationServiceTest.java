package com.company.leadershipintelligence.domain.service.interpretation;

import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.InterpretationResult;
import com.company.leadershipintelligence.domain.model.OverallScore;
import com.company.leadershipintelligence.domain.rule.InterpretationConfig;
import com.company.leadershipintelligence.domain.rule.InterpretationRange;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;
import com.company.leadershipintelligence.domain.valueobject.ScoreValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static org.assertj.core.api.Assertions.assertThat;

class InterpretationServiceTest {

    private final InterpretationService service = new InterpretationService();
    private final InterpretationConfig config = InterpretationConfig.illustrative();

    private String label(String score) {
        return service.interpret(ScoreValue.of(score), config).label();
    }

    private static InterpretationRange r(String min, String max, String label) {
        return new InterpretationRange(d(min), d(max), label);
    }

    @Test
    void everyRangeAndItsBoundaries() {
        assertThat(label("0")).isEqualTo("CRITICAL");
        assertThat(label("39.99")).isEqualTo("CRITICAL");
        assertThat(label("40")).isEqualTo("AT_RISK");
        assertThat(label("59.99")).isEqualTo("AT_RISK");
        assertThat(label("60")).isEqualTo("HEALTHY");
        assertThat(label("79.99")).isEqualTo("HEALTHY");
        assertThat(label("80")).isEqualTo("STRONG");
        assertThat(label("100")).isEqualTo("STRONG");
    }

    @Test
    void decimalScoresBetweenTheOldIntegerBoundsAreCovered() {
        assertThat(label("39.5")).isEqualTo("CRITICAL");
        assertThat(label("39.995")).isEqualTo("CRITICAL");
        assertThat(label("67.75")).isEqualTo("HEALTHY");
        assertThat(label("69.50")).isEqualTo("HEALTHY");
    }

    @Test
    void everyScoreInTheScaleMapsToExactlyOneLabel() {
        for (int hundredths = 0; hundredths <= 10000; hundredths += 7) {
            ScoreValue score = new ScoreValue(java.math.BigDecimal.valueOf(hundredths, 2));
            assertThat(config.ranges().stream().filter(range -> config.find(score.value()).orElseThrow() == range)).hasSize(1);
        }
    }

    @Test
    void resultReportsTheRangeThatMatched() {
        InterpretationResult result = service.interpret(ScoreValue.of("69.5"), config);
        assertThat(result.rangeMin()).isEqualByComparingTo("60");
        assertThat(result.rangeMax()).isEqualByComparingTo("80");
    }

    @Test
    void interpretationDoesNotChangeTheScore() {
        ScoreValue score = ScoreValue.of("69.50");
        service.interpret(score, config);
        assertThat(score.value().toPlainString()).isEqualTo("69.50");
    }

    @Test
    void lisAndDimensionsCanUseDifferentConfigurations() {
        InterpretationConfig coarse = new InterpretationConfig(List.of(r("0", "50", "LOW"), r("50", "100", "HIGH")));
        assertThat(service.interpret(ScoreValue.of("49.99"), coarse).label()).isEqualTo("LOW");
        assertThat(service.interpret(ScoreValue.of("50"), coarse).label()).isEqualTo("HIGH");
        assertThat(label("49.99")).isEqualTo("AT_RISK");
    }

    @Test
    void insufficientDataHasNoInterpretation() {
        OverallScore insufficient = new OverallScore(ScoreStatus.INSUFFICIENT_DATA, List.of(), null, PenaltyAmount.ZERO, null);
        assertThat(service.interpret(insufficient, config)).isEmpty();
        OverallScore valid = new OverallScore(ScoreStatus.VALID, List.of(), ScoreValue.of("69.5"), PenaltyAmount.ZERO, ScoreValue.of("69.5"));
        assertThat(service.interpret(valid, config)).get().extracting(InterpretationResult::label).isEqualTo("HEALTHY");
    }

    // ---- validation ----

    @Test
    void gapsOverlapsAndBadBoundsAreRejected() {
        assertCode(() -> new InterpretationConfig(List.of(r("0", "39", "A"), r("40", "100", "B"))), ScoringErrorCode.INVALID_INTERPRETATION_CONFIGURATION);
        assertCode(() -> new InterpretationConfig(List.of(r("0", "50", "A"), r("40", "100", "B"))), ScoringErrorCode.INVALID_INTERPRETATION_CONFIGURATION);
        assertCode(() -> new InterpretationConfig(List.of(r("5", "50", "A"), r("50", "100", "B"))), ScoringErrorCode.INVALID_INTERPRETATION_CONFIGURATION);
        assertCode(() -> new InterpretationConfig(List.of(r("0", "50", "A"), r("50", "99", "B"))), ScoringErrorCode.INVALID_INTERPRETATION_CONFIGURATION);
        assertCode(() -> new InterpretationConfig(List.of(r("0", "50", "A"), r("50", "100", "A"))), ScoringErrorCode.INVALID_INTERPRETATION_CONFIGURATION);
        assertCode(() -> new InterpretationConfig(List.of()), ScoringErrorCode.INVALID_INTERPRETATION_CONFIGURATION);
        assertCode(() -> r("50", "50", "A"), ScoringErrorCode.INVALID_INTERPRETATION_CONFIGURATION);
        assertCode(() -> r("0", "50", " "), ScoringErrorCode.INVALID_INTERPRETATION_CONFIGURATION);
    }

    @Test
    void rangeOrderInTheConfigurationDoesNotMatter() {
        InterpretationConfig shuffled = new InterpretationConfig(List.of(r("50", "100", "HIGH"), r("0", "50", "LOW")));
        assertThat(shuffled.ranges()).extracting(InterpretationRange::label).containsExactly("LOW", "HIGH");
    }
}
