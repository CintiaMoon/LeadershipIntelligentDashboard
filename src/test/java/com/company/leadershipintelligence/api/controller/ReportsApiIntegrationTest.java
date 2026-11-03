package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.company.leadershipintelligence.service.ConfigurationService;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reports are rendered from stored runs only, are scoped like the run reads, are deterministic and are audited (D59 to D64). */
@AutoConfigureMockMvc
class ReportsApiIntegrationTest extends PostgresIntegrationTest {

    private static final String[] TYPES = {"LEADERSHIP_INTELLIGENCE", "DIMENSION_ANALYSIS", "PATTERN_PENALTY"};
    private static final String FREE_TEXT = "only one key person";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ConfigurationService configurations;

    private static final String BODY = "{\"assessment\":{\"organizationId\":\"org-1\",\"templateId\":\"tpl-1\",\"templateVersion\":\"1\","
            + "\"status\":\"COMPLETED\",\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":[{\"questionId\":\"Q01\",\"answer\":2},"
            + "{\"questionId\":\"Q02\",\"answer\":\"OFTEN\"},{\"questionId\":\"Q03\",\"answer\":true},"
            + "{\"questionId\":\"Q04\",\"answer\":\"" + FREE_TEXT + "\"},{\"questionId\":\"Q05\",\"answer\":80}]}";

    @BeforeEach
    void platformDefault() {
        tenant("rep-a");
        tenant("rep-b");
        Caller platform = Caller.platform("ops");
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "platform", ConfigurationSamples.sampleText());
        configurations.submitForValidation(platform, draft.id());
        configurations.activate(platform, draft.id(), new Approval("approver", null));
    }

    private static RequestPostProcessor user(String tenant, String... roles) {
        SimpleGrantedAuthority[] authorities = java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toArray(SimpleGrantedAuthority[]::new);
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("user-1");
            j.claim("tenant_id", tenant);
            j.claim("org_ids", List.of("org-1"));
            j.claim("roles", List.of(roles));
        }).authorities(authorities);
    }

    private String calculate(String tenant, String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/calculate").with(user(tenant, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("scoringRunId").asText();
    }

    private MvcResult report(String tenant, String role, String type, String query, int expected) throws Exception {
        return mvc.perform(get("/api/v1/reports/" + type + "?" + query).with(user(tenant, role))).andExpect(status().is(expected)).andReturn();
    }

    private JsonNode jsonReport(String tenant, String role, String type, String runId) throws Exception {
        return json.readTree(report(tenant, role, type, "runId=" + runId, 200).getResponse().getContentAsString());
    }

    private static String pdfText(byte[] bytes) throws Exception {
        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(bytes);
        var extractor = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
        StringBuilder text = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            text.append(extractor.getTextFromPage(page)).append(' ');
        }
        reader.close();
        return text.toString();
    }

    private static JsonNode section(JsonNode report, String id) {
        for (JsonNode s : report.get("sections")) {
            if (s.get("id").asText().equals(id)) {
                return s;
            }
        }
        throw new AssertionError("no section " + id + " in " + report);
    }

    @Test
    void leadershipReportStatesTheScoreFindingsAdviceAndMethodology() throws Exception {
        String run = calculate("rep-a", BODY);
        JsonNode report = jsonReport("rep-a", "ADMIN", "LEADERSHIP_INTELLIGENCE", run);
        assertThat(report.get("title").asText()).isEqualTo("Leadership Intelligence Report");
        assertThat(report.get("notice").asText()).contains("not an individual performance rating");
        assertThat(report.get("redacted").asBoolean()).isFalse();
        assertThat(section(report, "summary").toString()).contains("org-1", "Leadership Intelligence Score");
        assertThat(section(report, "dimensions").get("rows")).isNotEmpty();
        assertThat(section(report, "patterns").get("rows").toString()).contains("TH-001", "MATCHED");
        assertThat(section(report, "recommendations").get("rows").toString()).contains("Review team capacity");
        assertThat(section(report, "evidence").get("columns").toString()).contains("Value");
        String methodology = section(report, "methodology").toString();
        assertThat(methodology).contains("Engine version", "Algorithm version", "Configuration hash", "Unvalidated");
    }

    @Test
    void dimensionAnalysisListsWhatEachQuestionContributed() throws Exception {
        String run = calculate("rep-a", BODY);
        JsonNode report = jsonReport("rep-a", "ANALYST", "dimension-analysis", run);
        JsonNode rows = section(report, "contributions").get("rows");
        assertThat(rows).isNotEmpty();
        assertThat(rows.toString()).contains("Q01");
    }

    @Test
    void patternAndPenaltyReportShowsEveryOutcome() throws Exception {
        String run = calculate("rep-a", BODY);
        JsonNode report = jsonReport("rep-a", "OWNER", "PATTERN_PENALTY", run);
        assertThat(section(report, "patterns").get("columns").toString()).contains("Severity", "Confidence");
        assertThat(section(report, "penalties").get("columns").toString()).contains("Candidate", "Suppressed");
        assertThat(section(report, "effective")).isNotNull();
    }

    @Test
    void aViewerGetsTheSameRedactionAsOnTheRunEndpoints() throws Exception {
        String run = calculate("rep-a", BODY);
        JsonNode analysis = jsonReport("rep-a", "VIEWER", "DIMENSION_ANALYSIS", run);
        assertThat(analysis.get("redacted").asBoolean()).isTrue();
        assertThat(section(analysis, "contributions").get("rows")).isEmpty();
        assertThat(section(analysis, "contributions").get("note").asText()).contains("withheld");
        JsonNode leadership = jsonReport("rep-a", "VIEWER", "LEADERSHIP_INTELLIGENCE", run);
        assertThat(section(leadership, "evidence").get("columns").toString()).doesNotContain("Value");
    }

    @Test
    void theSameRunAlwaysGivesTheSameReportInEveryFormat() throws Exception {
        String run = calculate("rep-a", BODY);
        for (String type : TYPES) {
            String first = report("rep-a", "ADMIN", type, "runId=" + run, 200).getResponse().getContentAsString();
            String second = report("rep-a", "ADMIN", type, "runId=" + run, 200).getResponse().getContentAsString();
            assertThat(second).as(type).isEqualTo(first);
            byte[] csv1 = report("rep-a", "ADMIN", type, "runId=" + run + "&format=csv", 200).getResponse().getContentAsByteArray();
            byte[] csv2 = report("rep-a", "ADMIN", type, "runId=" + run + "&format=csv", 200).getResponse().getContentAsByteArray();
            assertThat(csv2).as(type + " csv").isEqualTo(csv1);
        }
    }

    @Test
    void csvIsADownloadWithTheSameNumbersAsTheJson() throws Exception {
        String run = calculate("rep-a", BODY);
        MvcResult csv = report("rep-a", "ADMIN", "LEADERSHIP_INTELLIGENCE", "runId=" + run + "&format=csv", 200);
        assertThat(csv.getResponse().getContentType()).startsWith("text/csv");
        assertThat(csv.getResponse().getHeader("Content-Disposition")).contains("attachment", ".csv", run);
        String text = new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿Leadership Intelligence Report");
        JsonNode summary = section(jsonReport("rep-a", "ADMIN", "LEADERSHIP_INTELLIGENCE", run), "summary");
        for (JsonNode row : summary.get("rows")) {
            assertThat(text).contains(row.get(0).asText());
        }
    }

    @Test
    void noReportInAnyFormatContainsAnAnswerText() throws Exception {
        String run = calculate("rep-a", BODY);
        for (String type : TYPES) {
            for (String format : List.of("json", "csv", "pdf")) {
                byte[] raw = report("rep-a", "ADMIN", type, "runId=" + run + "&format=" + format, 200).getResponse().getContentAsByteArray();
                String body = format.equals("pdf") ? pdfText(raw) : new String(raw, StandardCharsets.UTF_8);
                assertThat(body).as(type + " " + format).doesNotContain(FREE_TEXT);
            }
        }
    }

    @Test
    void pdfIsADownloadWhoseTextIsTheTextOfTheJsonReport() throws Exception {
        String run = calculate("rep-a", BODY);
        MvcResult pdf = report("rep-a", "ADMIN", "LEADERSHIP_INTELLIGENCE", "runId=" + run + "&format=pdf", 200);
        assertThat(pdf.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(pdf.getResponse().getHeader("Content-Disposition")).contains("attachment", ".pdf", run);
        byte[] bytes = pdf.getResponse().getContentAsByteArray();
        assertThat(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        String text = pdfText(bytes);
        JsonNode report = jsonReport("rep-a", "ADMIN", "LEADERSHIP_INTELLIGENCE", run);
        assertThat(text).contains(report.get("title").asText());
        for (JsonNode row : section(report, "summary").get("rows")) {
            assertThat(text).contains(row.get(0).asText());
        }
        assertThat(text).contains("Review team capacity").doesNotContain(FREE_TEXT);
    }

    @Test
    void comparisonReportShowsChangesBetweenTwoComparableRuns() throws Exception {
        String base = calculate("rep-a", BODY);
        String later = calculate("rep-a", BODY.replace("\"answer\":80", "\"answer\":20"));
        JsonNode report = json.readTree(report("rep-a", "ADMIN", "HISTORICAL_COMPARISON", "baseRunId=" + base + "&runId=" + later, 200)
                .getResponse().getContentAsString());
        assertThat(report.get("title").asText()).isEqualTo("Historical Comparison Report");
        assertThat(section(report, "runs").toString()).contains(base, later);
        assertThat(section(report, "comparable").toString()).contains("Yes");
        assertThat(section(report, "dimensions").get("rows")).isNotEmpty();
        assertThat(section(report, "methodology").toString()).contains("Configuration hash");
        String csv = new String(report("rep-a", "ADMIN", "historical-comparison", "baseRunId=" + base + "&runId=" + later + "&format=csv", 200)
                .getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("Historical Comparison Report", base, later);
    }

    @Test
    void aForeignRunLooksMissingForEveryTypeAndFormat() throws Exception {
        String run = calculate("rep-a", BODY);
        for (String type : TYPES) {
            report("rep-b", "ADMIN", type, "runId=" + run, 404);
            report("rep-b", "ADMIN", type, "runId=" + run + "&format=csv", 404);
            report("rep-b", "ADMIN", type, "runId=" + run + "&format=pdf", 404);
        }
        String other = calculate("rep-a", BODY);
        report("rep-b", "ADMIN", "HISTORICAL_COMPARISON", "baseRunId=" + run + "&runId=" + other, 404);
        report("rep-a", "ADMIN", "LEADERSHIP_INTELLIGENCE", "runId=" + UUID.randomUUID(), 404);
        mvc.perform(get("/api/v1/reports/LEADERSHIP_INTELLIGENCE?runId=" + run)).andExpect(status().isUnauthorized());
    }

    @Test
    void platformAdministratorsReachNoReport() throws Exception {
        String run = calculate("rep-a", BODY);
        mvc.perform(get("/api/v1/reports/LEADERSHIP_INTELLIGENCE?runId=" + run).with(SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject("ops").claim("roles", List.of("PLATFORM_ADMIN"))).authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void badRequestsAreRefusedWithAMessage() throws Exception {
        String run = calculate("rep-a", BODY);
        report("rep-a", "ADMIN", "RISK", "runId=" + run, 400);
        report("rep-a", "ADMIN", "LEADERSHIP_INTELLIGENCE", "runId=not-a-run", 400);
        report("rep-a", "ADMIN", "LEADERSHIP_INTELLIGENCE", "runId=" + run + "&format=xml", 400);
        report("rep-a", "ADMIN", "LEADERSHIP_INTELLIGENCE", "runId=" + run + "&baseRunId=" + run, 400);
        report("rep-a", "ADMIN", "HISTORICAL_COMPARISON", "runId=" + run, 400);
        mvc.perform(get("/api/v1/reports/LEADERSHIP_INTELLIGENCE").with(user("rep-a", "ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test
    void anInsufficientDataRunIsReportedWithoutANumber() throws Exception {
        String body = "{\"assessment\":{\"organizationId\":\"org-1\",\"templateId\":\"tpl-1\",\"templateVersion\":\"1\",\"status\":\"COMPLETED\","
                + "\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":[{\"questionId\":\"Q01\",\"answer\":2}]}";
        String run = calculate("rep-a", body);
        JsonNode report = jsonReport("rep-a", "ADMIN", "LEADERSHIP_INTELLIGENCE", run);
        assertThat(section(report, "summary").get("note").asText()).contains("not enough data");
        assertThat(section(report, "summary").toString()).contains("INSUFFICIENT_DATA");
        assertThat(section(report, "recommendations").get("rows")).isEmpty();
    }

    @Test
    void everyGenerationIsAuditedWithoutContent() throws Exception {
        String run = calculate("rep-a", BODY);
        report("rep-a", "ANALYST", "PATTERN_PENALTY", "runId=" + run, 200);
        report("rep-a", "ANALYST", "PATTERN_PENALTY", "runId=" + run + "&format=csv", 200);
        report("rep-a", "ANALYST", "PATTERN_PENALTY", "runId=" + run + "&format=pdf", 200);
        report("rep-a", "ANALYST", "PATTERN_PENALTY", "runId=" + run + "&format=xml", 400);
        assertThat(jdbc.queryForList("select detail->>'type' || '/' || (detail->>'format') from scoring_access_audit where action = 'REPORT_GENERATED' order by id",
                String.class)).containsExactly("PATTERN_PENALTY/json", "PATTERN_PENALTY/csv", "PATTERN_PENALTY/pdf");
        String stored = String.join(" ", jdbc.queryForList("select coalesce(detail::text,'') from scoring_access_audit", String.class));
        assertThat(stored).doesNotContain(FREE_TEXT);
    }
}
