package com.company.leadershipintelligence.repository.configuration;

import com.company.leadershipintelligence.domain.enums.PatternStatus;
import com.company.leadershipintelligence.domain.enums.PenaltyStatus;
import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.model.PatternEvaluation;
import com.company.leadershipintelligence.domain.model.PenaltyResult;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringContext;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.model.ScoringResult;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;
import com.company.leadershipintelligence.domain.service.ScoringOrchestrator;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The initial platform configuration (src/main/resources/config/initial-platform-default.json). Every rule is an initial,
 * unvalidated proposal; these tests do not say the thresholds are right, they say the document is complete, follows the
 * specification's catalog (identifiers and suggested maxima) and behaves as described when scored by the real engine.
 */
public class InitialConfigurationTest {

    static final String RESOURCE = "/config/initial-platform-default.json";

    /** specv3.md 15.5 suggested maximum deductions, for the penalties this configuration enables. */
    static final Map<String, Integer> SPEC_MAXIMUM = Map.ofEntries(
            Map.entry("TH-P01", 12), Map.entry("TH-P02", 8), Map.entry("DS-P01", 10), Map.entry("DS-P03", 6), Map.entry("DS-P05", 12),
            Map.entry("DS-P09", 10), Map.entry("TR-P03", 12), Map.entry("TR-P04", 8), Map.entry("TR-P05", 15), Map.entry("TR-P06", 6),
            Map.entry("LL-P01", 10), Map.entry("LL-P04", 8), Map.entry("LL-P06", 12), Map.entry("LL-P08", 5), Map.entry("XD-P01", 8),
            Map.entry("XD-P08", 10));

    public static String text() throws IOException {
        try (var in = InitialConfigurationTest.class.getResourceAsStream(RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static ScoringConfigurationSnapshot snapshot() throws IOException {
        return ConfigurationSamples.PARSER.parse(text(), new ConfigurationVersion("initial-1"));
    }

    // ---------------- the document itself ----------------

    @Test
    void parsesAndPassesEveryDomainValidation() throws IOException {
        ScoringConfigurationSnapshot s = snapshot();
        assertThat(s.questions()).hasSize(20);
        assertThat(s.signals()).hasSize(17);
        assertThat(s.patterns()).hasSize(16);
        assertThat(s.penalties()).hasSize(16);
        assertThat(s.imbalanceRules()).hasSize(1);
        assertThat(s.recommendations()).hasSize(17);
        assertThat(s.responses().minResponses()).isEqualTo(5);
    }

    @Test
    void everyDimensionHasFiveQuestionsWithEqualWeight() throws IOException {
        ScoringConfigurationSnapshot s = snapshot();
        for (ScoringDimension dimension : ScoringDimension.values()) {
            var rules = s.dimensions().rules().stream().filter(r -> r.dimension() == dimension).toList();
            assertThat(rules).as(dimension.name()).hasSize(5);
            assertThat(rules.stream().map(r -> r.weight().value().stripTrailingZeros()).distinct()).hasSize(1);
            assertThat(s.dimensions().settings().get(dimension).requiredCoverage()).isEqualByComparingTo("0.75");
        }
        assertThat(s.overall().weights().weights().values().stream().map(w -> w.value()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1");
    }

    @Test
    void usesOnlyIdentifiersFromTheSpecificationCatalogAndNeverExceedsItsSuggestedMaxima() throws IOException {
        ScoringConfigurationSnapshot s = snapshot();
        Pattern patternId = Pattern.compile("(TH|DS|TR|LL|XD)-\\d{3}");
        s.patterns().forEach(p -> assertThat(p.patternId()).matches(patternId));
        assertThat(s.penalties().stream().map(PenaltyDefinition::penaltyId).collect(Collectors.toSet())).isEqualTo(SPEC_MAXIMUM.keySet());
        for (PenaltyDefinition penalty : s.penalties()) {
            assertThat(penalty.maximumDeduction().value()).as(penalty.penaltyId()).isEqualByComparingTo(String.valueOf(SPEC_MAXIMUM.get(penalty.penaltyId())));
            assertThat(penalty.penaltyId().substring(0, 2)).isEqualTo(penalty.triggerPatternId().substring(0, 2));
        }
        // the cross-dimensional penalties are the only ones that touch the overall score
        s.penalties().forEach(p -> assertThat(p.target().name().equals("OVERALL")).as(p.penaltyId()).isEqualTo(p.penaltyId().startsWith("XD")));
    }

    @Test
    void everyActionAndEveryEntryHasTextForTheReader() throws IOException {
        ScoringConfigurationSnapshot s = snapshot();
        Set<String> actions = s.patterns().stream().flatMap(p -> p.actionKeys().stream()).collect(Collectors.toSet());
        actions.add(s.imbalanceRules().get(0).recommendationKey());
        assertThat(s.recommendations().keySet()).containsAll(actions);
        s.recommendations().values().forEach(r -> {
            assertThat(r.title()).isNotBlank();
            assertThat(r.description()).isNotBlank();
        });

        var presentation = s.presentation();
        assertThat(presentation.patterns().keySet()).containsExactlyInAnyOrderElementsOf(s.patterns().stream().map(p -> p.patternId()).toList());
        assertThat(presentation.penalties().keySet()).isEqualTo(SPEC_MAXIMUM.keySet());
        assertThat(presentation.questions().keySet()).isEqualTo(s.questions().keySet());
        assertThat(presentation.signals().keySet()).isEqualTo(s.signals().keySet());
        assertThat(presentation.dimensions()).hasSize(4);
        presentation.patterns().values().forEach(e -> assertThat(e.explanation()).isNotBlank());
        presentation.penalties().values().forEach(e -> assertThat(e.explanation()).isNotBlank());
    }

    @Test
    void explainsThatPatternsDescribeConditionsNotPeople() throws IOException {
        for (var entry : snapshot().presentation().patterns().entrySet()) {
            String explanation = entry.getValue().explanation();
            boolean aboutPeople = explanation.contains("not any individual") || explanation.contains("not a judgement of that person")
                    || entry.getKey().startsWith("XD-001");
            assertThat(aboutPeople).as(entry.getKey() + " should say it is not about an individual").isTrue();
        }
    }

    // ---------------- behaviour through the real engine ----------------

    private final ScoringOrchestrator engine = ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative());

    /** The best possible answer to every question: 100 on every normalized value. */
    public static Map<String, Object> best() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("TH01", 5); a.put("TH02", 1); a.put("TH03", 5); a.put("TH04", 5); a.put("TH05", 5);
        a.put("DS01", "ALWAYS"); a.put("DS02", 1); a.put("DS03", 0); a.put("DS04", 5); a.put("DS05", false);
        a.put("TR01", 0); a.put("TR02", 5); a.put("TR03", 5); a.put("TR04", 1); a.put("TR05", false);
        a.put("LL01", 1); a.put("LL02", 0); a.put("LL03", 1); a.put("LL04", 5); a.put("LL05", 5);
        return a;
    }

    /** The worst possible answer to every question: 0 on every normalized value. */
    public static Map<String, Object> worst() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("TH01", 1); a.put("TH02", 5); a.put("TH03", 1); a.put("TH04", 1); a.put("TH05", 1);
        a.put("DS01", "NEVER"); a.put("DS02", 5); a.put("DS03", 10); a.put("DS04", 1); a.put("DS05", true);
        a.put("TR01", 20); a.put("TR02", 1); a.put("TR03", 1); a.put("TR04", 5); a.put("TR05", true);
        a.put("LL01", 5); a.put("LL02", 20); a.put("LL03", 5); a.put("LL04", 1); a.put("LL05", 1);
        return a;
    }

    private ScoringResult score(Map<String, Object> answers) throws IOException {
        ScoringConfigurationSnapshot config = snapshot();
        ScoringContext context = new ScoringContext(new TenantId("t"), new OrganizationId("o"), new AssessmentId("a"),
                new AssessmentSnapshotId("s"), config.version(), "3.0.0", "1.0.0");
        List<ScoringInput> inputs = new ArrayList<>();
        answers.forEach((q, a) -> inputs.add(new ScoringInput(q, a)));
        return engine.score(context, inputs, config);
    }

    private static Map<String, Object> with(Object... overrides) {
        Map<String, Object> answers = best();
        for (int i = 0; i < overrides.length; i += 2) {
            answers.put((String) overrides[i], overrides[i + 1]);
        }
        return answers;
    }

    private static Set<String> matched(ScoringResult r) {
        return r.facts().patterns().stream().filter(p -> p.status() == PatternStatus.MATCHED).map(PatternEvaluation::patternId).collect(Collectors.toSet());
    }

    private static Map<String, PenaltyResult> penalties(ScoringResult r) {
        return r.facts().penalties().results().stream().collect(Collectors.toMap(p -> p.definition().penaltyId(), p -> p));
    }

    @Test
    void theBestPossibleAnswersGiveAPerfectScoreAndNoFindings() throws IOException {
        ScoringResult r = score(best());
        assertThat(r.status()).isEqualTo(ScoreStatus.VALID);
        assertThat(r.overall().finalScore().value()).isEqualByComparingTo("100");
        assertThat(r.facts().lisInterpretation().label()).isEqualTo("STRONG");
        assertThat(matched(r)).isEmpty();
        assertThat(penalties(r).values()).noneMatch(p -> p.status() == PenaltyStatus.APPLIED);
        assertThat(r.facts().imbalances()).noneMatch(i -> i.status().name().equals("DETECTED"));
    }

    @Test
    void theWorstPossibleAnswersStayWithinZeroToOneHundredWhateverTheDeductions() throws IOException {
        ScoringResult r = score(worst());
        assertThat(r.status()).isEqualTo(ScoreStatus.VALID);
        assertThat(r.overall().finalScore().value()).isEqualByComparingTo("0");
        assertThat(r.facts().lisInterpretation().label()).isEqualTo("CRITICAL");
        r.overall().dimensions().forEach(d -> {
            assertThat(d.adjustedScore().value()).isBetween(BigDecimal.ZERO, new BigDecimal("100"));
            assertThat(d.adjustedScore().value()).isLessThanOrEqualTo(d.rawScore().value());
        });
        assertThat(matched(r)).hasSize(16);
    }

    /** pattern id, answers that raise it, the penalty it triggers, and the deduction: suggested maximum x severity multiplier. */
    private static List<Object[]> scenarios() {
        return List.of(
                new Object[]{"TH-001", with("TH02", 5, "TH01", 1), "TH-P01", "12.0"},
                new Object[]{"TH-002", with("TH03", 1), "TH-P02", "5.6"},
                new Object[]{"DS-001", with("DS01", "NEVER", "DS04", 1), "DS-P01", "7.0"},
                new Object[]{"DS-003", with("DS02", 5), "DS-P03", "2.4"},
                new Object[]{"DS-005", with("DS05", true), "DS-P05", "8.4"},
                new Object[]{"DS-009", with("DS03", 8), "DS-P09", "7.0"},
                new Object[]{"TR-003", with("TR05", true), "TR-P03", "8.4"},
                new Object[]{"TR-004", with("TR02", 1), "TR-P04", "3.2"},
                new Object[]{"TR-005", with("TR01", 12), "TR-P05", "15.0"},
                new Object[]{"TR-006", with("TR03", 1), "TR-P06", "2.4"},
                new Object[]{"LL-001", with("LL01", 5, "LL03", 5), "LL-P01", "7.0"},
                new Object[]{"LL-004", with("LL01", 5), "LL-P04", "3.2"},
                new Object[]{"LL-006", with("LL04", 1, "LL02", 14), "LL-P06", "12.0"},
                new Object[]{"LL-008", with("LL05", 1), "LL-P08", "2.0"},
                new Object[]{"XD-008", with("DS05", true, "TR05", true), "XD-P08", "7.0"});
    }

    @Test
    void eachEnabledPatternIsRaisedByItsOwnAnswersAndItsPenaltyDeductsTheDocumentedAmount() throws IOException {
        for (Object[] scenario : scenarios()) {
            @SuppressWarnings("unchecked")
            ScoringResult r = score((Map<String, Object>) scenario[1]);
            String pattern = (String) scenario[0];
            String penalty = (String) scenario[2];
            assertThat(matched(r)).as(pattern + " matched").contains(pattern);
            PenaltyResult result = penalties(r).get(penalty);
            assertThat(result.status()).as(penalty + " status").isEqualTo(PenaltyStatus.APPLIED);
            assertThat(result.effectiveDeduction().value()).as(penalty + " deduction").isEqualByComparingTo((String) scenario[3]);
            assertThat(result.effectiveDeduction().value()).isLessThanOrEqualTo(new BigDecimal(SPEC_MAXIMUM.get(penalty)));
        }
    }

    @Test
    void theCrossDimensionalPatternNeedsBothDimensionsLowAndLowersOnlyTheOverallScore() throws IOException {
        Map<String, Object> answers = best();
        answers.putAll(Map.of("TH01", 1, "TH02", 5, "TH03", 1, "TH04", 1, "TH05", 1));
        answers.putAll(Map.of("DS01", "NEVER", "DS02", 5, "DS03", 10, "DS04", 1, "DS05", false));
        ScoringResult r = score(answers);
        assertThat(matched(r)).contains("XD-001");
        PenaltyResult overall = penalties(r).get("XD-P01");
        assertThat(overall.status()).isEqualTo(PenaltyStatus.APPLIED);
        assertThat(overall.effectiveDeduction().value()).isEqualByComparingTo("5.6");
        assertThat(r.overall().overallPenaltyApplied().value()).isEqualByComparingTo("5.6");

        // only one of the two dimensions low: no match
        Map<String, Object> one = best();
        one.putAll(Map.of("TH01", 1, "TH02", 5, "TH03", 1, "TH04", 1, "TH05", 1));
        assertThat(matched(score(one))).doesNotContain("XD-001");
    }

    @Test
    void aTwoSignalPatternDoesNotMatchOnOneSignalAlone() throws IOException {
        assertThat(matched(score(with("TH02", 5)))).doesNotContain("TH-001");
        assertThat(matched(score(with("TH01", 1)))).doesNotContain("TH-001");
        assertThat(matched(score(with("LL04", 1)))).doesNotContain("LL-006");
    }

    @Test
    void overlappingDecisionFindingsDeductOnlyTheLargerOne() throws IOException {
        ScoringResult r = score(with("LL01", 5, "LL03", 5));
        assertThat(matched(r)).contains("LL-001", "LL-004");
        assertThat(penalties(r).get("LL-P01").status()).isEqualTo(PenaltyStatus.APPLIED);
        PenaltyResult smaller = penalties(r).get("LL-P04");
        assertThat(smaller.status()).isEqualTo(PenaltyStatus.SUPPRESSED);
        assertThat(smaller.suppressedAmount().value()).isEqualByComparingTo("3.2");
        assertThat(smaller.winningPenaltyId()).isEqualTo("LL-P01");
    }

    @Test
    void theDeliveryTechnicalGapIsReportedButNeverDeducted() throws IOException {
        // strong delivery next to weak technical resilience: raw 100 against 0 is a gap of 100
        Map<String, Object> answers = best();
        answers.putAll(Map.of("TR01", 20, "TR02", 1, "TR03", 1, "TR04", 5, "TR05", true));
        ScoringResult r = score(answers);
        assertThat(r.facts().imbalances()).anySatisfy(i -> {
            assertThat(i.ruleId()).isEqualTo("DELIVERY_TECHNICAL_GAP");
            assertThat(i.status().name()).isEqualTo("DETECTED");
        });
        assertThat(r.facts().dimensionScores().stream().filter(d -> d.dimension() == ScoringDimension.DELIVERY_STABILITY).findFirst().orElseThrow()
                .rawScore().value()).isEqualByComparingTo("100");
    }

    @Test
    void anIncompleteDimensionGivesNoScoreInsteadOfAnEstimate() throws IOException {
        Map<String, Object> answers = best();
        answers.remove("TH01");
        answers.remove("TH02");
        answers.remove("TH03");
        ScoringResult r = score(answers);
        assertThat(r.status()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        assertThat(r.overall().finalScore()).isNull();
    }

    @Test
    void countsBeyondTheRangeAreClampedNotRejected() throws IOException {
        ScoringResult r = score(with("TR01", 55));
        assertThat(r.status()).isEqualTo(ScoreStatus.VALID);
        assertThat(r.facts().normalizedInputs().stream().filter(n -> n.questionId().equals("TR01")).findFirst().orElseThrow().clamped()).isTrue();
    }
}
