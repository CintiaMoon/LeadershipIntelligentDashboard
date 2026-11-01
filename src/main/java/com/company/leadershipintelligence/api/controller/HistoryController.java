package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.response.HistoryResponses.AssessmentResponse;
import com.company.leadershipintelligence.api.response.HistoryResponses.ComparisonResponse;
import com.company.leadershipintelligence.api.response.HistoryResponses.DashboardResponse;
import com.company.leadershipintelligence.api.response.HistoryResponses.DimensionChangeResponse;
import com.company.leadershipintelligence.api.response.HistoryResponses.LatestRun;
import com.company.leadershipintelligence.api.response.HistoryResponses.PageResponse;
import com.company.leadershipintelligence.api.response.HistoryResponses.PatternChanges;
import com.company.leadershipintelligence.api.response.HistoryResponses.RecommendationsResponse;
import com.company.leadershipintelligence.api.response.HistoryResponses.RunSummaryResponse;
import com.company.leadershipintelligence.api.response.HistoryResponses.TrendPointResponse;
import com.company.leadershipintelligence.api.response.HistoryResponses.TrendsResponse;
import com.company.leadershipintelligence.api.response.Notices;
import com.company.leadershipintelligence.api.security.CurrentUser;
import com.company.leadershipintelligence.service.CatalogService;
import com.company.leadershipintelligence.service.RecommendationService;
import com.company.leadershipintelligence.service.query.CatalogViews.RunCatalog;
import com.company.leadershipintelligence.service.ScoringHistoryService;
import com.company.leadershipintelligence.service.query.HistoryViews.AssessmentSummary;
import com.company.leadershipintelligence.service.query.HistoryViews.Page;
import com.company.leadershipintelligence.service.query.HistoryViews.RunSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Read endpoints the frontend needs (decisions D26-D28): they only read stored runs, never score, and every query is
 * limited to the caller's tenant and organizations.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "History")
public class HistoryController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final Set<String> STATUSES = Set.of("DRAFT", "SUBMITTED", "COMPLETED", "CANCELLED", "SCORING", "SCORED", "FAILED");

    private final ScoringHistoryService history;
    private final RecommendationService recommendations;
    private final CatalogService catalog;

    public HistoryController(ScoringHistoryService history, RecommendationService recommendations, CatalogService catalog) {
        this.history = history;
        this.recommendations = recommendations;
        this.catalog = catalog;
    }

    @Operation(summary = "Assessments of the caller's organizations with their latest result")
    @GetMapping("/assessments")
    public PageResponse<AssessmentResponse> assessments(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestParam(required = false) String organizationId,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String cursor,
                                                        @RequestParam(required = false) Integer limit) {
        CurrentUser user = CurrentUser.from(jwt);
        if (status != null && !STATUSES.contains(status)) {
            throw new ApiRequestException("status is not a known assessment status");
        }
        Page<AssessmentSummary> page = guarded(() -> history.assessments(user.tenantId(), user.scope(), organizationId, status, cursor, limit(limit)));
        return new PageResponse<>(page.items().stream().map(HistoryController::assessment).toList(), page.nextCursor());
    }

    @Operation(summary = "One assessment with its latest result")
    @GetMapping("/assessments/{assessmentId}")
    public AssessmentResponse assessment(@AuthenticationPrincipal Jwt jwt, @PathVariable String assessmentId) {
        CurrentUser user = CurrentUser.from(jwt);
        return assessment(history.assessment(user.tenantId(), user.scope(), assessmentId));
    }

    @Operation(summary = "Score history with the previous score and whether the change is comparable")
    @GetMapping("/scoring-runs")
    public PageResponse<RunSummaryResponse> runs(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String organizationId,
                                                 @RequestParam(required = false) String assessmentId,
                                                 @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                 @RequestParam(required = false) String cursor,
                                                 @RequestParam(required = false) Integer limit) {
        CurrentUser user = CurrentUser.from(jwt);
        Instant start = instant(from, "from");
        Instant end = instant(to, "to");
        Page<RunSummary> page = guarded(() -> history.runs(user.tenantId(), user.scope(), organizationId, assessmentId, start, end, cursor, limit(limit)));
        return new PageResponse<>(page.items().stream().map(HistoryController::run).toList(), page.nextCursor());
    }

    @Operation(summary = "Compare two runs; a change is reported only when they are comparable")
    @GetMapping("/scoring-runs/compare")
    public ComparisonResponse compare(@AuthenticationPrincipal Jwt jwt, @RequestParam String baseRunId, @RequestParam String runId) {
        CurrentUser user = CurrentUser.from(jwt);
        var c = history.compare(user.tenantId(), user.scope(), uuid(baseRunId, "baseRunId"), uuid(runId, "runId"));
        return new ComparisonResponse(run(c.base()), run(c.run()), c.comparable(), c.notComparableReasons(), c.scoreChange(),
                c.dimensions().stream().map(d -> new DimensionChangeResponse(d.dimension(), d.baseScore(), d.score(), d.change())).toList(),
                new PatternChanges(c.patternsAppeared(), c.patternsResolved(), c.patternsPersistent()), Notices.DIAGNOSTIC);
    }

    @Operation(summary = "Recommendations for a run, from the catalog of the configuration it was scored with")
    @GetMapping("/scoring-runs/{scoringRunId}/recommendations")
    public RecommendationsResponse recommendations(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID scoringRunId) {
        CurrentUser user = CurrentUser.from(jwt);
        var r = recommendations.forRun(user.tenantId(), user.scope(), scoringRunId);
        return new RecommendationsResponse(r.scoringRunId(), r.recommendations(), r.unmappedActionKeys(), Notices.DIAGNOSTIC);
    }

    @Operation(summary = "Display names, descriptions and score bands of the configuration a run was scored with")
    @GetMapping("/scoring-runs/{scoringRunId}/catalog")
    public RunCatalog catalog(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID scoringRunId) {
        CurrentUser user = CurrentUser.from(jwt);
        return catalog.forRun(user.tenantId(), user.scope(), scoringRunId);
    }

    @Operation(summary = "Run series of an organization with dimensions, patterns and penalty totals per run, oldest first")
    @GetMapping("/organizations/{organizationId}/trends")
    public TrendsResponse trends(@AuthenticationPrincipal Jwt jwt, @PathVariable String organizationId,
                                 @RequestParam(required = false) String templateId, @RequestParam(required = false) String from,
                                 @RequestParam(required = false) String to, @RequestParam(required = false) Integer limit) {
        CurrentUser user = CurrentUser.from(jwt);
        var t = history.trends(user.tenantId(), user.scope(), organizationId, templateId, instant(from, "from"), instant(to, "to"),
                limit == null ? 30 : limit(limit));
        return new TrendsResponse(t.organizationId(), t.templateId(), t.points().stream().map(p -> new TrendPointResponse(run(p.run()),
                p.dimensions(), p.patterns(), p.patternsBySeverity(), p.penalties())).toList(), Notices.DIAGNOSTIC);
    }

    @Operation(summary = "Organization dashboard: latest result per template, recent runs and assessment counts")
    @GetMapping("/organizations/{organizationId}/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt, @PathVariable String organizationId) {
        CurrentUser user = CurrentUser.from(jwt);
        var d = history.dashboard(user.tenantId(), user.scope(), organizationId);
        return new DashboardResponse(d.organizationId(), d.latestPerTemplate().stream().map(HistoryController::run).toList(),
                d.recentRuns().stream().map(HistoryController::run).toList(), d.assessmentsByStatus(), Notices.DIAGNOSTIC);
    }

    // ---------------- mapping and parameter checks ----------------

    private static AssessmentResponse assessment(AssessmentSummary a) {
        LatestRun latest = a.latestRunId() == null ? null
                : new LatestRun(a.latestRunId(), a.latestScoreStatus(), a.latestScore(), a.latestInterpretation(), a.latestCalculatedAt());
        return new AssessmentResponse(a.id(), a.organizationId(), a.templateId(), a.templateVersion(), a.status(), a.completedAt(),
                a.createdAt(), latest);
    }

    private static RunSummaryResponse run(RunSummary r) {
        return new RunSummaryResponse(r.runId(), r.assessmentId(), r.organizationId(), r.templateId(), r.templateVersion(), r.source(),
                r.score(), r.interpretation(), r.configurationVersion(), r.configurationHash(), r.algorithmVersion(), r.engineVersion(),
                r.calculatedAt(), r.previousRunId(), r.previousScore(), r.change(), r.comparable(), r.notComparableReasons());
    }

    private static <T> T guarded(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (IllegalArgumentException e) {
            throw new ApiRequestException("cursor is not valid");
        }
    }

    private static int limit(Integer value) {
        if (value == null) {
            return DEFAULT_LIMIT;
        }
        if (value < 1 || value > MAX_LIMIT) {
            throw new ApiRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        return value;
    }

    private static Instant instant(String value, String name) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new ApiRequestException(name + " must be an ISO-8601 instant such as 2026-11-01T00:00:00Z");
        }
    }

    private static UUID uuid(String value, String name) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiRequestException(name + " must be a scoring run id");
        }
    }

    @SuppressWarnings("unused")
    private static <A, B> List<B> map(List<A> list, Function<A, B> f) {
        return list.stream().map(f).toList();
    }
}
