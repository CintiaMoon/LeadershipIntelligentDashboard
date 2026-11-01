package com.company.leadershipintelligence.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Response shapes for assessment lists, run histories, comparison and the dashboard (decisions D26-D28). */
public final class HistoryResponses {

    private HistoryResponses() {
    }

    public record PageResponse<T>(List<T> items, String nextCursor) {
    }

    public record AssessmentResponse(String assessmentId, String organizationId, String templateId, int templateVersion, String status,
                                     Instant completedAt, Instant createdAt, LatestRun latestRun) {
    }

    public record LatestRun(UUID scoringRunId, String scoreStatus, BigDecimal leadershipIntelligenceScore, String interpretation,
                            Instant calculatedAt) {
    }

    public record RunSummaryResponse(UUID scoringRunId, String assessmentId, String organizationId, String templateId, int templateVersion,
                                     String source, BigDecimal leadershipIntelligenceScore, String interpretation,
                                     String configurationVersion, String configurationHash, String algorithmVersion, String engineVersion,
                                     Instant calculatedAt, UUID previousScoringRunId, BigDecimal previousScore, BigDecimal change,
                                     boolean comparable, List<String> notComparableReasons) {
    }

    public record DimensionChangeResponse(String dimension, BigDecimal baseScore, BigDecimal score, BigDecimal change) {
    }

    public record ComparisonResponse(RunSummaryResponse base, RunSummaryResponse run, boolean comparable, List<String> notComparableReasons,
                                     BigDecimal scoreChange, List<DimensionChangeResponse> dimensions, PatternChanges patterns,
                                     String notice) {
    }

    public record PatternChanges(List<String> appeared, List<String> resolved, List<String> persistent) {
    }

    public record RecommendationsResponse(UUID scoringRunId,
                                          List<com.company.leadershipintelligence.service.query.RecommendationViews.Recommendation> recommendations,
                                          List<String> unmappedActionKeys, String notice) {
    }

    public record TrendPointResponse(RunSummaryResponse run,
                                     List<com.company.leadershipintelligence.service.query.HistoryViews.DimensionPoint> dimensions,
                                     List<com.company.leadershipintelligence.service.query.HistoryViews.PatternHit> patterns,
                                     Map<String, Integer> patternsBySeverity,
                                     com.company.leadershipintelligence.service.query.HistoryViews.PenaltyTotals penalties) {
    }

    public record TrendsResponse(String organizationId, String templateId, List<TrendPointResponse> points, String notice) {
    }

    public record DashboardResponse(String organizationId, List<RunSummaryResponse> latestPerTemplate, List<RunSummaryResponse> recentRuns,
                                    Map<String, Integer> assessmentsByStatus, String notice) {
    }
}
