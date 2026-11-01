package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.persistence.repository.HistoryStore;
import com.company.leadershipintelligence.repository.persistence.repository.HistoryStore.AssessmentRow;
import com.company.leadershipintelligence.repository.persistence.repository.HistoryStore.DimensionScoreRow;
import com.company.leadershipintelligence.repository.persistence.repository.HistoryStore.RunSummaryRow;
import com.company.leadershipintelligence.service.command.OrganizationScope;
import com.company.leadershipintelligence.service.query.Cursor;
import com.company.leadershipintelligence.service.query.HistoryViews.AssessmentSummary;
import com.company.leadershipintelligence.service.query.HistoryViews.Dashboard;
import com.company.leadershipintelligence.service.query.HistoryViews.DimensionPoint;
import com.company.leadershipintelligence.service.query.HistoryViews.PatternHit;
import com.company.leadershipintelligence.service.query.HistoryViews.PenaltyTotals;
import com.company.leadershipintelligence.service.query.HistoryViews.TrendPoint;
import com.company.leadershipintelligence.service.query.HistoryViews.Trends;
import com.company.leadershipintelligence.service.query.HistoryViews.DimensionChange;
import com.company.leadershipintelligence.service.query.HistoryViews.Page;
import com.company.leadershipintelligence.service.query.HistoryViews.RunComparison;
import com.company.leadershipintelligence.service.query.HistoryViews.RunSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read use cases behind the frontend (decisions D26-D28): assessment lists, run histories with previous score and
 * comparability, comparison of two runs, and the organization dashboard. Nothing here recalculates a score: every
 * figure is read from a stored run, and a change is reported only between comparable runs (decision D27).
 */
@Service
@Transactional(readOnly = true)
public class ScoringHistoryService {

    private static final int DASHBOARD_RUNS = 10;
    private static final int DASHBOARD_SCAN = 200;

    private final HistoryStore store;

    public ScoringHistoryService(HistoryStore store) {
        this.store = store;
    }

    public Page<AssessmentSummary> assessments(TenantId tenant, OrganizationScope scope, String organizationId, String status,
                                               String cursor, int limit) {
        if (organizationId != null) {
            scope.require(organizationId);
        }
        Cursor.AssessmentPosition after = cursor == null ? null : Cursor.decodeAssessment(cursor);
        List<AssessmentRow> rows = store.assessments(tenant.value(), organizations(scope), organizationId, status,
                after == null ? null : after.createdAt(), after == null ? null : after.id(), limit + 1);
        boolean more = rows.size() > limit;
        List<AssessmentRow> page = more ? rows.subList(0, limit) : rows;
        List<AssessmentSummary> items = page.stream().map(ScoringHistoryService::summary).toList();
        AssessmentRow last = page.isEmpty() ? null : page.get(page.size() - 1);
        return new Page<>(items, more ? Cursor.encodeAssessment(last.createdAt(), last.id()) : null);
    }

    public AssessmentSummary assessment(TenantId tenant, OrganizationScope scope, String assessmentId) {
        AssessmentRow row = store.assessment(tenant.value(), assessmentId).orElseThrow(() -> notFound(assessmentId));
        if (!scope.everything() && !scope.organizationIds().contains(row.organizationId())) {
            throw notFound(assessmentId);
        }
        return summary(row);
    }

    public Page<RunSummary> runs(TenantId tenant, OrganizationScope scope, String organizationId, String assessmentId, Instant from,
                                 Instant to, String cursor, int limit) {
        if (organizationId != null) {
            scope.require(organizationId);
        }
        Long before = cursor == null ? null : Cursor.decodeSeq(cursor);
        List<RunSummaryRow> rows = store.summaries(tenant.value(), organizations(scope), organizationId, assessmentId, from, to, before,
                null, limit + 1);
        boolean more = rows.size() > limit;
        List<RunSummaryRow> page = more ? rows.subList(0, limit) : rows;
        return new Page<>(page.stream().map(r -> summary(r)).toList(), more ? Cursor.encodeSeq(page.get(page.size() - 1).seq()) : null);
    }

    public RunComparison compare(TenantId tenant, OrganizationScope scope, UUID baseRunId, UUID runId) {
        RunSummaryRow base = one(tenant, scope, baseRunId);
        RunSummaryRow run = one(tenant, scope, runId);
        List<String> reasons = new ArrayList<>();
        if (!base.organizationId().equals(run.organizationId())) {
            reasons.add("ORGANIZATION_DIFFERS");
        }
        if (!base.templateId().equals(run.templateId())) {
            reasons.add("TEMPLATE_DIFFERS");
        } else if (base.templateVersion() != run.templateVersion()) {
            reasons.add("TEMPLATE_VERSION_DIFFERS");
        }
        if (!base.configurationHash().equals(run.configurationHash())) {
            reasons.add("CONFIGURATION_DIFFERS");
        }
        if (!base.algorithmVersion().equals(run.algorithmVersion())) {
            reasons.add("ALGORITHM_VERSION_DIFFERS");
        }
        boolean comparable = reasons.isEmpty();

        Map<String, BigDecimal> baseDims = dimensions(tenant, base.id());
        Map<String, BigDecimal> runDims = dimensions(tenant, run.id());
        List<DimensionChange> changes = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>(baseDims.keySet());
        names.addAll(runDims.keySet());
        for (String dimension : names) {
            BigDecimal b = baseDims.get(dimension);
            BigDecimal r = runDims.get(dimension);
            changes.add(new DimensionChange(dimension, b, r, comparable && b != null && r != null ? r.subtract(b) : null));
        }
        Set<String> basePatterns = store.matchedPatterns(tenant.value(), base.id());
        Set<String> runPatterns = store.matchedPatterns(tenant.value(), run.id());
        List<String> appeared = runPatterns.stream().filter(p -> !basePatterns.contains(p)).toList();
        List<String> resolved = basePatterns.stream().filter(p -> !runPatterns.contains(p)).toList();
        List<String> persistent = runPatterns.stream().filter(basePatterns::contains).toList();
        return new RunComparison(summary(base), summary(run), comparable, List.copyOf(reasons),
                comparable ? run.score().subtract(base.score()) : null, changes, appeared, resolved, persistent);
    }

    /** Oldest first; the newest {@code limit} valid runs of the organization (optionally of one template). */
    public Trends trends(TenantId tenant, OrganizationScope scope, String organizationId, String templateId, Instant from, Instant to,
                         int limit) {
        scope.require(organizationId);
        List<RunSummaryRow> rows = new ArrayList<>(store.summaries(tenant.value(), null, organizationId, null, templateId, from, to, null,
                null, limit));
        java.util.Collections.reverse(rows);
        List<UUID> ids = rows.stream().map(RunSummaryRow::id).toList();
        Map<UUID, List<DimensionPoint>> dimensions = new LinkedHashMap<>();
        store.trendDimensions(tenant.value(), ids).forEach(d -> dimensions.computeIfAbsent(d.runId(), k -> new ArrayList<>())
                .add(new DimensionPoint(d.dimension(), d.rawScore(), d.adjustedScore())));
        Map<UUID, List<PatternHit>> patterns = new LinkedHashMap<>();
        store.trendPatterns(tenant.value(), ids).forEach(p -> patterns.computeIfAbsent(p.runId(), k -> new ArrayList<>())
                .add(new PatternHit(p.patternId(), p.severity(), p.category())));
        Map<UUID, Map<String, HistoryStore.PenaltyTotalsRow>> penalties = new LinkedHashMap<>();
        store.trendPenalties(tenant.value(), ids).forEach(p -> penalties.computeIfAbsent(p.runId(), k -> new LinkedHashMap<>()).put(p.status(), p));
        List<TrendPoint> points = new ArrayList<>();
        for (RunSummaryRow row : rows) {
            List<PatternHit> hits = patterns.getOrDefault(row.id(), List.of());
            Map<String, Integer> bySeverity = new LinkedHashMap<>();
            for (String severity : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW")) {
                bySeverity.put(severity, (int) hits.stream().filter(h -> h.severity().equals(severity)).count());
            }
            points.add(new TrendPoint(summary(row), dimensions.getOrDefault(row.id(), List.of()), hits, bySeverity,
                    penaltyTotals(penalties.getOrDefault(row.id(), Map.of()))));
        }
        return new Trends(organizationId, templateId, List.copyOf(points));
    }

    private static PenaltyTotals penaltyTotals(Map<String, HistoryStore.PenaltyTotalsRow> byStatus) {
        HistoryStore.PenaltyTotalsRow applied = byStatus.get("APPLIED");
        HistoryStore.PenaltyTotalsRow suppressed = byStatus.get("SUPPRESSED");
        BigDecimal deduction = BigDecimal.ZERO;
        BigDecimal suppressedAmount = BigDecimal.ZERO;
        for (HistoryStore.PenaltyTotalsRow row : byStatus.values()) {
            deduction = deduction.add(row.effectiveDeduction());
            suppressedAmount = suppressedAmount.add(row.suppressedAmount());
        }
        return new PenaltyTotals(applied == null ? 0 : applied.count(), suppressed == null ? 0 : suppressed.count(),
                count(byStatus, "INELIGIBLE"), count(byStatus, "NOT_TRIGGERED"), deduction, suppressedAmount);
    }

    private static int count(Map<String, HistoryStore.PenaltyTotalsRow> byStatus, String status) {
        HistoryStore.PenaltyTotalsRow row = byStatus.get(status);
        return row == null ? 0 : row.count();
    }

    public Dashboard dashboard(TenantId tenant, OrganizationScope scope, String organizationId) {
        scope.require(organizationId);
        List<RunSummaryRow> rows = store.summaries(tenant.value(), null, organizationId, null, null, null, null, null, DASHBOARD_SCAN);
        Map<String, RunSummary> latest = new LinkedHashMap<>();
        for (RunSummaryRow row : rows) {
            latest.putIfAbsent(row.templateId(), summary(row));
        }
        List<RunSummary> recent = rows.stream().limit(DASHBOARD_RUNS).map(r -> summary(r)).toList();
        return new Dashboard(organizationId, List.copyOf(latest.values()), recent, store.assessmentCounts(tenant.value(), organizationId));
    }

    // ---------------- helpers ----------------

    private RunSummaryRow one(TenantId tenant, OrganizationScope scope, UUID runId) {
        RunSummaryRow row = store.summaries(tenant.value(), null, null, null, null, null, null, runId, 1).stream().findFirst()
                .orElseThrow(() -> new ScoringException(ScoringErrorCode.SCORING_RUN_NOT_FOUND,
                        "the scoring run was not found or has no valid score", Map.of("scoringRunId", runId.toString())));
        if (!scope.everything() && !scope.organizationIds().contains(row.organizationId())) {
            throw new ScoringException(ScoringErrorCode.SCORING_RUN_NOT_FOUND, "the scoring run was not found or has no valid score",
                    Map.of("scoringRunId", runId.toString()));
        }
        return row;
    }

    private Map<String, BigDecimal> dimensions(TenantId tenant, UUID runId) {
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        for (DimensionScoreRow row : store.dimensionScores(tenant.value(), runId)) {
            scores.put(row.dimension(), row.adjustedScore());
        }
        return scores;
    }

    private static java.util.Collection<String> organizations(OrganizationScope scope) {
        return scope.everything() ? null : scope.organizationIds();
    }

    private static ScoringException notFound(String assessmentId) {
        return new ScoringException(ScoringErrorCode.ASSESSMENT_NOT_FOUND, "the assessment was not found", Map.of("assessmentId", assessmentId));
    }

    private static AssessmentSummary summary(AssessmentRow r) {
        return new AssessmentSummary(r.id(), r.organizationId(), r.templateId(), r.templateVersion(), r.status(), r.completedAt(),
                r.createdAt(), r.latestRunId(), r.latestScore(), r.latestScoreStatus(), r.latestInterpretation(), r.latestCalculatedAt());
    }

    /** Decision D27: comparable = same configuration hash, algorithm version and template version as the previous valid run. */
    static RunSummary summary(RunSummaryRow r) {
        List<String> reasons = new ArrayList<>();
        boolean comparable = false;
        BigDecimal change = null;
        if (r.previousRunId() != null) {
            if (!r.configurationHash().equals(r.previousConfigurationHash())) {
                reasons.add("CONFIGURATION_DIFFERS");
            }
            if (!r.algorithmVersion().equals(r.previousAlgorithmVersion())) {
                reasons.add("ALGORITHM_VERSION_DIFFERS");
            }
            if (r.previousTemplateVersion() == null || r.templateVersion() != r.previousTemplateVersion()) {
                reasons.add("TEMPLATE_VERSION_DIFFERS");
            }
            comparable = reasons.isEmpty();
            if (comparable) {
                change = r.score().subtract(r.previousScore());
            }
        }
        return new RunSummary(r.id(), r.assessmentId(), r.organizationId(), r.templateId(), r.templateVersion(), r.source(), r.score(),
                r.interpretation(), r.configurationVersion(), r.configurationHash(), r.algorithmVersion(), r.engineVersion(),
                r.calculatedAt(), r.previousRunId(), r.previousScore(), change, comparable, List.copyOf(reasons));
    }
}
