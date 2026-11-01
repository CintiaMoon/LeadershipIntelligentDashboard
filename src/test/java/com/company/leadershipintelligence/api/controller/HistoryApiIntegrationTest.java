package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.domain.valueobject.TenantId;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Assessment lists, score history, comparability, comparison and the dashboard (decisions D26, D27). */
@AutoConfigureMockMvc
class HistoryApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ConfigurationService configurations;

    private static final String OTHER_ANSWERS = "[{\"questionId\":\"Q01\",\"answer\":5},{\"questionId\":\"Q02\",\"answer\":\"OFTEN\"},"
            + "{\"questionId\":\"Q03\",\"answer\":true},{\"questionId\":\"Q04\",\"answer\":\"only one key person\"},{\"questionId\":\"Q05\",\"answer\":80}]";
    private static final String ANSWERS = "[{\"questionId\":\"Q01\",\"answer\":2},{\"questionId\":\"Q02\",\"answer\":\"OFTEN\"},"
            + "{\"questionId\":\"Q03\",\"answer\":true},{\"questionId\":\"Q04\",\"answer\":\"only one key person\"},{\"questionId\":\"Q05\",\"answer\":80}]";

    @BeforeEach
    void platformDefault() {
        tenant("hist-a");
        tenant("hist-b");
        Caller platform = Caller.platform("ops");
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "platform", ConfigurationSamples.sampleText());
        configurations.submitForValidation(platform, draft.id());
        configurations.activate(platform, draft.id(), new Approval("approver", null));
    }

    private static RequestPostProcessor user(String tenant, List<String> organizations, String... roles) {
        SimpleGrantedAuthority[] authorities = java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toArray(SimpleGrantedAuthority[]::new);
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("user-1");
            j.claim("tenant_id", tenant);
            j.claim("org_ids", organizations);
            j.claim("roles", List.of(roles));
        }).authorities(authorities);
    }

    private static RequestPostProcessor admin() {
        return user("hist-a", List.of("org-1", "org-2"), "ADMIN");
    }

    private JsonNode calculate(RequestPostProcessor who, String org, String template, String templateVersion, String answers) throws Exception {
        clock.advance(Duration.ofMinutes(1));
        String body = "{\"assessment\":{\"organizationId\":\"" + org + "\",\"templateId\":\"" + template + "\",\"templateVersion\":\""
                + templateVersion + "\",\"status\":\"COMPLETED\",\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":" + answers + "}";
        return json.readTree(mvc.perform(post("/api/v1/calculate").with(who).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode getJson(RequestPostProcessor who, String url) throws Exception {
        return json.readTree(mvc.perform(get(url).with(who)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static boolean absent(JsonNode node, String field) {
        return node.get(field) == null || node.get(field).isNull();
    }

    @Test
    void historyShowsThePreviousScoreAndAChangeOnlyBetweenComparableRuns() throws Exception {
        JsonNode first = calculate(admin(), "org-1", "tpl-1", "1", ANSWERS);
        JsonNode second = calculate(admin(), "org-1", "tpl-1", "1", OTHER_ANSWERS);

        JsonNode items = getJson(admin(), "/api/v1/scoring-runs?organizationId=org-1").get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("scoringRunId").asText()).isEqualTo(second.get("scoringRunId").asText());
        assertThat(items.get(0).get("previousScoringRunId").asText()).isEqualTo(first.get("scoringRunId").asText());
        assertThat(items.get(0).get("comparable").asBoolean()).isTrue();
        assertThat(items.get(0).get("change").decimalValue()).isEqualByComparingTo(
                second.get("leadershipIntelligenceScore").decimalValue().subtract(first.get("leadershipIntelligenceScore").decimalValue()));
        // the first run of a series has nothing to compare with
        assertThat(items.get(1).get("comparable").asBoolean()).isFalse();
        assertThat(absent(items.get(1), "change")).isTrue();
        assertThat(items.get(1).get("notComparableReasons")).isEmpty();
    }

    @Test
    void aChangedConfigurationOrTemplateMakesTheChangeNotComparable() throws Exception {
        calculate(admin(), "org-1", "tpl-1", "1", ANSWERS);
        Caller owner = Caller.tenant(new TenantId("hist-a"), "owner-a");
        ConfigurationVersionView override = configurations.createDraft(owner, ConfigurationTarget.tenant(new TenantId("hist-a")), "weights",
                "{\"overall\":{\"weights\":{\"TEAM_HEALTH\":0.7,\"DELIVERY_STABILITY\":0.1,\"TECHNICAL_RISK\":0.1,\"LEADERSHIP_LOAD\":0.1}}}");
        configurations.submitForValidation(owner, override.id());
        configurations.activate(owner, override.id(), new Approval("approver", null));
        calculate(admin(), "org-1", "tpl-1", "1", ANSWERS);

        JsonNode items = getJson(admin(), "/api/v1/scoring-runs?organizationId=org-1").get("items");
        assertThat(items.get(0).get("comparable").asBoolean()).isFalse();
        assertThat(absent(items.get(0), "change")).isTrue();
        assertThat(items.get(0).get("previousScore").decimalValue()).isEqualByComparingTo("67.00");
        assertThat(items.get(0).get("notComparableReasons").toString()).contains("CONFIGURATION_DIFFERS");

        // a different template starts its own series
        calculate(admin(), "org-1", "tpl-2", "1", ANSWERS);
        JsonNode latest = getJson(admin(), "/api/v1/scoring-runs?organizationId=org-1").get("items").get(0);
        assertThat(latest.get("templateId").asText()).isEqualTo("tpl-2");
        assertThat(latest.get("comparable").asBoolean()).isFalse();
        assertThat(absent(latest, "previousScoringRunId")).isTrue();
    }

    @Test
    void historyIsPagedWithOpaqueCursorsAndFilteredByDate() throws Exception {
        for (int i = 0; i < 3; i++) {
            calculate(admin(), "org-1", "tpl-1", "1", ANSWERS);
        }
        JsonNode first = getJson(admin(), "/api/v1/scoring-runs?limit=2");
        assertThat(first.get("items")).hasSize(2);
        String cursor = first.get("nextCursor").asText();
        JsonNode second = getJson(admin(), "/api/v1/scoring-runs?limit=2&cursor=" + cursor);
        assertThat(second.get("items")).hasSize(1);
        assertThat(absent(second, "nextCursor")).isTrue();
        assertThat(second.get("items").get(0).get("scoringRunId").asText())
                .isNotIn(first.get("items").get(0).get("scoringRunId").asText(), first.get("items").get(1).get("scoringRunId").asText());

        assertThat(getJson(admin(), "/api/v1/scoring-runs?from=2999-01-01T00:00:00Z").get("items")).isEmpty();
        assertThat(getJson(admin(), "/api/v1/scoring-runs?to=2999-01-01T00:00:00Z").get("items")).hasSize(3);
        mvc.perform(get("/api/v1/scoring-runs?cursor=not-a-cursor").with(admin())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/scoring-runs?limit=0").with(admin())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/scoring-runs?limit=101").with(admin())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/scoring-runs?from=yesterday").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void organizationsOutsideTheTokenAndOtherTenantsAreInvisible() throws Exception {
        calculate(admin(), "org-1", "tpl-1", "1", ANSWERS);
        JsonNode inOrg2 = calculate(admin(), "org-2", "tpl-1", "1", ANSWERS);

        RequestPostProcessor onlyOrg1 = user("hist-a", List.of("org-1"), "VIEWER");
        assertThat(getJson(onlyOrg1, "/api/v1/scoring-runs").get("items")).hasSize(1);
        assertThat(getJson(onlyOrg1, "/api/v1/assessments").get("items")).hasSize(1);
        mvc.perform(get("/api/v1/scoring-runs?organizationId=org-2").with(onlyOrg1)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assessments?organizationId=org-2").with(onlyOrg1)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/organizations/org-2/dashboard").with(onlyOrg1)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assessments/" + inOrg2.get("assessmentId").asText()).with(onlyOrg1)).andExpect(status().isNotFound());

        RequestPostProcessor otherTenant = user("hist-b", List.of("org-1", "org-2"), "ADMIN");
        assertThat(getJson(otherTenant, "/api/v1/scoring-runs").get("items")).isEmpty();
        assertThat(getJson(otherTenant, "/api/v1/assessments").get("items")).isEmpty();
        mvc.perform(get("/api/v1/assessments/" + inOrg2.get("assessmentId").asText()).with(otherTenant)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/scoring-runs/compare?baseRunId=" + inOrg2.get("scoringRunId").asText() + "&runId="
                + inOrg2.get("scoringRunId").asText()).with(otherTenant)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/scoring-runs")).andExpect(status().isUnauthorized());
    }

    @Test
    void assessmentsListAndDetailCarryTheLatestResult() throws Exception {
        JsonNode run = calculate(admin(), "org-1", "tpl-1", "1", ANSWERS);
        JsonNode list = getJson(admin(), "/api/v1/assessments?organizationId=org-1&status=COMPLETED");
        assertThat(list.get("items")).hasSize(1);
        JsonNode one = list.get("items").get(0);
        assertThat(one.get("assessmentId").asText()).isEqualTo(run.get("assessmentId").asText());
        assertThat(one.get("latestRun").get("scoringRunId").asText()).isEqualTo(run.get("scoringRunId").asText());
        assertThat(one.get("latestRun").get("leadershipIntelligenceScore").decimalValue()).isEqualByComparingTo("67.00");

        JsonNode detail = getJson(admin(), "/api/v1/assessments/" + run.get("assessmentId").asText());
        assertThat(detail.get("templateId").asText()).isEqualTo("tpl-1");
        assertThat(detail.get("latestRun").get("interpretation").asText()).isEqualTo("HEALTHY");
        assertThat(getJson(admin(), "/api/v1/assessments?status=DRAFT").get("items")).isEmpty();
        mvc.perform(get("/api/v1/assessments?status=BOGUS").with(admin())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/assessments/asm-missing").with(admin())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSESSMENT_NOT_FOUND"));
    }

    @Test
    void insufficientDataRunsStayOutOfTheHistory() throws Exception {
        calculate(admin(), "org-1", "tpl-1", "1", ANSWERS);
        JsonNode insufficient = calculate(admin(), "org-1", "tpl-1", "1", "[{\"questionId\":\"Q01\",\"answer\":2}]");
        assertThat(insufficient.get("scoreStatus").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(getJson(admin(), "/api/v1/scoring-runs").get("items")).hasSize(1);
        mvc.perform(get("/api/v1/scoring-runs/compare?baseRunId=" + insufficient.get("scoringRunId").asText() + "&runId="
                + insufficient.get("scoringRunId").asText()).with(admin())).andExpect(status().isNotFound());
        // the assessment still shows what its latest run was
        assertThat(getJson(admin(), "/api/v1/assessments/" + insufficient.get("assessmentId").asText()).get("latestRun").get("scoreStatus").asText())
                .isEqualTo("INSUFFICIENT_DATA");
    }

    @Test
    void comparisonReportsDimensionChangesAndPatternsOnlyForComparableRuns() throws Exception {
        JsonNode first = calculate(admin(), "org-1", "tpl-1", "1", ANSWERS);
        JsonNode second = calculate(admin(), "org-1", "tpl-1", "1", OTHER_ANSWERS);
        JsonNode compared = getJson(admin(), "/api/v1/scoring-runs/compare?baseRunId=" + first.get("scoringRunId").asText()
                + "&runId=" + second.get("scoringRunId").asText());
        assertThat(compared.get("comparable").asBoolean()).isTrue();
        assertThat(compared.get("scoreChange").decimalValue()).isEqualByComparingTo(
                second.get("leadershipIntelligenceScore").decimalValue().subtract(first.get("leadershipIntelligenceScore").decimalValue()));
        assertThat(compared.get("dimensions")).hasSize(4);
        JsonNode team = null;
        for (JsonNode d : compared.get("dimensions")) {
            if (d.get("dimension").asText().equals("TEAM_HEALTH")) {
                team = d;
            }
        }
        assertThat(team).isNotNull();
        assertThat(team.get("change").decimalValue()).isEqualByComparingTo(team.get("score").decimalValue().subtract(team.get("baseScore").decimalValue()));
        // burnout matched in the first run (Q01 = 2) and is gone in the second (Q01 = 5)
        assertThat(compared.get("patterns").get("resolved").toString()).contains("TH-001");
        assertThat(compared.get("notice").asText()).contains("not an individual performance rating");

        // runs from different templates are shown side by side without a delta
        JsonNode other = calculate(admin(), "org-1", "tpl-2", "1", ANSWERS);
        JsonNode mismatch = getJson(admin(), "/api/v1/scoring-runs/compare?baseRunId=" + first.get("scoringRunId").asText()
                + "&runId=" + other.get("scoringRunId").asText());
        assertThat(mismatch.get("comparable").asBoolean()).isFalse();
        assertThat(mismatch.get("notComparableReasons").toString()).contains("TEMPLATE_DIFFERS");
        assertThat(absent(mismatch, "scoreChange")).isTrue();
        mvc.perform(get("/api/v1/scoring-runs/compare?baseRunId=x&runId=y").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void theDashboardSummarizesTheOrganizationAndCarriesTheDiagnosticNotice() throws Exception {
        calculate(admin(), "org-1", "tpl-1", "1", ANSWERS);
        JsonNode newest = calculate(admin(), "org-1", "tpl-1", "1", OTHER_ANSWERS);
        calculate(admin(), "org-1", "tpl-2", "1", ANSWERS);
        JsonNode dashboard = getJson(admin(), "/api/v1/organizations/org-1/dashboard");
        assertThat(dashboard.get("organizationId").asText()).isEqualTo("org-1");
        assertThat(dashboard.get("latestPerTemplate")).hasSize(2);
        assertThat(dashboard.get("recentRuns")).hasSize(3);
        assertThat(dashboard.get("assessmentsByStatus").get("COMPLETED").asInt()).isEqualTo(3);
        boolean found = false;
        for (JsonNode latest : dashboard.get("latestPerTemplate")) {
            if (latest.get("templateId").asText().equals("tpl-1")) {
                assertThat(latest.get("scoringRunId").asText()).isEqualTo(newest.get("scoringRunId").asText());
                found = true;
            }
        }
        assertThat(found).isTrue();
        assertThat(dashboard.get("notice").asText()).contains("not a sufficient basis for employment decisions");
        assertThat(calculate(admin(), "org-1", "tpl-1", "1", ANSWERS).get("notice").asText()).contains("organizational diagnostic signal");
    }
}
