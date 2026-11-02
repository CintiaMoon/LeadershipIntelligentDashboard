package com.company.leadershipintelligence.service.query;

import com.company.leadershipintelligence.domain.enums.ConfigurationScope;
import com.company.leadershipintelligence.domain.enums.ConfigurationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration management as the application exposes it (decisions D52 to D58). Everything here is numbers, ids and rules: a
 * preview or a review never carries an answer.
 */
public final class ConfigurationViews {

    private ConfigurationViews() {
    }

    /**
     * What one scoring of some answers shows (never the answers themselves).
     *
     * @param status    VALID, INSUFFICIENT_DATA, or ERROR when the configuration could not score these answers
     * @param score     the overall score, null unless VALID
     * @param dimensions adjusted score by dimension
     * @param patterns  ids of the detected patterns, sorted
     * @param penalties effective deduction by applied penalty id
     * @param error     why scoring failed, when status is ERROR
     */
    public record ConfigurationOutcome(String status, BigDecimal score, String band, Map<String, BigDecimal> dimensions, List<String> patterns,
                                       Map<String, BigDecimal> penalties, String error) {

        public ConfigurationOutcome {
            dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
            patterns = patterns == null ? List.of() : List.copyOf(patterns);
            penalties = penalties == null ? Map.of() : Map.copyOf(penalties);
        }
    }

    public record FixtureView(UUID id, String name, Object answers, Object expectations, String createdBy, Instant updatedAt) {
    }

    public record FixtureResultView(UUID fixtureId, String name, boolean passed, List<String> failures, ConfigurationOutcome outcome) {
    }

    /** @param kind FIXTURE or ASSESSMENT; for an assessment {@code name} is its id */
    public record PreviewItem(String kind, String name, String organizationId, ConfigurationOutcome before, ConfigurationOutcome after, List<String> changes) {
    }

    /** Score distribution of the assessments of a preview: scored count, minimum, median, maximum and the number in each band. */
    public record Distribution(int scored, BigDecimal minimum, BigDecimal median, BigDecimal maximum, Map<String, Integer> bands) {

        public Distribution {
            bands = bands == null ? Map.of() : Map.copyOf(bands);
        }
    }

    public record PreviewSummary(int fixtures, int fixturesPassed, int assessments, boolean assessmentsUsed, Distribution before, Distribution after,
                                 int patternsAppearing, int patternsDisappearing, int scoresChanged) {
    }

    public record PreviewResult(UUID versionId, PreviewSummary summary, List<PreviewItem> items, List<FixtureResultView> fixtures) {
    }

    /** The calibration and approval record of a version (decision D57). Without a calibration reference the version is unvalidated. */
    public record ReviewView(UUID versionId, String submittedBy, Instant submittedAt, List<String> validationProblems, PreviewSummary preview,
                             List<FixtureResultView> fixtures, String approvedBy, Instant approvedAt, String approvalNote,
                             String calibrationReference, String knownLimitations, String rejectedBy, Instant rejectedAt, String rejectionComment) {
    }

    /** A version with its review; {@code unvalidated} is true when no calibration reference was stated. */
    public record ConfigurationVersionDetail(UUID id, UUID layerId, ConfigurationScope scope, String scopeId, int version, ConfigurationStatus status,
                                             String configHash, String createdBy, Instant createdAt, String approvedBy, Instant approvedAt,
                                             Instant effectiveFrom, boolean unvalidated, ReviewView review, String payload) {
    }

    /** One layer a caller may manage with its active version (null when none) and the one being worked on (null when none). */
    public record ConfigurationLayer(UUID id, ConfigurationScope scope, String scopeId, String name, ConfigurationVersionSummary active,
                                     ConfigurationVersionSummary open) {
    }

    public record ConfigurationVersionSummary(UUID id, int version, ConfigurationStatus status, Instant effectiveFrom, boolean unvalidated) {
    }
}
