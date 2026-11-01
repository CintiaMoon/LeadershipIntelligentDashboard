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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Answers with a scope (decision D45): member answers are combined, an organization run and a run for each team are
 * stored together, small groups are never scored or listed, and nothing can single out a respondent.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ScopedResponsesApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ConfigurationService configurations;

    @BeforeEach
    void platformDefault() {
        tenant("sc-a");
        tenant("sc-b");
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
        return user("sc-a", List.of("org-1"), "ADMIN");
    }

    // ---------------- request builders ----------------

    private ObjectNode request(ArrayNode responses) {
        ObjectNode body = json.createObjectNode();
        ObjectNode assessment = body.putObject("assessment");
        assessment.put("organizationId", "org-1").put("templateId", "tpl-1").put("templateVersion", "1")
                .put("status", "COMPLETED").put("completedAt", "2026-11-01T09:00:00Z");
        body.set("responses", responses);
        return body;
    }

    private void member(ArrayNode out, String team, int index) {
        add(out, "Q01", 1 + (index % 5), team);
        add(out, "Q02", "OFTEN", team);
        add(out, "Q03", index % 2 == 0, team);
        add(out, "Q04", "fine team", team);
        add(out, "Q05", 40 + 10 * index, team);
    }

    private void add(ArrayNode out, String question, Object answer, String team) {
        ObjectNode node = out.addObject();
        node.put("questionId", question).put("scope", "MEMBER");
        switch (answer) {
            case Integer i -> node.put("answer", i);
            case Boolean b -> node.put("answer", b);
            default -> node.put("answer", answer.toString());
        }
        if (team != null) {
            node.put("teamId", team);
        }
    }

    private ArrayNode teams(int alpha, int beta, int tiny) {
        ArrayNode out = json.createArrayNode();
        for (int i = 0; i < alpha; i++) {
            member(out, "alpha", i);
        }
        for (int i = 0; i < beta; i++) {
            member(out, "beta", i);
        }
        for (int i = 0; i < tiny; i++) {
            member(out, "tiny", i);
        }
        return out;
    }

    private JsonNode calculate(ObjectNode body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode getJson(String url, RequestPostProcessor user) throws Exception {
        return json.readTree(mvc.perform(get(url).with(user)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode teamRun(JsonNode result, String team) {
        for (JsonNode t : result.get("teamRuns")) {
            if (team.equals(t.get("teamId").asText())) {
                return t;
            }
        }
        throw new AssertionError("no team run for " + team);
    }

    // ---------------- tests ----------------

    @Test
    void memberAnswersGiveAnOrganizationRunAndARunPerTeam() throws Exception {
        JsonNode result = calculate(request(teams(5, 6, 0)));

        assertThat(result.get("scope").asText()).isEqualTo("ORGANIZATION");
        assertThat(result.get("scoreStatus").asText()).isEqualTo("VALID");
        assertThat(result.get("leadershipIntelligenceScore")).isNotNull();
        assertThat(result.get("teamRuns")).hasSize(2);
        assertThat(result.get("responses").get("questions")).hasSize(5);
        result.get("responses").get("questions").forEach(q -> {
            assertThat(q.get("scope").asText()).isEqualTo("MEMBER");
            assertThat(q.get("respondents").asInt()).isEqualTo(11);
            assertThat(q.get("participation").asText()).isEqualTo("OBSERVED");
        });

        String orgRun = result.get("scoringRunId").asText();
        for (String team : List.of("alpha", "beta")) {
            JsonNode entry = teamRun(result, team);
            assertThat(entry.get("scoreStatus").asText()).isEqualTo("VALID");
            JsonNode run = getJson("/api/v1/scoring-runs/" + entry.get("scoringRunId").asText(), admin());
            assertThat(run.get("scope").asText()).isEqualTo("TEAM");
            assertThat(run.get("teamId").asText()).isEqualTo(team);
            assertThat(run.get("organizationScoringRunId").asText()).isEqualTo(orgRun);
            assertThat(run.get("leadershipIntelligenceScore").decimalValue()).isEqualByComparingTo(entry.get("leadershipIntelligenceScore").decimalValue());
        }
        assertThat(jdbc.queryForObject("select count(*) from scoring_run where assessment_id = ?", Integer.class,
                result.get("assessmentId").asText())).isEqualTo(3);
    }

    @Test
    void aTeamBelowTheMinimumHasNoScoreAndOthersAreUnaffected() throws Exception {
        JsonNode result = calculate(request(teams(5, 0, 3)));

        JsonNode tiny = teamRun(result, "tiny");
        assertThat(tiny.get("scoreStatus").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(tiny.has("leadershipIntelligenceScore")).isFalse();
        assertThat(teamRun(result, "alpha").get("scoreStatus").asText()).isEqualTo("VALID");
        // the organization pools all 8 members
        assertThat(result.get("scoreStatus").asText()).isEqualTo("VALID");
        JsonNode tinyRun = getJson("/api/v1/scoring-runs/" + tiny.get("scoringRunId").asText(), admin());
        assertThat(tinyRun.get("responses").get("questions").get(0).get("belowMinimum").asBoolean()).isTrue();
    }

    @Test
    void memberAnswersAreNeverListedOneByOneAndSmallGroupsAreSuppressed() throws Exception {
        JsonNode result = calculate(request(teams(5, 0, 3)));
        String orgRun = result.get("scoringRunId").asText();
        String tinyRun = teamRun(result, "tiny").get("scoringRunId").asText();

        JsonNode org = getJson("/api/v1/scoring-runs/" + orgRun + "/answers", admin()).get("answers");
        JsonNode q01 = null;
        for (JsonNode a : org) {
            if ("Q01".equals(a.get("questionId").asText())) {
                q01 = a;
            }
        }
        assertThat(q01).isNotNull();
        assertThat(q01.get("scope").asText()).isEqualTo("MEMBER");
        assertThat(q01.get("count").asInt()).isEqualTo(8);
        List<Integer> values = new ArrayList<>();
        q01.get("values").forEach(v -> values.add(v.asInt()));
        assertThat(values).isSorted().hasSize(8);
        assertThat(q01.has("teamId")).isFalse();

        JsonNode tiny = getJson("/api/v1/scoring-runs/" + tinyRun + "/answers", admin()).get("answers");
        for (JsonNode a : tiny) {
            if ("MEMBER".equals(a.get("scope").asText())) {
                assertThat(a.get("suppressed").asBoolean()).isTrue();
                assertThat(a.get("count").asInt()).isEqualTo(3);
                assertThat(a.get("values")).isEmpty();
            }
        }
    }

    @Test
    void anOrganizationScoredFromScopedAnswersKeepsHistoryAndLatestAtOrganizationLevel() throws Exception {
        JsonNode result = calculate(request(teams(5, 6, 0)));
        String assessment = result.get("assessmentId").asText();

        JsonNode latest = getJson("/api/v1/assessments/" + assessment + "/score", admin());
        assertThat(latest.get("scoringRunId").asText()).isEqualTo(result.get("scoringRunId").asText());
        assertThat(latest.get("scope").asText()).isEqualTo("ORGANIZATION");

        JsonNode history = getJson("/api/v1/scoring-runs?organizationId=org-1", admin());
        assertThat(history.get("items")).hasSize(1);
        assertThat(history.get("items").get(0).get("scoringRunId").asText()).isEqualTo(result.get("scoringRunId").asText());
        assertThat(getJson("/api/v1/assessments/" + assessment, admin()).get("latestRun").get("scoringRunId").asText())
                .isEqualTo(result.get("scoringRunId").asText());
    }

    @Test
    void scoringAgainReusesTheRunsAndRecalculatingReproducesThem() throws Exception {
        JsonNode result = calculate(request(teams(5, 6, 0)));
        String assessment = result.get("assessmentId").asText();

        JsonNode again = json.readTree(mvc.perform(post("/api/v1/assessments/" + assessment + "/score").with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(again.get("scoringRunId").asText()).isEqualTo(result.get("scoringRunId").asText());
        assertThat(again.get("teamRuns")).hasSize(2);
        assertThat(teamRun(again, "alpha").get("scoringRunId").asText()).isEqualTo(teamRun(result, "alpha").get("scoringRunId").asText());

        JsonNode recalculated = json.readTree(mvc.perform(post("/api/v1/assessments/" + assessment + "/score/recalculate").with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(recalculated.get("scoringRunId").asText()).isNotEqualTo(result.get("scoringRunId").asText());
        assertThat(recalculated.get("leadershipIntelligenceScore").decimalValue())
                .isEqualByComparingTo(result.get("leadershipIntelligenceScore").decimalValue());
        assertThat(recalculated.get("inputHash").asText()).isEqualTo(result.get("inputHash").asText());
        assertThat(recalculated.get("teamRuns")).hasSize(2);
        assertThat(teamRun(recalculated, "beta").get("leadershipIntelligenceScore").decimalValue())
                .isEqualByComparingTo(teamRun(result, "beta").get("leadershipIntelligenceScore").decimalValue());
    }

    @Test
    void theOrderOfTheAnswersDoesNotChangeTheResult() throws Exception {
        ArrayNode forward = teams(5, 6, 0);
        ArrayNode backward = json.createArrayNode();
        for (int i = forward.size() - 1; i >= 0; i--) {
            backward.add(forward.get(i));
        }
        JsonNode first = calculate(request(forward));
        JsonNode second = calculate(request(backward));
        assertThat(second.get("leadershipIntelligenceScore").decimalValue()).isEqualByComparingTo(first.get("leadershipIntelligenceScore").decimalValue());
        assertThat(second.get("inputHash").asText()).isNotEmpty();
        assertThat(jdbc.queryForObject("select count(distinct input_hash) from scoring_run where scope_type = 'ORGANIZATION'", Integer.class)).isEqualTo(2);
        // same answers, same assessment-independent unit hash for a team
        assertThat(jdbc.queryForList("select distinct input_hash from scoring_run where scope_id = 'alpha'", String.class)).hasSize(2);
    }

    @Test
    void mixingScopesForOneQuestionIsRejectedAndNothingIsStored() throws Exception {
        ArrayNode responses = teams(5, 0, 0);
        responses.addObject().put("questionId", "Q01").put("scope", "TEAM").put("teamId", "alpha").put("answer", 3);
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request(responses))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from assessment", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from scoring_run", Integer.class)).isZero();
    }

    @Test
    void invalidScopeFormsAreRejected() throws Exception {
        ObjectNode both = request(teams(5, 0, 0));
        both.putArray("inputs").addObject().put("questionId", "Q01").put("answer", 3);
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(both)))
                .andExpect(status().isBadRequest());

        ArrayNode noTeam = json.createArrayNode();
        noTeam.addObject().put("questionId", "Q01").put("scope", "TEAM").put("answer", 3);
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request(noTeam)))).andExpect(status().isUnprocessableEntity());

        ArrayNode badScope = json.createArrayNode();
        badScope.addObject().put("questionId", "Q01").put("scope", "PERSON").put("answer", 3);
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request(badScope)))).andExpect(status().isBadRequest());

        ArrayNode memberCount = json.createArrayNode();
        memberCount.addObject().put("questionId", "Q01").put("scope", "MEMBER").put("answer", 3).put("respondents", 4);
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request(memberCount)))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void teamFiguresWithADeclaredCountAreCombinedForTheOrganization() throws Exception {
        ArrayNode responses = json.createArrayNode();
        for (String question : List.of("Q01", "Q05")) {
            responses.addObject().put("questionId", question).put("scope", "TEAM").put("teamId", "alpha")
                    .put("answer", question.equals("Q01") ? 4 : 70).put("respondents", 6);
            responses.addObject().put("questionId", question).put("scope", "TEAM").put("teamId", "beta")
                    .put("answer", question.equals("Q01") ? 2 : 50).put("respondents", 7);
        }
        JsonNode result = calculate(request(responses));
        JsonNode q01 = null;
        for (JsonNode q : result.get("responses").get("questions")) {
            if ("Q01".equals(q.get("questionId").asText())) {
                q01 = q;
            }
        }
        assertThat(q01.get("scope").asText()).isEqualTo("TEAM");
        assertThat(q01.get("respondents").asInt()).isEqualTo(13);
        assertThat(q01.get("participation").asText()).isEqualTo("DECLARED");
        assertThat(result.get("teamRuns")).hasSize(2);
    }

    @Test
    void aTeamRunHasItsOwnExplanationButIsNeverComparedOrTrended() throws Exception {
        JsonNode first = calculate(request(teams(5, 6, 0)));
        JsonNode second = calculate(request(teams(5, 6, 0)));
        String team = teamRun(first, "alpha").get("scoringRunId").asText();

        JsonNode explanation = getJson("/api/v1/scoring-runs/" + team + "/explanation", admin());
        assertThat(explanation.get("trace")).isNotNull();
        assertThat(getJson("/api/v1/scoring-runs/" + team + "/catalog", admin())).isNotNull();

        mvc.perform(get("/api/v1/scoring-runs/compare?baseRunId=" + team + "&runId=" + second.get("scoringRunId").asText()).with(admin()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/scoring-runs/compare?baseRunId=" + first.get("scoringRunId").asText() + "&runId="
                + second.get("scoringRunId").asText()).with(admin())).andExpect(status().isOk());

        JsonNode trends = getJson("/api/v1/organizations/org-1/trends", admin());
        assertThat(trends.get("points")).hasSize(2);
        JsonNode dashboard = getJson("/api/v1/organizations/org-1/dashboard", admin());
        assertThat(dashboard.toString()).doesNotContain(team);
    }

    @Test
    void anotherTenantCannotReachATeamRunOrItsAnswers() throws Exception {
        JsonNode result = calculate(request(teams(5, 6, 0)));
        String team = teamRun(result, "alpha").get("scoringRunId").asText();
        mvc.perform(get("/api/v1/scoring-runs/" + team).with(user("sc-b", List.of("org-1"), "ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/scoring-runs/" + team + "/answers").with(user("sc-b", List.of("org-1"), "ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/scoring-runs/" + team).with(user("sc-a", List.of("org-2"), "ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void nothingStoredOrLoggedIdentifiesARespondent(CapturedOutput output) throws Exception {
        ArrayNode responses = json.createArrayNode();
        for (int i = 0; i < 5; i++) {
            add(responses, "Q04", "marker-" + i + "-private", "alpha");
            add(responses, "Q01", 1 + i, "alpha");
        }
        calculate(request(responses));

        // members have an ordinal and a team, nothing else that could point to a person
        assertThat(jdbc.queryForList("select distinct scope_level from assessment_answer", String.class)).containsExactly("MEMBER");
        assertThat(jdbc.queryForObject("select count(*) from assessment_answer where team_id = 'alpha'", Integer.class)).isEqualTo(10);
        String logs = output.getAll().lines().filter(l -> l.startsWith("{\"@timestamp\"")).collect(java.util.stream.Collectors.joining(" "));
        assertThat(logs).doesNotContain("marker-");
        String stored = String.join(" ", jdbc.queryForList("select coalesce(trace::text, '') || coalesce(response_summaries::text, '') from scoring_run", String.class));
        assertThat(stored).doesNotContain("marker-");
    }
}
