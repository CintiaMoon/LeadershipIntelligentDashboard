package com.company.leadershipintelligence.api.report;

import com.company.leadershipintelligence.api.mapper.RunResponseMapper;
import com.company.leadershipintelligence.api.report.ReportDocument.Section;
import com.company.leadershipintelligence.api.response.DimensionResponse;
import com.company.leadershipintelligence.api.response.Notices;
import com.company.leadershipintelligence.api.response.ScoringResultResponse;
import com.company.leadershipintelligence.api.security.CurrentUser;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.service.CatalogService;
import com.company.leadershipintelligence.service.RecommendationService;
import com.company.leadershipintelligence.service.ReportService;
import com.company.leadershipintelligence.service.ScoringHistoryService;
import com.company.leadershipintelligence.service.query.CatalogViews.RunCatalog;
import com.company.leadershipintelligence.service.query.HistoryViews.RunComparison;
import com.company.leadershipintelligence.service.query.RecommendationViews.RunRecommendations;
import com.company.leadershipintelligence.service.query.ReportViews.Calibration;
import com.company.leadershipintelligence.service.query.ScoringRunView;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the four reports of decision D59 from stored facts only: the run, its stored trace, the display catalog and
 * recommendations of the configuration it was scored with, and the comparison of two stored runs. Nothing is
 * recalculated or inferred, and a caller who may not see per-question evidence gets the same redaction as on the run
 * endpoints (decision D19). The result is a pure function of what is stored.
 */
@Component
public class ReportBuilder {

    private static final String NA = "n/a";

    private final RunResponseMapper mapper;
    private final CatalogService catalog;
    private final RecommendationService recommendations;
    private final ScoringHistoryService history;
    private final ReportService reports;

    public ReportBuilder(RunResponseMapper mapper, CatalogService catalog, RecommendationService recommendations,
                         ScoringHistoryService history, ReportService reports) {
        this.mapper = mapper;
        this.catalog = catalog;
        this.recommendations = recommendations;
        this.history = history;
        this.reports = reports;
    }

    public ReportDocument build(ReportType type, CurrentUser user, boolean redact, UUID runId, UUID baseRunId) {
        if (type.comparison()) {
            return comparison(user, runId, baseRunId);
        }
        ScoringRunView run = reports.run(user.tenantId(), user.scope(), runId);
        if (run.traceJson() == null) {
            throw new ScoringException(ScoringErrorCode.SCORING_RUN_NOT_FOUND, "the run has no result: it failed", Map.of("scoringRunId", runId.toString()));
        }
        ScoringResultResponse result = mapper.result(run, user.tenantId().value(), redact);
        RunCatalog names = catalog.forRun(user.tenantId(), user.scope(), runId);
        Calibration calibration = reports.calibration(run);
        List<Section> sections = new ArrayList<>();
        sections.add(summary(result));
        switch (type) {
            case LEADERSHIP_INTELLIGENCE -> {
                sections.add(dimensionTable(result, names));
                sections.add(overall(result));
                sections.add(patterns(mapper.patterns(run, redact).patterns(), names, true));
                sections.add(penalties(mapper.penalties(run, redact).penalties(), names, true));
                sections.add(imbalances(mapper.imbalances(run).imbalances(), names));
                sections.add(evidence(mapper.patterns(run, redact).patterns(), names, redact));
                sections.add(recommendationsSection(recommendations.forRun(user.tenantId(), user.scope(), runId)));
            }
            case DIMENSION_ANALYSIS -> {
                sections.add(dimensionTable(result, names));
                sections.add(contributions(mapper.dimensions(run, redact).dimensions(), names, redact));
            }
            case PATTERN_PENALTY -> {
                sections.add(patterns(mapper.patterns(run, redact).patterns(), names, false));
                sections.add(penalties(mapper.penalties(run, redact).penalties(), names, false));
                sections.add(effectiveByTarget(mapper.penalties(run, redact).effectiveByTarget()));
            }
            default -> throw new IllegalStateException("not a single-run report: " + type);
        }
        sections.add(methodology(result, calibration));
        return new ReportDocument(type.name(), type.title(), redact, Notices.DIAGNOSTIC, List.copyOf(sections));
    }

    // ---------------- comparison ----------------

    private ReportDocument comparison(CurrentUser user, UUID runId, UUID baseRunId) {
        RunComparison c = history.compare(user.tenantId(), user.scope(), baseRunId, runId);
        RunCatalog names = catalog.forRun(user.tenantId(), user.scope(), runId);
        List<Section> sections = new ArrayList<>();
        sections.add(section("runs", "The two runs", null, List.of("Item", "Base run", "Current run"), List.of(
                row("Run", c.base().runId(), c.run().runId()),
                row("Organization", c.base().organizationId(), c.run().organizationId()),
                row("Questionnaire", c.base().templateId() + " v" + c.base().templateVersion(), c.run().templateId() + " v" + c.run().templateVersion()),
                row("Calculated", c.base().calculatedAt(), c.run().calculatedAt()),
                row("Leadership Intelligence Score", decimal(c.base().score()), decimal(c.run().score())),
                row("Interpretation", c.base().interpretation(), c.run().interpretation()))));
        String comparable = c.comparable() ? "Yes" : "No: " + String.join(", ", c.notComparableReasons());
        sections.add(section("comparable", "Can the runs be compared", c.comparable() ? null
                        : "A change is not reported because the runs were scored under different conditions (decision D27).",
                List.of("Item", "Value"), List.of(row("Comparable", comparable), row("Change in score", decimal(c.scoreChange())))));
        List<List<String>> dimensions = new ArrayList<>();
        c.dimensions().forEach(d -> dimensions.add(row(name(names.dimensions(), d.dimension()), decimal(d.baseScore()), decimal(d.score()), decimal(d.change()))));
        sections.add(section("dimensions", "Dimension scores", null, List.of("Dimension", "Base run", "Current run", "Change"), dimensions));
        List<List<String>> patternRows = new ArrayList<>();
        c.patternsAppeared().forEach(p -> patternRows.add(row(name(names.patterns(), p), "Appeared")));
        c.patternsResolved().forEach(p -> patternRows.add(row(name(names.patterns(), p), "No longer detected")));
        c.patternsPersistent().forEach(p -> patternRows.add(row(name(names.patterns(), p), "Detected in both")));
        sections.add(section("patterns", "Patterns", patternRows.isEmpty() ? "No pattern was detected in either run." : null,
                List.of("Pattern", "Change"), patternRows));
        sections.add(section("methodology", "Methodology and versions", "Runs scored with different versions are not directly comparable.",
                List.of("Item", "Base run", "Current run"), List.of(
                        row("Engine version", c.base().engineVersion(), c.run().engineVersion()),
                        row("Algorithm version", c.base().algorithmVersion(), c.run().algorithmVersion()),
                        row("Configuration version", c.base().configurationVersion(), c.run().configurationVersion()),
                        row("Configuration hash", c.base().configurationHash(), c.run().configurationHash()))));
        return new ReportDocument(ReportType.HISTORICAL_COMPARISON.name(), ReportType.HISTORICAL_COMPARISON.title(), false, Notices.DIAGNOSTIC, List.copyOf(sections));
    }

    // ---------------- sections of a single run ----------------

    private static Section summary(ScoringResultResponse r) {
        List<List<String>> rows = new ArrayList<>(List.of(
                row("Organization", r.organizationId()),
                row("Assessment", r.assessmentId()),
                row("Scope", "TEAM".equals(r.scope()) ? "Team " + r.teamId() : "Organization"),
                row("Calculated", r.calculatedAt()),
                row("Score status", r.scoreStatus()),
                row("Leadership Intelligence Score", decimal(r.leadershipIntelligenceScore())),
                row("Interpretation", r.interpretation())));
        return section("summary", "Summary", "INSUFFICIENT_DATA".equals(r.scoreStatus())
                ? "There was not enough data to calculate a score; no number is shown." : null, List.of("Item", "Value"), rows);
    }

    private static Section dimensionTable(ScoringResultResponse r, RunCatalog names) {
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<String, DimensionResponse> e : r.dimensions().entrySet()) {
            DimensionResponse d = e.getValue();
            rows.add(row(name(names.dimensions(), e.getKey()), d.status(), decimal(d.rawScore()), decimal(d.effectivePenalty()),
                    decimal(d.adjustedScore()), decimal(d.coverage()), decimal(d.weight()), decimal(d.weightedContribution()), d.label()));
        }
        return section("dimensions", "Dimension scores", "Scores run from 0 to 100; 100 is the desirable state.",
                List.of("Dimension", "Status", "Raw score", "Penalty", "Adjusted score", "Coverage", "Weight", "Weighted contribution", "Band"), rows);
    }

    private static Section overall(ScoringResultResponse r) {
        var o = r.overall();
        return section("overall", "Overall score", null, List.of("Item", "Value"), List.of(
                row("Weighted adjusted score", o == null ? NA : decimal(o.weightedAdjustedScore())),
                row("Overall penalty", o == null ? NA : decimal(o.effectiveOverallPenalty())),
                row("Final score", o == null ? NA : decimal(o.finalScore()))));
    }

    private static Section patterns(JsonNode patterns, RunCatalog names, boolean matchedOnly) {
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode p : patterns) {
            boolean matched = "MATCHED".equals(p.path("status").asText());
            if (matchedOnly && !matched) {
                continue;
            }
            String id = p.path("patternId").asText();
            rows.add(row(name(names.patterns(), id), id, p.path("category").asText(), p.path("severity").asText(), p.path("status").asText(),
                    p.path("reason").asText(), number(p.path("confidence")), text(p.path("confidenceBand"))));
        }
        return section("patterns", matchedOnly ? "Detected patterns" : "Pattern evaluations",
                rows.isEmpty() ? "No pattern was detected." : "A pattern is an organizational condition; severity is its impact and confidence is the quality of the evidence.",
                List.of("Pattern", "Id", "Category", "Severity", "Status", "Reason", "Confidence", "Confidence band"), rows);
    }

    private static Section penalties(JsonNode penalties, RunCatalog names, boolean appliedOnly) {
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode p : penalties) {
            String status = p.path("status").asText();
            if (appliedOnly && !"APPLIED".equals(status)) {
                continue;
            }
            String id = p.path("penaltyId").asText();
            rows.add(row(name(names.penalties(), id), id, p.path("target").asText(), p.path("strategy").asText(), status, p.path("reason").asText(),
                    p.path("triggerPatternId").asText(), number(p.path("candidateDeduction")), number(p.path("effectiveDeduction")),
                    number(p.path("suppressedAmount"))));
        }
        return section("penalties", appliedOnly ? "Applied penalties" : "Penalties and their outcome",
                rows.isEmpty() ? "No penalty was applied." : "A pattern match is not a penalty: each penalty ends APPLIED, SUPPRESSED, INELIGIBLE or NOT_TRIGGERED.",
                List.of("Penalty", "Id", "Target", "Strategy", "Status", "Reason", "Pattern", "Candidate", "Applied", "Suppressed"), rows);
    }

    private static Section effectiveByTarget(JsonNode byTarget) {
        List<List<String>> rows = new ArrayList<>();
        byTarget.properties().forEach(e -> rows.add(row(e.getKey(), number(e.getValue()))));
        return section("effective", "Total deduction by target", null, List.of("Target", "Deduction"), rows);
    }

    private static Section imbalances(JsonNode imbalances, RunCatalog names) {
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode i : imbalances) {
            if (!"DETECTED".equals(i.path("status").asText())) {
                continue;
            }
            rows.add(row(i.path("ruleId").asText(), name(names.dimensions(), i.path("left").asText()), name(names.dimensions(), i.path("right").asText()),
                    number(i.path("gap")), number(i.path("threshold")), i.path("severity").asText()));
        }
        return section("imbalances", "Imbalances", rows.isEmpty() ? "No imbalance was detected." : "An imbalance is a gap between two dimension scores.",
                List.of("Rule", "First dimension", "Second dimension", "Gap", "Threshold", "Severity"), rows);
    }

    private static Section evidence(JsonNode patterns, RunCatalog names, boolean redact) {
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode p : patterns) {
            if (!"MATCHED".equals(p.path("status").asText())) {
                continue;
            }
            String pattern = name(names.patterns(), p.path("patternId").asText());
            for (JsonNode e : p.path("evidence")) {
                rows.add(redact ? row(pattern, e.path("kind").asText(), e.path("operandKey").asText(), number(e.path("reliability")))
                        : row(pattern, e.path("kind").asText(), e.path("operandKey").asText(), number(e.path("reliability")), number(e.path("numericValue"))));
            }
        }
        return section("evidence", "Key evidence", redact ? "Values are withheld for your role." : (rows.isEmpty() ? "No pattern was detected." : null),
                redact ? List.of("Pattern", "Kind", "Measure", "Reliability") : List.of("Pattern", "Kind", "Measure", "Reliability", "Value"), rows);
    }

    private static Section recommendationsSection(RunRecommendations r) {
        List<List<String>> rows = new ArrayList<>();
        r.recommendations().forEach(x -> rows.add(row(x.title(), String.valueOf(x.priority()), x.severity(), x.description())));
        return section("recommendations", "Recommendations", rows.isEmpty() ? "No recommendation applies." : "Text comes from the catalog of the configuration the run was scored with.",
                List.of("Recommendation", "Priority", "Severity", "Description"), rows);
    }

    private static Section contributions(JsonNode dimensions, RunCatalog names, boolean redact) {
        List<List<String>> rows = new ArrayList<>();
        if (!redact) {
            for (JsonNode d : dimensions) {
                JsonNode score = d.path("score");
                String dimension = name(names.dimensions(), score.path("dimension").asText());
                for (JsonNode c : score.path("contributions")) {
                    String question = c.path("questionId").asText();
                    rows.add(row(dimension, question, name(names.questions(), question), c.path("status").asText(), number(c.path("value")),
                            number(c.path("weight")), number(c.path("contribution"))));
                }
            }
        }
        return section("contributions", "What each question contributed", redact ? "Per-question values are withheld for your role." : (rows.isEmpty() ? "No question contributed." : null),
                List.of("Dimension", "Question", "Text", "Status", "Normalized value", "Weight", "Contribution"), rows);
    }

    private static Section methodology(ScoringResultResponse r, Calibration calibration) {
        List<List<String>> rows = new ArrayList<>(List.of(
                row("Engine version", r.engineVersion()),
                row("Algorithm version", r.algorithmVersion()),
                row("Configuration version", r.configurationVersion()),
                row("Configuration hash", r.configurationHash()),
                row("Input hash", r.inputHash()),
                row("Configuration selection", r.configurationSelection()),
                row("Calibration status", calibration.calibrated() ? "Calibrated" : "Unvalidated: no calibration reference is recorded for every part of the configuration")));
        calibration.layers().forEach(l -> {
            rows.add(row("Layer " + l.scope() + " v" + l.version() + " calibration reference", l.calibrationReference() == null ? "None" : l.calibrationReference()));
            if (l.knownLimitations() != null) {
                rows.add(row("Layer " + l.scope() + " v" + l.version() + " known limitations", l.knownLimitations()));
            }
        });
        return section("methodology", "Methodology and scoring version", "The score is deterministic: the same answers, configuration and engine version give the same result.",
                List.of("Item", "Value"), rows);
    }

    // ---------------- cells ----------------

    private static Section section(String id, String heading, String note, List<String> columns, List<List<String>> rows) {
        return new Section(id, heading, note, columns, List.copyOf(rows));
    }

    private static List<String> row(Object... cells) {
        List<String> row = new ArrayList<>();
        for (Object cell : cells) {
            row.add(cell == null ? NA : cell instanceof BigDecimal d ? decimal(d) : cell.toString());
        }
        return row;
    }

    private static String decimal(BigDecimal value) {
        if (value == null) {
            return NA;
        }
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String number(JsonNode node) {
        return node.isNumber() ? decimal(node.decimalValue()) : NA;
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? NA : node.asText();
    }

    private static String name(Map<String, com.company.leadershipintelligence.service.query.CatalogViews.Entry> entries, String id) {
        var entry = entries.get(id);
        return entry == null || entry.name() == null || entry.name().isBlank() ? id : entry.name();
    }
}
