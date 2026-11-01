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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recommendation text comes only from the configuration catalog the run was scored with (decision D28). */
@AutoConfigureMockMvc
class RecommendationsApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ConfigurationService configurations;

    private static final String BODY = "{\"assessment\":{\"organizationId\":\"org-1\",\"templateId\":\"tpl-1\",\"templateVersion\":\"1\","
            + "\"status\":\"COMPLETED\",\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":[{\"questionId\":\"Q01\",\"answer\":2},"
            + "{\"questionId\":\"Q02\",\"answer\":\"OFTEN\"},{\"questionId\":\"Q03\",\"answer\":true},"
            + "{\"questionId\":\"Q04\",\"answer\":\"only one key person\"},{\"questionId\":\"Q05\",\"answer\":80}]}";

    @BeforeEach
    void platformDefault() {
        tenant("rec-a");
        tenant("rec-b");
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

    private JsonNode calculate(String tenant) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/calculate").with(user(tenant, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode recommendations(String tenant, String run, String... roles) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/scoring-runs/" + run + "/recommendations").with(user(tenant, roles)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void aMatchedPatternYieldsItsCatalogRecommendationsInDocumentedOrder() throws Exception {
        String run = calculate("rec-a").get("scoringRunId").asText();
        JsonNode body = recommendations("rec-a", run, "VIEWER");
        assertThat(body.get("scoringRunId").asText()).isEqualTo(run);
        JsonNode items = body.get("recommendations");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("key").asText()).isEqualTo("REVIEW_TEAM_CAPACITY");
        assertThat(items.get(0).get("title").asText()).isEqualTo("Review team capacity");
        assertThat(items.get(0).get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(items.get(0).get("sources").get(0).get("id").asText()).isEqualTo("TH-001");
        assertThat(items.get(1).get("key").asText()).isEqualTo("REVIEW_WORKLOAD_DISTRIBUTION");
        assertThat(body.get("unmappedActionKeys")).isEmpty();
        assertThat(body.get("notice").asText()).contains("not an individual performance rating");
    }

    @Test
    void aLaterCatalogChangeDoesNotRewriteTheTextOfAnOlderRun() throws Exception {
        String run = calculate("rec-a").get("scoringRunId").asText();
        Caller owner = Caller.tenant(new TenantId("rec-a"), "owner-a");
        ConfigurationVersionView override = configurations.createDraft(owner, ConfigurationTarget.tenant(new TenantId("rec-a")), "catalog",
                "{\"recommendations\":[{\"key\":\"REVIEW_TEAM_CAPACITY\",\"title\":\"Changed title\",\"description\":\"Changed text\",\"priority\":1}]}");
        configurations.submitForValidation(owner, override.id());
        configurations.activate(owner, override.id(), new Approval("approver", null));

        // the first run still reads the catalog it was scored with
        assertThat(recommendations("rec-a", run, "ADMIN").get("recommendations").get(0).get("title").asText()).isEqualTo("Review team capacity");

        // a new run uses the new catalog; the key it no longer defines is reported, not invented
        String later = calculate("rec-a").get("scoringRunId").asText();
        JsonNode body = recommendations("rec-a", later, "ADMIN");
        assertThat(body.get("recommendations")).hasSize(1);
        assertThat(body.get("recommendations").get(0).get("title").asText()).isEqualTo("Changed title");
        assertThat(body.get("unmappedActionKeys").toString()).contains("REVIEW_WORKLOAD_DISTRIBUTION");
    }

    @Test
    void recommendationsAreTenantAndOrganizationScoped() throws Exception {
        String run = calculate("rec-a").get("scoringRunId").asText();
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/recommendations").with(user("rec-b", "ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/recommendations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/scoring-runs/" + java.util.UUID.randomUUID() + "/recommendations").with(user("rec-a", "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anInsufficientDataRunHasNoRecommendations() throws Exception {
        String body = "{\"assessment\":{\"organizationId\":\"org-1\",\"templateId\":\"tpl-1\",\"templateVersion\":\"1\",\"status\":\"COMPLETED\","
                + "\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":[{\"questionId\":\"Q01\",\"answer\":2}]}";
        String run = json.readTree(mvc.perform(post("/api/v1/calculate").with(user("rec-a", "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn().getResponse().getContentAsString()).get("scoringRunId").asText();
        assertThat(recommendations("rec-a", run, "ADMIN").get("recommendations")).isEmpty();
    }
}
