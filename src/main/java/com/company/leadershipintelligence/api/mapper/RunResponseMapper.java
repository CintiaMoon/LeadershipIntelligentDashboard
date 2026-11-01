package com.company.leadershipintelligence.api.mapper;

import com.company.leadershipintelligence.api.response.CountsResponse;
import com.company.leadershipintelligence.api.response.DimensionResponse;
import com.company.leadershipintelligence.api.response.ExplanationResponse;
import com.company.leadershipintelligence.api.response.OverallResponse;
import com.company.leadershipintelligence.api.response.RunDimensionsResponse;
import com.company.leadershipintelligence.api.response.RunImbalancesResponse;
import com.company.leadershipintelligence.api.response.RunPatternsResponse;
import com.company.leadershipintelligence.api.response.RunPenaltiesResponse;
import com.company.leadershipintelligence.api.response.ScoringResultResponse;
import com.company.leadershipintelligence.api.response.TeamRunResponse;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.service.query.ScoringRunView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shapes a stored run into API responses. Everything comes from the stored trace; nothing is recalculated. Entities
 * and domain objects are never serialized directly (specv3.md Section 37.1).
 */
@Component
public class RunResponseMapper {

    private final ObjectMapper json;

    public RunResponseMapper(ObjectMapper json) {
        this.json = json;
    }

    public ScoringResultResponse result(ScoringRunView run, String tenantId, boolean redact) {
        return result(run, tenantId, redact, List.of());
    }

    /** @param teamRuns the team runs produced with an organization run (empty for a run scored from one answer per question) */
    public ScoringResultResponse result(ScoringRunView run, String tenantId, boolean redact, List<ScoringRunView> teamRuns) {
        JsonNode trace = trace(run);
        JsonNode responses = run.responseSummariesJson() == null ? null : read(run.responseSummariesJson());
        Map<String, DimensionResponse> dimensions = null;
        OverallResponse overall = null;
        JsonNode imbalances = null;
        JsonNode patterns = null;
        JsonNode penalties = null;
        CountsResponse counts = null;
        if (trace != null) {
            dimensions = dimensions(trace);
            JsonNode overallNode = step(trace, "OVERALL").path("overall");
            overall = new OverallResponse(decimal(overallNode.path("weightedAdjustedScore")),
                    decimal(overallNode.path("overallPenaltyApplied")), decimal(overallNode.path("finalScore")));
            imbalances = step(trace, "IMBALANCES").path("imbalances");
            patterns = step(trace, "PATTERNS").path("patterns");
            penalties = step(trace, "PENALTIES").path("aggregation").path("results");
            counts = counts(patterns, penalties);
            if (redact) {
                patterns = TraceRedactor.patterns(patterns);
                penalties = TraceRedactor.penalties(penalties);
            }
        }
        return new ScoringResultResponse(run.id(), run.assessmentId(), run.snapshotId(), tenantId, run.organizationId(), run.source(),
                run.configurationSelection(), run.engineVersion(), run.algorithmVersion(), run.configurationVersion(),
                run.configurationHash(), run.inputHash(), run.status().name(), run.scoreStatus() == null ? null : run.scoreStatus().name(),
                run.finalScore(), run.interpretation(), dimensions, overall, imbalances, patterns, penalties, counts, run.errorCode(),
                run.completedAt(), run.traceJson() == null ? null : run.id(), com.company.leadershipintelligence.api.response.Notices.DIAGNOSTIC,
                run.scopeType(), run.scopeId(), run.parentRunId(), responses, teamRunList(teamRuns, responses));
    }

    /** Team results in team order, followed by teams that could not be scored (no run, never a score). */
    private static List<TeamRunResponse> teamRunList(List<ScoringRunView> teamRuns, JsonNode responses) {
        List<TeamRunResponse> list = new ArrayList<>();
        for (ScoringRunView team : teamRuns) {
            list.add(new TeamRunResponse(team.scopeId(), team.id(), team.scoreStatus() == null ? null : team.scoreStatus().name(),
                    team.finalScore(), team.interpretation()));
        }
        if (responses != null) {
            for (JsonNode unscored : responses.path("unscoredTeams")) {
                list.add(new TeamRunResponse(unscored.asText(), null, "INSUFFICIENT_DATA", null, null));
            }
        }
        list.sort(Comparator.comparing(TeamRunResponse::teamId));
        return list.isEmpty() ? null : List.copyOf(list);
    }

    private JsonNode read(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored response summaries are not valid JSON", e);
        }
    }

    public RunDimensionsResponse dimensions(ScoringRunView run, boolean redact) {
        JsonNode trace = requireTrace(run);
        JsonNode raw = step(trace, "RAW_DIMENSIONS").path("dimensions");
        return new RunDimensionsResponse(run.id(), redact ? TraceRedactor.dimensions(raw) : raw,
                step(trace, "ADJUSTED_DIMENSIONS").path("dimensions"), redact);
    }

    public RunPatternsResponse patterns(ScoringRunView run, boolean redact) {
        JsonNode patterns = step(requireTrace(run), "PATTERNS").path("patterns");
        return new RunPatternsResponse(run.id(), redact ? TraceRedactor.patterns(patterns) : patterns, redact);
    }

    public RunPenaltiesResponse penalties(ScoringRunView run, boolean redact) {
        JsonNode aggregation = step(requireTrace(run), "PENALTIES").path("aggregation");
        JsonNode results = aggregation.path("results");
        return new RunPenaltiesResponse(run.id(), redact ? TraceRedactor.penalties(results) : results,
                aggregation.path("effectiveByTarget"), aggregation.path("tieBreakVersion").asText(), redact);
    }

    public RunImbalancesResponse imbalances(ScoringRunView run) {
        return new RunImbalancesResponse(run.id(), step(requireTrace(run), "IMBALANCES").path("imbalances"));
    }

    public ExplanationResponse explanation(ScoringRunView run, boolean redact) {
        JsonNode trace = requireTrace(run);
        return new ExplanationResponse(run.id(), redact, redact ? TraceRedactor.trace(trace) : trace);
    }

    // ---------------- reading the trace ----------------

    private Map<String, DimensionResponse> dimensions(JsonNode trace) {
        Map<String, DimensionResponse> map = new LinkedHashMap<>();
        JsonNode adjusted = step(trace, "ADJUSTED_DIMENSIONS");
        for (JsonNode entry : step(trace, "RAW_DIMENSIONS").path("dimensions")) {
            JsonNode score = entry.path("score");
            String name = score.path("dimension").asText();
            JsonNode a = find(adjusted.path("dimensions"), name);
            map.put(name, new DimensionResponse(score.path("status").asText(), decimal(score.path("rawScore")),
                    decimal(a.path("dimensionPenalty")), decimal(a.path("adjustedScore")),
                    decimal(score.path("coverage").path("coverage")), decimal(a.path("weight")),
                    decimal(a.path("weightedContribution")), adjusted.path("labels").path(name).path("label").asText(null)));
        }
        return map;
    }

    private static JsonNode find(JsonNode adjustedDimensions, String name) {
        for (JsonNode a : adjustedDimensions) {
            if (name.equals(a.path("dimension").asText())) {
                return a;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static CountsResponse counts(JsonNode patterns, JsonNode penalties) {
        int matched = 0;
        int applied = 0;
        int suppressed = 0;
        for (JsonNode p : patterns) {
            matched += "MATCHED".equals(p.path("status").asText()) ? 1 : 0;
        }
        for (JsonNode p : penalties) {
            applied += "APPLIED".equals(p.path("status").asText()) ? 1 : 0;
            suppressed += "SUPPRESSED".equals(p.path("status").asText()) ? 1 : 0;
        }
        return new CountsResponse(matched, applied, suppressed);
    }

    private static JsonNode step(JsonNode trace, String stage) {
        for (JsonNode step : trace.path("steps")) {
            if (stage.equals(step.path("stage").asText())) {
                return step;
            }
        }
        throw new IllegalStateException("the stored trace has no " + stage + " step");
    }

    private static BigDecimal decimal(JsonNode node) {
        return node.isNumber() ? node.decimalValue() : null;
    }

    private JsonNode trace(ScoringRunView run) {
        if (run.traceJson() == null) {
            return null;
        }
        try {
            return json.readTree(run.traceJson());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("the stored trace could not be read", e);
        }
    }

    private JsonNode requireTrace(ScoringRunView run) {
        JsonNode trace = trace(run);
        if (trace == null) {
            throw new ScoringException(ScoringErrorCode.SCORING_RUN_NOT_FOUND, "the run has no result: it failed",
                    Map.of("scoringRunId", run.id().toString()));
        }
        return trace;
    }
}
