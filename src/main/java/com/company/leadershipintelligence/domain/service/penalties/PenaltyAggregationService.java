package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.enums.EvaluationOutcome;
import com.company.leadershipintelligence.domain.enums.PenaltyReason;
import com.company.leadershipintelligence.domain.enums.PenaltyStatus;
import com.company.leadershipintelligence.domain.enums.PenaltyTarget;
import com.company.leadershipintelligence.domain.enums.StackingPolicy;
import com.company.leadershipintelligence.domain.model.PenaltyAggregation;
import com.company.leadershipintelligence.domain.model.PenaltyEvaluation;
import com.company.leadershipintelligence.domain.model.PenaltyResult;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Applies stacking policies and records suppression (specv3.md Section 16). Candidates are processed per
 * target. Each penalty's policy acts only among penalties that declare the same policy, in this fixed order:
 * EXCLUSIVE_GROUP (per group), MAX_PER_CATEGORY (per category), MAX, PRIORITY, then CAPPED_STACK (one pool per
 * target, filled in tie-break order up to the common cap). STACK penalties are always kept. The sum of the
 * survivors is the effective total for the target.
 *
 * <p>Tie-break (version "1"), never dependent on collection order: lower {@code priority} number first, then
 * {@code ruleOrder}, then penalty id, then version. "Largest" (MAX, MAX_PER_CATEGORY) compares the candidate
 * deduction first and falls back to that order. Every penalty ends in exactly one terminal status.
 */
public class PenaltyAggregationService {

    public static final String TIE_BREAK_VERSION = "1";

    private static final Comparator<PenaltyEvaluation> TIE_BREAK = Comparator
            .comparingInt((PenaltyEvaluation e) -> e.definition().priority())
            .thenComparingInt(e -> e.definition().ruleOrder())
            .thenComparing(e -> e.definition().penaltyId())
            .thenComparingInt(e -> e.definition().version());

    private static final Comparator<PenaltyEvaluation> LARGEST_FIRST = Comparator
            .comparing((PenaltyEvaluation e) -> e.candidateDeduction().value()).reversed()
            .thenComparing(TIE_BREAK);

    /** Mutable working state for one candidate. */
    private static final class Entry {
        final PenaltyEvaluation evaluation;
        BigDecimal effective;
        PenaltyReason reason = PenaltyReason.APPLIED_IN_FULL;
        String winner;

        Entry(PenaltyEvaluation evaluation) {
            this.evaluation = evaluation;
            this.effective = evaluation.candidateDeduction().value();
        }

        boolean alive() {
            return effective.signum() > 0;
        }

        void suppress(PenaltyReason why, PenaltyEvaluation by) {
            effective = BigDecimal.ZERO;
            reason = why;
            winner = by == null ? null : by.penaltyId();
        }
    }

    public PenaltyAggregation aggregate(List<PenaltyEvaluation> evaluations) {
        Map<PenaltyEvaluation, Entry> entries = new IdentityHashMap<>();
        for (PenaltyEvaluation evaluation : evaluations) {
            if (evaluation.outcome() == EvaluationOutcome.CANDIDATE) {
                entries.put(evaluation, new Entry(evaluation));
            }
        }
        for (PenaltyTarget target : PenaltyTarget.values()) {
            List<Entry> inTarget = entries.values().stream()
                    .filter(e -> e.evaluation.target() == target)
                    .sorted((a, b) -> TIE_BREAK.compare(a.evaluation, b.evaluation))
                    .toList();
            exclusiveGroups(inTarget);
            maxPerCategory(inTarget);
            single(inTarget, StackingPolicy.MAX, LARGEST_FIRST, PenaltyReason.SUPPRESSED_MAX);
            single(inTarget, StackingPolicy.PRIORITY, TIE_BREAK, PenaltyReason.SUPPRESSED_PRIORITY);
            cappedStack(inTarget);
        }

        List<PenaltyResult> results = new ArrayList<>();
        Map<PenaltyTarget, BigDecimal> totals = new EnumMap<>(PenaltyTarget.class);
        for (PenaltyEvaluation evaluation : evaluations) {
            Entry entry = entries.get(evaluation);
            results.add(entry == null ? passThrough(evaluation) : candidateResult(entry));
            if (entry != null) {
                totals.merge(evaluation.target(), entry.effective, BigDecimal::add);
            }
        }
        Map<PenaltyTarget, PenaltyAmount> effective = new EnumMap<>(PenaltyTarget.class);
        totals.forEach((target, amount) -> effective.put(target, new PenaltyAmount(amount)));
        return new PenaltyAggregation(results, effective, TIE_BREAK_VERSION);
    }

    private static void exclusiveGroups(List<Entry> inTarget) {
        Map<String, List<Entry>> groups = new TreeMap<>();
        for (Entry entry : inTarget) {
            if (entry.alive() && entry.evaluation.definition().stackingPolicy() == StackingPolicy.EXCLUSIVE_GROUP) {
                groups.computeIfAbsent(entry.evaluation.definition().exclusiveGroup(), k -> new ArrayList<>()).add(entry);
            }
        }
        groups.values().forEach(group -> keepOnly(group, TIE_BREAK, PenaltyReason.SUPPRESSED_EXCLUSIVE_GROUP));
    }

    private static void maxPerCategory(List<Entry> inTarget) {
        Map<String, List<Entry>> categories = new TreeMap<>();
        for (Entry entry : inTarget) {
            if (entry.alive() && entry.evaluation.definition().stackingPolicy() == StackingPolicy.MAX_PER_CATEGORY) {
                categories.computeIfAbsent(entry.evaluation.definition().category(), k -> new ArrayList<>()).add(entry);
            }
        }
        categories.values().forEach(group -> keepOnly(group, LARGEST_FIRST, PenaltyReason.SUPPRESSED_MAX_PER_CATEGORY));
    }

    private static void single(List<Entry> inTarget, StackingPolicy policy, Comparator<PenaltyEvaluation> order, PenaltyReason why) {
        List<Entry> group = inTarget.stream()
                .filter(e -> e.alive() && e.evaluation.definition().stackingPolicy() == policy).toList();
        keepOnly(group, order, why);
    }

    private static void keepOnly(List<Entry> group, Comparator<PenaltyEvaluation> order, PenaltyReason why) {
        if (group.size() < 2) {
            return;
        }
        List<Entry> ranked = new ArrayList<>(group);
        ranked.sort((a, b) -> order.compare(a.evaluation, b.evaluation));
        Entry winner = ranked.get(0);
        for (Entry loser : ranked.subList(1, ranked.size())) {
            loser.suppress(why, winner.evaluation);
        }
    }

    private static void cappedStack(List<Entry> inTarget) {
        List<Entry> pool = inTarget.stream()
                .filter(e -> e.alive() && e.evaluation.definition().stackingPolicy() == StackingPolicy.CAPPED_STACK).toList();
        if (pool.isEmpty()) {
            return;
        }
        BigDecimal remaining = pool.stream()
                .map(e -> e.evaluation.definition().maximumCumulativeDeduction().value())
                .min(Comparator.naturalOrder()).orElseThrow();
        for (Entry entry : pool) {
            BigDecimal candidate = entry.evaluation.candidateDeduction().value();
            BigDecimal allowed = candidate.min(remaining);
            remaining = remaining.subtract(allowed);
            if (allowed.compareTo(candidate) == 0) {
                continue;
            }
            entry.effective = allowed;
            entry.reason = allowed.signum() > 0 ? PenaltyReason.APPLIED_PARTIALLY_CAPPED : PenaltyReason.SUPPRESSED_CUMULATIVE_CAP;
        }
    }

    private static PenaltyResult candidateResult(Entry entry) {
        PenaltyEvaluation ev = entry.evaluation;
        BigDecimal candidate = ev.candidateDeduction().value();
        PenaltyStatus status = entry.effective.signum() > 0 ? PenaltyStatus.APPLIED : PenaltyStatus.SUPPRESSED;
        return new PenaltyResult(ev.definition(), status, entry.reason, ev.candidateDeduction(),
                new PenaltyAmount(entry.effective), new PenaltyAmount(candidate.subtract(entry.effective)),
                entry.winner, ev.definition().stackingPolicy(), ev.scoreBase(), ev.scoreBaseValue(), ev.evidenceRefs());
    }

    private static PenaltyResult passThrough(PenaltyEvaluation ev) {
        PenaltyStatus status = ev.outcome() == EvaluationOutcome.INELIGIBLE ? PenaltyStatus.INELIGIBLE : PenaltyStatus.NOT_TRIGGERED;
        return new PenaltyResult(ev.definition(), status, ev.reason(), PenaltyAmount.ZERO, PenaltyAmount.ZERO,
                PenaltyAmount.ZERO, null, ev.definition().stackingPolicy(), ev.scoreBase(), ev.scoreBaseValue(), ev.evidenceRefs());
    }
}
