package com.company.leadershipintelligence.domain.service.patterns;

import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.model.ParticipationInfo;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.num;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.th001;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The participation factor of confidence algorithm version 2 (specv3.md Section 14.2.1, decision D45). */
class PatternConfidenceParticipationTest {

    private final PatternConfidenceService service = new PatternConfidenceService(ConfidenceBandConfig.illustrative());

    /** Both signals in the shared fixtures come from question Q1. */
    private PatternConfidenceService.Assessment assess(Optional<ParticipationInfo> participation) {
        EvaluationContext context = EvaluationContext.of(
                List.of(num("WORKLOAD_PRESSURE", "80"), num("TEAM_SUSTAINABILITY", "30")), List.of(), List.of(), participation);
        return service.assess(th001(), context);
    }

    @Test
    void withoutParticipationTheEarlierAlgorithmApplies() {
        PatternConfidenceService.Assessment a = assess(Optional.empty());
        assertThat(a.breakdown().algorithmVersion()).isEqualTo("1");
        assertThat(a.breakdown().participation()).isNull();
        assertThat(a.confidence().value()).isEqualByComparingTo("1");
    }

    @Test
    void fullParticipationDoesNotLowerConfidence() {
        PatternConfidenceService.Assessment a = assess(ParticipationInfo.of(Map.of("Q1", 10), 10));
        assertThat(a.breakdown().algorithmVersion()).isEqualTo("2");
        assertThat(a.breakdown().participation()).isEqualByComparingTo("1");
        assertThat(a.breakdown().respondents()).isEqualTo(10);
        assertThat(a.confidence().value()).isEqualByComparingTo("1");
    }

    @Test
    void moreRespondentsThanNeededAreCappedAtOne() {
        PatternConfidenceService.Assessment a = assess(ParticipationInfo.of(Map.of("Q1", 40), 10));
        assertThat(a.breakdown().participation()).isEqualByComparingTo("1");
    }

    @Test
    void fewRespondentsScaleConfidenceDown() {
        PatternConfidenceService.Assessment a = assess(ParticipationInfo.of(Map.of("Q1", 6), 10));
        assertThat(a.breakdown().participation()).isEqualByComparingTo("0.6");
        assertThat(a.confidence().value()).isEqualByComparingTo("0.60");
    }

    @Test
    void theLowestCountAmongSupportingQuestionsIsUsed() {
        // the pattern's evidence comes from Q1 only; Q9 having fewer respondents is irrelevant to it
        PatternConfidenceService.Assessment a = assess(ParticipationInfo.of(Map.of("Q1", 8, "Q9", 5), 10));
        assertThat(a.breakdown().respondents()).isEqualTo(8);
        assertThat(a.breakdown().participation()).isEqualByComparingTo("0.8");
    }

    @Test
    void unknownParticipationForTheSupportingQuestionsGivesFactorOneAndNoCount() {
        PatternConfidenceService.Assessment a = assess(ParticipationInfo.of(Map.of("Q9", 5), 10));
        assertThat(a.breakdown().algorithmVersion()).isEqualTo("2");
        assertThat(a.breakdown().participation()).isEqualByComparingTo("1");
        assertThat(a.breakdown().respondents()).isNull();
    }

    @Test
    void participationInfoNeedsAKnownCountAndAPositiveFullThreshold() {
        assertThat(ParticipationInfo.of(Map.of(), 10)).isEmpty();
        assertThatThrownBy(() -> new ParticipationInfo(Map.of("Q1", 3), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.assess(th001(), EvaluationContext.of(List.of(), List.of(), List.of())).breakdown().algorithmVersion()).isEqualTo("1");
        assertThat(PatternConfidenceService.versionFor(true)).isEqualTo("2");
        assertThat(PatternConfidenceService.versionFor(false)).isEqualTo("1");
    }
}
