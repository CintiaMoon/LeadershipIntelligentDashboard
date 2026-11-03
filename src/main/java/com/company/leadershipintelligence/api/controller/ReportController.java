package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.report.CsvRenderer;
import com.company.leadershipintelligence.api.report.PdfRenderer;
import com.company.leadershipintelligence.api.report.ReportBuilder;
import com.company.leadershipintelligence.api.report.ReportDocument;
import com.company.leadershipintelligence.api.report.ReportType;
import com.company.leadershipintelligence.api.security.CurrentUser;
import com.company.leadershipintelligence.api.security.Role;
import com.company.leadershipintelligence.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Reports (decisions D59 to D64): rendered on demand from a stored run, never stored and never recalculated. The caller
 * reads the same runs as on the run endpoints (tenant and organization scope, a foreign run is a missing one), and every
 * generation is written to the access audit.
 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
public class ReportController {

    private static final Set<String> FORMATS = Set.of("json", "csv", "pdf");

    private final ReportBuilder builder;
    private final ReportService reports;

    public ReportController(ReportBuilder builder, ReportService reports) {
        this.builder = builder;
        this.reports = reports;
    }

    @Operation(summary = "A report of a stored run as json, csv or pdf (a comparison reads baseRunId and runId)")
    @GetMapping("/{type}")
    public ResponseEntity<?> report(@AuthenticationPrincipal Jwt jwt, @PathVariable String type, @RequestParam(required = false) String runId,
                                    @RequestParam(required = false) String baseRunId, @RequestParam(required = false) String format) {
        CurrentUser user = CurrentUser.from(jwt);
        ReportType reportType = ReportType.parse(type).orElseThrow(() -> new ApiRequestException(
                "type must be one of LEADERSHIP_INTELLIGENCE, DIMENSION_ANALYSIS, PATTERN_PENALTY, HISTORICAL_COMPARISON"));
        String kind = format == null ? "json" : format.toLowerCase(java.util.Locale.ROOT);
        if (!FORMATS.contains(kind)) {
            throw new ApiRequestException("format must be json, csv or pdf");
        }
        if (runId == null) {
            throw new ApiRequestException("runId is required");
        }
        UUID run = uuid(runId, "runId");
        UUID base = null;
        if (reportType.comparison()) {
            if (baseRunId == null) {
                throw new ApiRequestException("baseRunId is required for a comparison report");
            }
            base = uuid(baseRunId, "baseRunId");
        } else if (baseRunId != null) {
            throw new ApiRequestException("baseRunId is only for the comparison report");
        }
        boolean redact = !user.hasAnyRole(Role.OWNER, Role.ADMIN, Role.ANALYST);
        ReportDocument document = builder.build(reportType, user, redact, run, base);
        reports.audit(user.tenantId(), user.actor(), run, reportType.name(), kind, document.redacted());
        if (kind.equals("json")) {
            return ResponseEntity.ok(document);
        }
        boolean csv = kind.equals("csv");
        return ResponseEntity.ok()
                .contentType(csv ? new MediaType("text", "csv", StandardCharsets.UTF_8) : MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(reportType.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-') + "-" + run + "." + kind).build().toString())
                .body(csv ? CsvRenderer.render(document) : PdfRenderer.render(document));
    }

    private static UUID uuid(String value, String name) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiRequestException(name + " must be a scoring run id");
        }
    }
}
