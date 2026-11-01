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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A team with too few answers for a REQUIRED question cannot be scored at all. It must not fail the request: the
 * organization is scored, and the team is reported without a run and without a score (decision D45).
 */
@AutoConfigureMockMvc
class ScopedRequiredQuestionIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ConfigurationService configurations;

    @BeforeEach
    void platformDefaultWithARequiredQuestion() throws Exception {
        tenant("rq-a");
        ObjectNode document = ConfigurationSamples.sampleDocument();
        ((ObjectNode) document.withArray("questions").get(0)).put("missingPolicy", "REQUIRED");
        Caller platform = Caller.platform("ops");
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "platform", json.writeValueAsString(document));
        configurations.submitForValidation(platform, draft.id());
        configurations.activate(platform, draft.id(), new Approval("approver", null));
    }

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("user-1");
            j.claim("tenant_id", "rq-a");
            j.claim("org_ids", List.of("org-1"));
            j.claim("roles", List.of("ADMIN"));
        }).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private void members(ArrayNode out, String team, int count) {
        for (int i = 0; i < count; i++) {
            out.addObject().put("questionId", "Q01").put("scope", "MEMBER").put("teamId", team).put("answer", 1 + (i % 5));
            out.addObject().put("questionId", "Q02").put("scope", "MEMBER").put("teamId", team).put("answer", "OFTEN");
            out.addObject().put("questionId", "Q03").put("scope", "MEMBER").put("teamId", team).put("answer", i % 2 == 0);
            out.addObject().put("questionId", "Q04").put("scope", "MEMBER").put("teamId", team).put("answer", "fine team");
            out.addObject().put("questionId", "Q05").put("scope", "MEMBER").put("teamId", team).put("answer", 40 + 10 * i);
        }
    }

    @Test
    void aTeamThatCannotBeScoredIsReportedWithoutARunWhileTheOrganizationIsScored() throws Exception {
        ObjectNode body = json.createObjectNode();
        body.putObject("assessment").put("organizationId", "org-1").put("templateId", "tpl-1").put("templateVersion", "1")
                .put("status", "COMPLETED").put("completedAt", "2026-11-01T09:00:00Z");
        ArrayNode responses = body.putArray("responses");
        members(responses, "alpha", 6);
        members(responses, "tiny", 2);

        JsonNode result = json.readTree(mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(result.get("scoreStatus").asText()).isEqualTo("VALID");
        assertThat(result.get("responses").get("unscoredTeams")).extracting(JsonNode::asText).containsExactly("tiny");
        JsonNode teams = result.get("teamRuns");
        assertThat(teams).hasSize(2);
        assertThat(teams.get(0).get("teamId").asText()).isEqualTo("alpha");
        assertThat(teams.get(0).get("scoreStatus").asText()).isEqualTo("VALID");
        assertThat(teams.get(1).get("teamId").asText()).isEqualTo("tiny");
        assertThat(teams.get(1).get("scoreStatus").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(teams.get(1).has("scoringRunId")).isFalse();
        assertThat(teams.get(1).has("leadershipIntelligenceScore")).isFalse();
        // organization run and one team run only
        assertThat(jdbc.queryForObject("select count(*) from scoring_run", Integer.class)).isEqualTo(2);

        // a later read of the organization run lists the unscored team too, from what was stored with the run
        JsonNode again = json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/scoring-runs/" + result.get("scoringRunId").asText()).with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(again.get("teamRuns")).hasSize(2);
        assertThat(again.get("teamRuns").get(1).get("teamId").asText()).isEqualTo("tiny");
        assertThat(again.get("teamRuns").get(1).has("scoringRunId")).isFalse();
    }
}
