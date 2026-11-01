package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.enums.EvaluationOutcome;
import com.company.leadershipintelligence.domain.enums.PenaltyReason;
import com.company.leadershipintelligence.domain.enums.PenaltyStatus;
import com.company.leadershipintelligence.domain.enums.PenaltyTarget;
import com.company.leadershipintelligence.domain.enums.StackingPolicy;
import com.company.leadershipintelligence.domain.model.PenaltyAggregation;
import com.company.leadershipintelligence.domain.model.PenaltyEvaluation;
import com.company.leadershipintelligence.domain.model.PenaltyResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.company.leadershipintelligence.domain.service.penalties.PenaltyTestSupport.candidate;
import static com.company.leadershipintelligence.domain.service.penalties.PenaltyTestSupport.notCandidate;
import static com.company.leadershipintelligence.domain.service.penalties.PenaltyTestSupport.pen;
import static org.assertj.core.api.Assertions.assertThat;

class PenaltyAggregationServiceTest {

    private final PenaltyAggregationService service = new PenaltyAggregationService();

    private static PenaltyResult result(PenaltyAggregation a, String id) {
        return a.results().stream().filter(r -> r.penaltyId().equals(id)).findFirst().orElseThrow();
    }

    private static PenaltyEvaluation max(String id, String amount, int priority, int order) {
        return candidate(pen().id(id).policy(StackingPolicy.MAX).priority(priority).order(order).build(), amount);
    }

    // ---- STACK ----
    @Test
    void stackSumsEveryDeduction() {
        PenaltyAggregation a = service.aggregate(List.of(candidate(pen().id("A").build(), "5"), candidate(pen().id("B").build(), "3")));
        assertThat(a.total(PenaltyTarget.TEAM_HEALTH).value()).isEqualByComparingTo("8");
        assertThat(a.results()).extracting(PenaltyResult::status).containsOnly(PenaltyStatus.APPLIED);
        assertThat(a.results()).extracting(PenaltyResult::reason).containsOnly(PenaltyReason.APPLIED_IN_FULL);
    }

    // ---- MAX ----
    @Test
    void maxKeepsOnlyTheLargestAndExplainsTheSuppressedAmount() {
        PenaltyAggregation a = service.aggregate(List.of(max("A", "8", 0, 0), max("B", "5", 0, 1)));
        assertThat(a.total(PenaltyTarget.TEAM_HEALTH).value()).isEqualByComparingTo("8");
        assertThat(result(a, "A").status()).isEqualTo(PenaltyStatus.APPLIED);
        PenaltyResult b = result(a, "B");
        assertThat(b.status()).isEqualTo(PenaltyStatus.SUPPRESSED);
        assertThat(b.reason()).isEqualTo(PenaltyReason.SUPPRESSED_MAX);
        assertThat(b.winningPenaltyId()).isEqualTo("A");
        assertThat(b.policy()).isEqualTo(StackingPolicy.MAX);
        assertThat(b.candidateDeduction().value()).isEqualByComparingTo("5");
        assertThat(b.suppressedAmount().value()).isEqualByComparingTo("5");
        assertThat(b.effectiveDeduction().value()).isEqualByComparingTo("0");
    }

    @Test
    void maxTiesBreakByPriorityThenRuleOrderThenId() {
        // equal amounts: lower priority number wins
        assertThat(result(service.aggregate(List.of(max("A", "5", 2, 0), max("B", "5", 1, 5))), "B").status()).isEqualTo(PenaltyStatus.APPLIED);
        // equal priority: lower rule order wins
        assertThat(result(service.aggregate(List.of(max("A", "5", 1, 3), max("B", "5", 1, 2))), "B").status()).isEqualTo(PenaltyStatus.APPLIED);
        // equal priority and order: id order
        PenaltyAggregation byId = service.aggregate(List.of(max("B", "5", 1, 1), max("A", "5", 1, 1)));
        assertThat(result(byId, "A").status()).isEqualTo(PenaltyStatus.APPLIED);
        assertThat(result(byId, "B").winningPenaltyId()).isEqualTo("A");
    }

    // ---- MAX_PER_CATEGORY ----
    @Test
    void maxPerCategoryKeepsOneLargestPerCategory() {
        List<PenaltyEvaluation> in = List.of(
                candidate(pen().id("A").policy(StackingPolicy.MAX_PER_CATEGORY).category("X").build(), "6"),
                candidate(pen().id("B").policy(StackingPolicy.MAX_PER_CATEGORY).category("X").build(), "4"),
                candidate(pen().id("C").policy(StackingPolicy.MAX_PER_CATEGORY).category("Y").build(), "3"));
        PenaltyAggregation a = service.aggregate(in);
        assertThat(a.total(PenaltyTarget.TEAM_HEALTH).value()).isEqualByComparingTo("9");
        assertThat(result(a, "B").reason()).isEqualTo(PenaltyReason.SUPPRESSED_MAX_PER_CATEGORY);
        assertThat(result(a, "B").winningPenaltyId()).isEqualTo("A");
        assertThat(result(a, "C").status()).isEqualTo(PenaltyStatus.APPLIED);
    }

    // ---- PRIORITY ----
    @Test
    void priorityPicksTheBestRankEvenWhenSmaller() {
        PenaltyAggregation a = service.aggregate(List.of(
                candidate(pen().id("BIG").policy(StackingPolicy.PRIORITY).priority(5).build(), "9"),
                candidate(pen().id("SMALL").policy(StackingPolicy.PRIORITY).priority(1).build(), "2")));
        assertThat(a.total(PenaltyTarget.TEAM_HEALTH).value()).isEqualByComparingTo("2");
        assertThat(result(a, "BIG").reason()).isEqualTo(PenaltyReason.SUPPRESSED_PRIORITY);
        assertThat(result(a, "BIG").winningPenaltyId()).isEqualTo("SMALL");
        assertThat(result(a, "BIG").suppressedAmount().value()).isEqualByComparingTo("9");
    }

    // ---- CAPPED_STACK ----
    @Test
    void cappedStackFillsThePoolInTieBreakOrderAndTrimsTheRest() {
        PenaltyAggregation a = service.aggregate(List.of(
                candidate(pen().id("C").policy(StackingPolicy.CAPPED_STACK).cap("10").priority(3).build(), "4"),
                candidate(pen().id("A").policy(StackingPolicy.CAPPED_STACK).cap("10").priority(1).build(), "6"),
                candidate(pen().id("B").policy(StackingPolicy.CAPPED_STACK).cap("10").priority(2).build(), "5")));
        assertThat(a.total(PenaltyTarget.TEAM_HEALTH).value()).isEqualByComparingTo("10");
        assertThat(result(a, "A").effectiveDeduction().value()).isEqualByComparingTo("6");
        assertThat(result(a, "A").reason()).isEqualTo(PenaltyReason.APPLIED_IN_FULL);
        PenaltyResult b = result(a, "B");
        assertThat(b.status()).isEqualTo(PenaltyStatus.APPLIED);
        assertThat(b.reason()).isEqualTo(PenaltyReason.APPLIED_PARTIALLY_CAPPED);
        assertThat(b.effectiveDeduction().value()).isEqualByComparingTo("4");
        assertThat(b.suppressedAmount().value()).isEqualByComparingTo("1");
        PenaltyResult c = result(a, "C");
        assertThat(c.status()).isEqualTo(PenaltyStatus.SUPPRESSED);
        assertThat(c.reason()).isEqualTo(PenaltyReason.SUPPRESSED_CUMULATIVE_CAP);
        assertThat(c.suppressedAmount().value()).isEqualByComparingTo("4");
        assertThat(c.winningPenaltyId()).isNull();
    }

    @Test
    void cappedStackBelowTheCapIsUntouched() {
        PenaltyAggregation a = service.aggregate(List.of(
                candidate(pen().id("A").policy(StackingPolicy.CAPPED_STACK).cap("10").build(), "3"),
                candidate(pen().id("B").policy(StackingPolicy.CAPPED_STACK).cap("10").build(), "4")));
        assertThat(a.total(PenaltyTarget.TEAM_HEALTH).value()).isEqualByComparingTo("7");
        assertThat(a.results()).extracting(PenaltyResult::reason).containsOnly(PenaltyReason.APPLIED_IN_FULL);
    }

    // ---- EXCLUSIVE_GROUP ----
    @Test
    void exclusiveGroupPicksByPriorityNotBySize() {
        PenaltyAggregation a = service.aggregate(List.of(
                candidate(pen().id("SMALL").policy(StackingPolicy.EXCLUSIVE_GROUP).group("g").priority(1).build(), "3"),
                candidate(pen().id("BIG").policy(StackingPolicy.EXCLUSIVE_GROUP).group("g").priority(2).build(), "9"),
                candidate(pen().id("OTHER").policy(StackingPolicy.EXCLUSIVE_GROUP).group("h").priority(2).build(), "4")));
        assertThat(a.total(PenaltyTarget.TEAM_HEALTH).value()).isEqualByComparingTo("7");
        PenaltyResult big = result(a, "BIG");
        assertThat(big.status()).isEqualTo(PenaltyStatus.SUPPRESSED);
        assertThat(big.reason()).isEqualTo(PenaltyReason.SUPPRESSED_EXCLUSIVE_GROUP);
        assertThat(big.winningPenaltyId()).isEqualTo("SMALL");
        assertThat(result(a, "OTHER").status()).isEqualTo(PenaltyStatus.APPLIED);
    }

    // ---- mixing and targets ----
    @Test
    void policiesCombineWithinATargetAndTargetsStayIndependent() {
        PenaltyAggregation a = service.aggregate(List.of(
                candidate(pen().id("S").build(), "2"),
                candidate(pen().id("C1").policy(StackingPolicy.CAPPED_STACK).cap("4").priority(1).build(), "3"),
                candidate(pen().id("C2").policy(StackingPolicy.CAPPED_STACK).cap("4").priority(2).build(), "3"),
                candidate(pen().id("M1").policy(StackingPolicy.MAX).priority(1).build(), "5"),
                candidate(pen().id("M2").policy(StackingPolicy.MAX).priority(2).build(), "1"),
                candidate(pen().id("O").target(PenaltyTarget.OVERALL).build(), "6"),
                candidate(pen().id("D").target(PenaltyTarget.TECHNICAL_RISK).build(), "7")));
        // team health: STACK 2 + capped 4 (3 + 1) + max 5 = 11
        assertThat(a.total(PenaltyTarget.TEAM_HEALTH).value()).isEqualByComparingTo("11");
        assertThat(a.total(PenaltyTarget.OVERALL).value()).isEqualByComparingTo("6");
        assertThat(a.total(PenaltyTarget.TECHNICAL_RISK).value()).isEqualByComparingTo("7");
        assertThat(a.total(PenaltyTarget.LEADERSHIP_LOAD).value()).isEqualByComparingTo("0");
    }

    @Test
    void policiesOnlyActAmongPenaltiesThatDeclareThem() {
        // a STACK penalty and a MAX penalty never suppress each other
        PenaltyAggregation a = service.aggregate(List.of(candidate(pen().id("S").build(), "2"), max("M", "9", 0, 0)));
        assertThat(a.total(PenaltyTarget.TEAM_HEALTH).value()).isEqualByComparingTo("11");
        assertThat(a.results()).extracting(PenaltyResult::status).containsOnly(PenaltyStatus.APPLIED);
    }

    // ---- terminal states and invariants ----
    @Test
    void everyPenaltyEndsInExactlyOneTerminalStateAndAmountsReconcile() {
        List<PenaltyEvaluation> in = List.of(
                candidate(pen().id("APPLIED").build(), "4"),
                max("W", "9", 0, 0), max("L", "2", 1, 1),
                notCandidate(pen().id("INELIG").build(), EvaluationOutcome.INELIGIBLE, PenaltyReason.PATTERN_INELIGIBLE),
                notCandidate(pen().id("NOTTRIG").build(), EvaluationOutcome.NOT_TRIGGERED, PenaltyReason.PATTERN_NOT_MATCHED));
        PenaltyAggregation a = service.aggregate(in);
        assertThat(a.results()).hasSize(in.size());
        assertThat(a.results().stream().map(PenaltyResult::penaltyId).collect(Collectors.toSet())).hasSize(in.size());
        assertThat(result(a, "INELIG").status()).isEqualTo(PenaltyStatus.INELIGIBLE);
        assertThat(result(a, "NOTTRIG").status()).isEqualTo(PenaltyStatus.NOT_TRIGGERED);
        assertThat(result(a, "L").status()).isEqualTo(PenaltyStatus.SUPPRESSED);
        for (PenaltyResult r : a.results()) {
            assertThat(r.reason()).isNotNull();
            assertThat(r.effectiveDeduction().value().add(r.suppressedAmount().value())).isEqualByComparingTo(r.candidateDeduction().value());
            if (r.status() == PenaltyStatus.INELIGIBLE || r.status() == PenaltyStatus.NOT_TRIGGERED) {
                assertThat(r.candidateDeduction().value()).isEqualByComparingTo("0");
            }
        }
    }

    @Test
    void noDeductionIsAppliedTwiceAndPenaltiesNeverIncreaseTheCandidates() {
        List<PenaltyEvaluation> in = List.of(
                candidate(pen().id("A").policy(StackingPolicy.CAPPED_STACK).cap("5").build(), "4"),
                candidate(pen().id("B").policy(StackingPolicy.CAPPED_STACK).cap("5").build(), "4"),
                max("C", "6", 0, 0), max("D", "6", 1, 1));
        PenaltyAggregation a = service.aggregate(in);
        BigDecimal candidates = a.results().stream().map(r -> r.candidateDeduction().value()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal effective = a.results().stream().map(r -> r.effectiveDeduction().value()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totals = a.effectiveByTarget().values().stream().map(v -> v.value()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(effective).isLessThanOrEqualTo(candidates);
        assertThat(totals).isEqualByComparingTo(effective);
        assertThat(a.results().stream().map(PenaltyResult::penaltyId)).doesNotHaveDuplicates();
    }

    @Test
    void resultsAreIndependentOfInputOrder() {
        List<PenaltyEvaluation> in = new ArrayList<>(List.of(
                max("A", "5", 1, 1), max("B", "5", 1, 1), max("C", "7", 3, 2),
                candidate(pen().id("G1").policy(StackingPolicy.EXCLUSIVE_GROUP).group("g").priority(2).build(), "4"),
                candidate(pen().id("G2").policy(StackingPolicy.EXCLUSIVE_GROUP).group("g").priority(2).order(1).build(), "6"),
                candidate(pen().id("K1").policy(StackingPolicy.CAPPED_STACK).cap("5").priority(1).build(), "3"),
                candidate(pen().id("K2").policy(StackingPolicy.CAPPED_STACK).cap("5").priority(1).order(1).build(), "3")));
        Map<String, String> baseline = summary(service.aggregate(in));
        for (int seed = 0; seed < 6; seed++) {
            List<PenaltyEvaluation> shuffled = new ArrayList<>(in);
            Collections.shuffle(shuffled, new java.util.Random(seed));
            assertThat(summary(service.aggregate(shuffled))).isEqualTo(baseline);
        }
    }

    private static Map<String, String> summary(PenaltyAggregation a) {
        return a.results().stream().collect(Collectors.toMap(PenaltyResult::penaltyId,
                r -> r.status() + "/" + r.reason() + "/" + r.effectiveDeduction() + "/" + r.winningPenaltyId()));
    }

    @Test
    void resultOrderFollowsTheInputOrderAndIsRepeatable() {
        List<PenaltyEvaluation> in = List.of(max("Z", "5", 0, 0), max("A", "3", 1, 1));
        assertThat(service.aggregate(in).results()).extracting(PenaltyResult::penaltyId).containsExactly("Z", "A");
        assertThat(service.aggregate(in)).isEqualTo(service.aggregate(in));
        assertThat(service.aggregate(in).tieBreakVersion()).isEqualTo("1");
    }

    @Test
    void emptyInputGivesZeroTotals() {
        PenaltyAggregation a = service.aggregate(List.of());
        for (PenaltyTarget target : PenaltyTarget.values()) {
            assertThat(a.total(target).value()).isEqualByComparingTo("0");
        }
    }
}
