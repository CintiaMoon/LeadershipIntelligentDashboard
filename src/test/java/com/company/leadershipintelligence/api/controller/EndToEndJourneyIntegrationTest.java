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

/**
 * One journey through the whole system (V3 Section 38.3): platform default, a tenant override, scoring through the
 * API, the explanation, and a recalculation that picks up the override while the history stays untouched.
 */
@AutoConfigureMockMvc
class EndToEndJourneyIntegrationTest extends PostgresIntegrationTest {

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

    private static RequestPostProcessor admin(String tenant) {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("user-1");
            j.claim("tenant_id", tenant);
            j.claim("org_ids", List.of("org-1"));
            j.claim("roles", List.of("ADMIN"));
        }).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private JsonNode calculate(String tenant) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/calculate").with(admin(tenant)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void aTenantOverrideChangesFutureScoringOnlyAndOnlyForThatTenant() throws Exception {
        tenant("e2e-a");
        tenant("e2e-b");
        Caller platform = Caller.platform("ops");
        ConfigurationVersionView base = configurations.createDraft(platform, ConfigurationTarget.platform(), "p", ConfigurationSamples.sampleText());
        configurations.submitForValidation(platform, base.id());
        configurations.activate(platform, base.id(), new Approval("approver", null));

        JsonNode before = calculate("e2e-a");
        assertThat(before.get("leadershipIntelligenceScore").decimalValue()).isEqualByComparingTo("67.00");
        String run = before.get("scoringRunId").asText();
        String assessment = before.get("assessmentId").asText();
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/explanation").with(admin("e2e-a"))).andExpect(status().isOk());

        // tenant A weights Team Health heavily; this is configuration, never a request field
        Caller owner = Caller.tenant(new TenantId("e2e-a"), "owner-a");
        ConfigurationVersionView override = configurations.createDraft(owner, ConfigurationTarget.tenant(new TenantId("e2e-a")), "weights",
                "{\"overall\":{\"weights\":{\"TEAM_HEALTH\":0.7,\"DELIVERY_STABILITY\":0.1,\"TECHNICAL_RISK\":0.1,\"LEADERSHIP_LOAD\":0.1}}}");
        configurations.submitForValidation(owner, override.id());
        configurations.activate(owner, override.id(), new Approval("approver", null));

        // history is untouched: the stored run still reads 67.00 under the old hash
        mvc.perform(get("/api/v1/scoring-runs/" + run).with(admin("e2e-a"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.leadershipIntelligenceScore").value(67.00))
                .andExpect(jsonPath("$.configurationHash").value(before.get("configurationHash").asText()));

        clock.advance(Duration.ofMinutes(5));
        JsonNode recalculated = json.readTree(mvc.perform(post("/api/v1/assessments/" + assessment + "/score/recalculate").with(admin("e2e-a")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(recalculated.get("leadershipIntelligenceScore").decimalValue()).isEqualByComparingTo("34.60");
        assertThat(recalculated.get("interpretation").asText()).isEqualTo("CRITICAL");
        assertThat(recalculated.get("configurationHash").asText()).isNotEqualTo(before.get("configurationHash").asText());
        assertThat(recalculated.get("configurationVersion").asText()).isEqualTo("platform_default:v1|tenant:v1");

        // the explanation of the new run is built from its own facts
        mvc.perform(get("/api/v1/scoring-runs/" + recalculated.get("scoringRunId").asText() + "/explanation").with(admin("e2e-a")))
                .andExpect(status().isOk());

        // another tenant is still scored with the platform default
        assertThat(calculate("e2e-b").get("leadershipIntelligenceScore").decimalValue()).isEqualByComparingTo("67.00");
        mvc.perform(get("/api/v1/assessments/" + assessment + "/score").with(admin("e2e-b"))).andExpect(status().isNotFound());
    }
}
