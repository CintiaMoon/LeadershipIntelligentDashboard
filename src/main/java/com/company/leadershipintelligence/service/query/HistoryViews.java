package com.company.leadershipintelligence.service.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read models for history, comparison and the organization dashboard (decisions D26, D27). */
public final class HistoryViews {

    private HistoryViews() {
    }

    public record Page<T>(List<T> items, String nextCursor) {
    }

    public record AssessmentSummary(String id, String organizationId, String templateId, int templateVersion, String status,
                                    Instant completedAt, Instant createdAt, UUID latestRunId, BigDecimal latestScore,
                                    String latestScoreStatus, String latestInterpretation, Instant latestCalculatedAt) {
    }

    /** {@code change} and {@code comparable} follow decision D27: a delta exists only when the two runs are comparable. */
    public record RunSummary(UUID runId, String assessmentId, String organizationId, String templateId, int templateVersion,
                             String source, BigDecimal score, String interpretation, String configurationVersion,
                             String configurationHash, String algorithmVersion, String engineVersion, Instant calculatedAt,
                             UUID previousRunId, BigDecimal previousScore, BigDecimal change, boolean comparable,
                             List<String> notComparableReasons) {
    }

    public record DimensionChange(String dimension, BigDecimal baseScore, BigDecimal score, BigDecimal change) {
    }

    public record RunComparison(RunSummary base, RunSummary run, boolean comparable, List<String> notComparableReasons,
                                BigDecimal scoreChange, List<DimensionChange> dimensions, List<String> patternsAppeared,
                                List<String> patternsResolved, List<String> patternsPersistent) {
    }

    public record DimensionPoint(String dimension, BigDecimal rawScore, BigDecimal adjustedScore) {
    }

    public record PatternHit(String patternId, String severity, String category) {
    }

    /** Counts per terminal state, and the amounts, of one run's penalties; suppressed amounts are never part of the deduction. */
    public record PenaltyTotals(int applied, int suppressed, int ineligible, int notTriggered, BigDecimal effectiveDeduction,
                                BigDecimal suppressedAmount) {
    }

    /** One valid run of a series with everything the trend charts need, all read from stored findings (decision D40). */
    public record TrendPoint(RunSummary run, List<DimensionPoint> dimensions, List<PatternHit> patterns,
                             Map<String, Integer> patternsBySeverity, PenaltyTotals penalties) {
    }

    public record Trends(String organizationId, String templateId, List<TrendPoint> points) {
    }

    public record Dashboard(String organizationId, List<RunSummary> latestPerTemplate, List<RunSummary> recentRuns,
                            Map<String, Integer> assessmentsByStatus) {
    }
}
