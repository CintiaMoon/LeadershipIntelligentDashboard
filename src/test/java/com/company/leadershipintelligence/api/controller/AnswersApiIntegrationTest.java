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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

/** Source answers are evidence: only OWNER, ADMIN and ANALYST read them, tenant-scoped, audited without content (D42). */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AnswersApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ConfigurationService configurations;

    private static final String BODY = "{\"assessment\":{\"organizationId\":\"org-1\",\"templateId\":\"tpl-1\",\"templateVersion\":\"1\","
            + "\"status\":\"COMPLETED\",\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":[{\"questionId\":\"Q01\",\"answer\":2},"
            + "{\"questionId\":\"Q02\",\"answer\":\"OFTEN\"},{\"questionId\":\"Q03\",\"answer\":true},"
            + "{\"questionId\":\"Q04\",\"answer\":\"secret-free-text-marker\"},{\"questionId\":\"Q05\",\"answer\":80}]}";

    @BeforeEach
    void platformDefault() {
        tenant("ans-a");
        tenant("ans-b");
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

    private String run() throws Exception {
        JsonNode body = json.readTree(mvc.perform(post("/api/v1/calculate").with(user("ans-a", List.of("org-1"), "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return body.get("scoringRunId").asText();
    }

    @Test
    void evidenceRolesReadTheAnswersTheRunWasScoredWith() throws Exception {
        String run = run();
        for (String role : List.of("OWNER", "ADMIN", "ANALYST")) {
            JsonNode body = json.readTree(mvc.perform(get("/api/v1/scoring-runs/" + run + "/answers").with(user("ans-a", List.of("org-1"), role)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(body.get("scoringRunId").asText()).isEqualTo(run);
            JsonNode answers = body.get("answers");
            assertThat(answers).hasSize(5);
            assertThat(answers.get(0).get("questionId").asText()).isEqualTo("Q01");
            assertThat(answers.get(0).get("answer").decimalValue()).isEqualByComparingTo("2");
            assertThat(answers.get(1).get("answer").asText()).isEqualTo("OFTEN");
            assertThat(answers.get(2).get("answer").asBoolean()).isTrue();
            assertThat(answers.get(3).get("answer").asText()).isEqualTo("secret-free-text-marker");
        }
    }

    @Test
    void leadersAndViewersAreRefusedAndAnonymousCallersAreUnauthorized() throws Exception {
        String run = run();
        for (String role : List.of("ENGINEERING_LEADER", "VIEWER")) {
            mvc.perform(get("/api/v1/scoring-runs/" + run + "/answers").with(user("ans-a", List.of("org-1"), role))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/answers")).andExpect(status().isUnauthorized());
    }

    @Test
    void otherTenantsAndOrganizationsCannotReachThem() throws Exception {
        String run = run();
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/answers").with(user("ans-b", List.of("org-1"), "ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/answers").with(user("ans-a", List.of("org-2"), "ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/scoring-runs/" + java.util.UUID.randomUUID() + "/answers").with(user("ans-a", List.of("org-1"), "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void eachReadIsAuditedWithoutTheContentAndNothingIsLogged(CapturedOutput output) throws Exception {
        String run = run();
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/answers").with(user("ans-a", List.of("org-1"), "ANALYST"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/answers").with(user("ans-b", List.of("org-1"), "ANALYST"))).andExpect(status().isNotFound());

        assertThat(jdbc.queryForList("select action || '/' || actor || '/' || (detail->>'count') from scoring_access_audit where action = 'ANSWERS_READ'",
                String.class)).containsExactly("ANSWERS_READ/user-1/5");
        String stored = String.join(" ", jdbc.queryForList("select coalesce(detail::text,'') from scoring_access_audit", String.class));
        assertThat(stored).doesNotContain("secret-free-text-marker");
        assertThat(output.getAll().lines().filter(l -> l.startsWith("{\"@timestamp\"")).collect(java.util.stream.Collectors.joining(" ")))
                .doesNotContain("secret-free-text-marker");
    }

    @Test
    void theResultAndTheExplanationStillNeverContainTheAnswers() throws Exception {
        String run = run();
        String result = mvc.perform(get("/api/v1/scoring-runs/" + run).with(user("ans-a", List.of("org-1"), "ADMIN"))).andReturn().getResponse().getContentAsString();
        String explanation = mvc.perform(get("/api/v1/scoring-runs/" + run + "/explanation").with(user("ans-a", List.of("org-1"), "ADMIN")))
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("secret-free-text-marker");
        assertThat(explanation).doesNotContain("secret-free-text-marker");
    }
}
