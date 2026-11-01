package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.OutOfRangePolicy;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.model.NormalizedInput;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.NumberConfig;
import com.company.leadershipintelligence.domain.rule.ScaleConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.Fixtures.normalizationService;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.Fixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

/** A clamped value must be visible in the normalization record so the explanation can say so. */
class ClampRecordedTest {

    private final ScoringConfigurationSnapshot config = snapshot(List.of(
            question("S", new ScaleConfig(d("1"), d("5"), ScaleDirection.POSITIVE, OutOfRangePolicy.CLAMP, true), MissingDataPolicy.IGNORE),
            question("N", new NumberConfig(d("0"), d("100"), ScaleDirection.POSITIVE, OutOfRangePolicy.CLAMP, null), MissingDataPolicy.IGNORE)),
            List.of());

    private NormalizedInput one(String q, Object answer) {
        return normalizationService().normalize(List.of(new ScoringInput(q, answer)), config).stream()
                .filter(n -> n.questionId().equals(q)).findFirst().orElseThrow();
    }

    @Test
    void clampedValuesAreFlaggedAndInRangeValuesAreNot() {
        assertThat(one("S", 9).clamped()).isTrue();
        assertThat(one("S", 0).clamped()).isTrue();
        assertThat(one("S", 3).clamped()).isFalse();
        assertThat(one("S", 5).clamped()).isFalse();
        assertThat(one("N", 150).clamped()).isTrue();
        assertThat(one("N", 100).clamped()).isFalse();
        assertThat(one("S", 9).normalizedValue().value()).isEqualByComparingTo("100");
    }
}
