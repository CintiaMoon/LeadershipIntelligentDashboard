package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.NormalizationStatus;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelRecordsTest {

    private static NormalizedValue nv(String v) {
        return new NormalizedValue(new BigDecimal(v));
    }

    @Test
    void scoringInputRequiresQuestionIdAndTreatsNullAnswerAsMissing() {
        assertThatThrownBy(() -> new ScoringInput(" ", 4)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ScoringInput("Q01", null).isMissing()).isTrue();
        assertThat(new ScoringInput("Q03", false).isMissing()).isFalse();
        assertThat(new ScoringInput("Q01", 0).isMissing()).isFalse();
    }

    @Test
    void normalizedInputWithValueRequiresValueCarryingStatus() {
        NormalizedInput ok = new NormalizedInput("Q01", QuestionType.SCALE, nv("75"), Set.of(),
                NormalizationStatus.NORMALIZED, "scale", "1");
        assertThat(ok.value()).contains(nv("75"));

        assertThatThrownBy(() -> new NormalizedInput("Q01", QuestionType.SCALE, nv("75"), Set.of(),
                NormalizationStatus.MISSING_IGNORED, "scale", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NormalizedInput("Q01", QuestionType.SCALE, null, Set.of(),
                NormalizationStatus.NORMALIZED, "scale", "1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingAnswerIsNeverRepresentedAsZero() {
        NormalizedInput missing = new NormalizedInput("Q01", QuestionType.SCALE, null, null,
                NormalizationStatus.MISSING_IGNORED, "scale", "1");
        assertThat(missing.value()).isEmpty();
        assertThat(missing.detectedSignals()).isEmpty();
    }

    @Test
    void detectedSignalsAreSortedImmutableAndCopied() {
        java.util.HashSet<String> source = new java.util.HashSet<>(List.of("ZED", "ALPHA", "MID"));
        NormalizedInput text = new NormalizedInput("Q04", QuestionType.TEXT, null, source,
                NormalizationStatus.SIGNALS_ONLY, "text", "1");
        source.add("LATE");
        assertThat(text.detectedSignals()).containsExactly("ALPHA", "MID", "ZED");
        assertThatThrownBy(() -> text.detectedSignals().add("X")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void normalizedInputRequiresProvenance() {
        assertThatThrownBy(() -> new NormalizedInput("Q01", QuestionType.SCALE, nv("1"), Set.of(),
                NormalizationStatus.NORMALIZED, " ", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NormalizedInput("Q01", null, nv("1"), Set.of(),
                NormalizationStatus.NORMALIZED, "scale", "1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scoringContextRequiresEveryField() {
        ScoringContext ctx = new ScoringContext(new TenantId("t"), new OrganizationId("o"), new AssessmentId("a"),
                new AssessmentSnapshotId("s"), new ConfigurationVersion("c1"), "3.0.0", "1.0.0");
        assertThat(ctx.tenantId()).isEqualTo(new TenantId("t"));

        assertThatThrownBy(() -> new ScoringContext(null, new OrganizationId("o"), new AssessmentId("a"),
                new AssessmentSnapshotId("s"), new ConfigurationVersion("c1"), "3.0.0", "1.0.0"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ScoringContext(new TenantId("t"), new OrganizationId("o"), new AssessmentId("a"),
                new AssessmentSnapshotId("s"), new ConfigurationVersion("c1"), "", "1.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
