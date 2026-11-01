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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The display catalog (D39) and the organization trends (D40) are read from stored runs and configuration only. */
@AutoConfigureMockMvc
class CatalogAndTrendsApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ConfigurationService configurations;

    private static final String ANSWERS = "[{\"questionId\":\"Q01\",\"answer\":2},{\"questionId\":\"Q02\",\"answer\":\"OFTEN\"},"
            + "{\"questionId\":\"Q03\",\"answer\":true},{\"questionId\":\"Q04\",\"answer\":\"only one key person\"},{\"questionId\":\"Q05\",\"answer\":80}]";
    private static final String HEALTHY = "[{\"questionId\":\"Q01\",\"answer\":5},{\"questionId\":\"Q02\",\"answer\":\"OFTEN\"},"
            + "{\"questionId\":\"Q03\",\"answer\":true},{\"questionId\":\"Q04\",\"answer\":\"only one key person\"},{\"questionId\":\"Q05\",\"answer\":80}]";

    @BeforeEach
    void platformDefault() {
        tenant("cat-a");
        tenant("cat-b");
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
        return user("cat-a", List.of("org-1"), "ADMIN");
    }

    private JsonNode calculate(String answers) throws Exception {
        clock.advance(Duration.ofMinutes(1));
        String body = "{\"assessment\":{\"organizationId\":\"org-1\",\"templateId\":\"tpl-1\",\"templateVersion\":\"1\",\"status\":\"COMPLETED\","
                + "\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":" + answers + "}";
        return json.readTree(mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode getJson(RequestPostProcessor who, String url) throws Exception {
        return json.readTree(mvc.perform(get(url).with(who)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    // ---------------- catalog ----------------

    @Test
    void theCatalogGivesNamesDescriptionsExplanationsAndScoreBands() throws Exception {
        String run = calculate(ANSWERS).get("scoringRunId").asText();
        JsonNode catalog = getJson(user("cat-a", List.of("org-1"), "VIEWER"), "/api/v1/scoring-runs/" + run + "/catalog");
        assertThat(catalog.get("scoringRunId").asText()).isEqualTo(run);
        assertThat(catalog.get("patterns").get("TH-001").get("name").asText()).isEqualTo("Team burnout risk");
        assertThat(catalog.get("patterns").get("TH-001").get("explanation").asText()).contains("not any individual");
        assertThat(catalog.get("penalties").get("TH-P01").get("name").asText()).isEqualTo("Burnout risk deduction");
        assertThat(catalog.get("dimensions").get("TEAM_HEALTH").get("name").asText()).isEqualTo("Team Health");
        assertThat(catalog.get("signals").get("WORKLOAD_PRESSURE")).isNotNull();
        assertThat(catalog.get("questions").get("Q01").get("name").asText()).isEqualTo("Team sustainability");
        JsonNode bands = catalog.get("lisBands");
        assertThat(bands).hasSize(4);
        assertThat(bands.get(2).get("label").asText()).isEqualTo("HEALTHY");
        assertThat(bands.get(2).get("min").decimalValue()).isEqualByComparingTo("60");
        assertThat(bands.get(2).get("max").decimalValue()).isEqualByComparingTo("80");
        assertThat(catalog.get("dimensionBands")).isNotEmpty();
    }

    @Test
    void aLaterCatalogChangeNeverRewritesTheWordsOfAnOlderRun() throws Exception {
        String run = calculate(ANSWERS).get("scoringRunId").asText();
        Caller owner = Caller.tenant(new TenantId("cat-a"), "owner-a");
        ConfigurationVersionView override = configurations.createDraft(owner, ConfigurationTarget.tenant(new TenantId("cat-a")), "wording",
                "{\"presentation\":{\"patterns\":{\"TH-001\":{\"name\":\"Renamed\",\"description\":\"Changed\"}}}}");
        configurations.submitForValidation(owner, override.id());
        configurations.activate(owner, override.id(), new Approval("approver", null));

        assertThat(getJson(admin(), "/api/v1/scoring-runs/" + run + "/catalog").get("patterns").get("TH-001").get("name").asText())
                .isEqualTo("Team burnout risk");
        String later = calculate(ANSWERS).get("scoringRunId").asText();
        JsonNode changed = getJson(admin(), "/api/v1/scoring-runs/" + later + "/catalog").get("patterns").get("TH-001");
        assertThat(changed.get("name").asText()).isEqualTo("Renamed");
        assertThat(changed.get("description").asText()).isEqualTo("Changed");
        // overrides merge field by field, so the fields they do not name keep their base value
        assertThat(changed.get("explanation").asText()).contains("not any individual");
    }

    @Test
    void anUnknownReferenceInThePresentationFailsActivationNotScoring() {
        Caller owner = Caller.tenant(new TenantId("cat-a"), "owner-a");
        ConfigurationVersionView draft = configurations.createDraft(owner, ConfigurationTarget.tenant(new TenantId("cat-a")), "bad",
                "{\"presentation\":{\"patterns\":{\"NOPE-1\":{\"name\":\"x\",\"description\":\"y\"}}}}");
        var result = configurations.submitForValidation(owner, draft.id());
        assertThat(result.valid()).isFalse();
    }

    @Test
    void theCatalogIsTenantScopedAndNeedsAToken() throws Exception {
        String run = calculate(ANSWERS).get("scoringRunId").asText();
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/catalog").with(user("cat-b", List.of("org-1"), "ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/catalog").with(user("cat-a", List.of("org-2"), "ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/catalog")).andExpect(status().isUnauthorized());
    }

    // ---------------- trends ----------------

    @Test
    void trendsListValidRunsOldestFirstWithEverythingTheChartsNeed() throws Exception {
        JsonNode first = calculate(ANSWERS);
        JsonNode second = calculate(HEALTHY);
        JsonNode trends = getJson(admin(), "/api/v1/organizations/org-1/trends");
        assertThat(trends.get("organizationId").asText()).isEqualTo("org-1");
        JsonNode points = trends.get("points");
        assertThat(points).hasSize(2);
        assertThat(points.get(0).get("run").get("scoringRunId").asText()).isEqualTo(first.get("scoringRunId").asText());
        assertThat(points.get(1).get("run").get("scoringRunId").asText()).isEqualTo(second.get("scoringRunId").asText());
        assertThat(points.get(1).get("run").get("comparable").asBoolean()).isTrue();

        JsonNode p0 = points.get(0);
        assertThat(p0.get("dimensions")).hasSize(4);
        assertThat(p0.get("patterns").get(0).get("patternId").asText()).isEqualTo("TH-001");
        assertThat(p0.get("patternsBySeverity").get("CRITICAL").asInt()).isEqualTo(1);
        assertThat(p0.get("patternsBySeverity").get("LOW").asInt()).isZero();
        assertThat(p0.get("penalties").get("applied").asInt()).isEqualTo(1);
        assertThat(p0.get("penalties").get("effectiveDeduction").decimalValue()).isEqualByComparingTo("12.00");
        // burnout is gone in the second run: no pattern, no deduction
        assertThat(points.get(1).get("patterns")).isEmpty();
        assertThat(points.get(1).get("penalties").get("effectiveDeduction").decimalValue()).isEqualByComparingTo("0");
        assertThat(trends.get("notice").asText()).contains("not an individual performance rating");
    }

    @Test
    void trendsHonourLimitTemplateDatesAndScope() throws Exception {
        for (int i = 0; i < 3; i++) {
            calculate(ANSWERS);
        }
        assertThat(getJson(admin(), "/api/v1/organizations/org-1/trends?limit=2").get("points")).hasSize(2);
        assertThat(getJson(admin(), "/api/v1/organizations/org-1/trends?templateId=other-template").get("points")).isEmpty();
        assertThat(getJson(admin(), "/api/v1/organizations/org-1/trends?templateId=tpl-1").get("points")).hasSize(3);
        assertThat(getJson(admin(), "/api/v1/organizations/org-1/trends?from=2999-01-01T00:00:00Z").get("points")).isEmpty();
        mvc.perform(get("/api/v1/organizations/org-1/trends?limit=0").with(admin())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/organizations/org-1/trends?to=soon").with(admin())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/organizations/org-2/trends").with(admin())).andExpect(status().isForbidden());
        assertThat(getJson(user("cat-b", List.of("org-1"), "ADMIN"), "/api/v1/organizations/org-1/trends").get("points")).isEmpty();
        mvc.perform(get("/api/v1/organizations/org-1/trends")).andExpect(status().isUnauthorized());
    }

    @Test
    void aChangedConfigurationBreaksComparabilityInTheTrendWithItsReason() throws Exception {
        calculate(ANSWERS);
        Caller owner = Caller.tenant(new TenantId("cat-a"), "owner-a");
        ConfigurationVersionView override = configurations.createDraft(owner, ConfigurationTarget.tenant(new TenantId("cat-a")), "weights",
                "{\"overall\":{\"weights\":{\"TEAM_HEALTH\":0.7,\"DELIVERY_STABILITY\":0.1,\"TECHNICAL_RISK\":0.1,\"LEADERSHIP_LOAD\":0.1}}}");
        configurations.submitForValidation(owner, override.id());
        configurations.activate(owner, override.id(), new Approval("approver", null));
        calculate(ANSWERS);
        JsonNode second = getJson(admin(), "/api/v1/organizations/org-1/trends").get("points").get(1).get("run");
        assertThat(second.get("comparable").asBoolean()).isFalse();
        assertThat(second.get("notComparableReasons").toString()).contains("CONFIGURATION_DIFFERS");
    }
}
