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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A question whose answer is a list of options (specv3.md Section 4.2.1, decision D49), through the real API: scored as the
 * mean of the chosen options, stored as a sorted set, and combined across members like every other type.
 */
@AutoConfigureMockMvc
class MultiSelectApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ConfigurationService configurations;

    @BeforeEach
    void platformDefaultWithAMultiSelectQuestion() throws Exception {
        tenant("ms-a");
        ObjectNode document = ConfigurationSamples.sampleDocument();
        ObjectNode question = document.withArray("questions").addObject();
        question.put("id", "Q06").put("type", "MULTI_SELECT").put("missingPolicy", "IGNORE");
        ObjectNode normalization = question.putObject("normalization");
        normalization.putObject("values").put("MONITORING", 100).put("ON_CALL", 50).put("RUNBOOKS", 80).put("NONE", 0);
        normalization.put("unknownOption", "REJECT");
        ((ObjectNode) document.get("dimensions")).withArray("mappings").addObject()
                .put("question", "Q06").put("dimension", "TEAM_HEALTH").put("weight", 1.0);
        Caller platform = Caller.platform("ops");
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "platform", json.writeValueAsString(document));
        configurations.submitForValidation(platform, draft.id());
        configurations.activate(platform, draft.id(), new Approval("approver", null));
    }

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("user-1");
            j.claim("tenant_id", "ms-a");
            j.claim("org_ids", List.of("org-1"));
            j.claim("roles", List.of("ADMIN"));
        }).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private ObjectNode request() {
        ObjectNode body = json.createObjectNode();
        body.putObject("assessment").put("organizationId", "org-1").put("templateId", "tpl-1").put("templateVersion", "1")
                .put("status", "COMPLETED").put("completedAt", "2026-11-01T09:00:00Z");
        return body;
    }

    private void others(ArrayNode inputs, int sustainability) {
        inputs.addObject().put("questionId", "Q01").put("answer", sustainability);
        inputs.addObject().put("questionId", "Q02").put("answer", "OFTEN");
        inputs.addObject().put("questionId", "Q03").put("answer", true);
        inputs.addObject().put("questionId", "Q04").put("answer", "fine");
        inputs.addObject().put("questionId", "Q05").put("answer", 60);
    }

    private ObjectNode withSelection(Object selection) {
        ObjectNode body = request();
        ArrayNode inputs = body.putArray("inputs");
        others(inputs, 2);
        ObjectNode q6 = inputs.addObject().put("questionId", "Q06");
        if (selection instanceof List<?> list) {
            ArrayNode array = q6.putArray("answer");
            list.forEach(o -> array.add(String.valueOf(o)));
        } else {
            q6.put("answer", String.valueOf(selection));
        }
        return body;
    }

    private JsonNode calculate(ObjectNode body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void expect(ObjectNode body, int expected) throws Exception {
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expected));
    }

    private static String teamHealth(JsonNode result) {
        return result.get("dimensions").get("TEAM_HEALTH").get("rawScore").decimalValue().toPlainString();
    }

    @Test
    void theChosenOptionsAreScoredAsTheirMeanTogetherWithTheOtherQuestionsOfTheDimension() throws Exception {
        // Q01 = 2 is 25; MONITORING (100) and ON_CALL (50) average 75; the dimension averages 25 and 75
        JsonNode result = calculate(withSelection(List.of("MONITORING", "ON_CALL")));
        assertThat(result.get("scoreStatus").asText()).isEqualTo("VALID");
        assertThat(new java.math.BigDecimal(teamHealth(result))).isEqualByComparingTo("50");
    }

    @Test
    void theOrderOfTheOptionsAndARepeatedOptionChangeNothing() throws Exception {
        JsonNode forward = calculate(withSelection(List.of("MONITORING", "ON_CALL")));
        JsonNode reversed = calculate(withSelection(List.of("ON_CALL", "MONITORING", "ON_CALL")));
        assertThat(teamHealth(reversed)).isEqualTo(teamHealth(forward));
        assertThat(reversed.get("leadershipIntelligenceScore").decimalValue()).isEqualByComparingTo(forward.get("leadershipIntelligenceScore").decimalValue());
    }

    @Test
    void anEmptySelectionIsMissingDataSoCoverageDecidesAndNeverAZeroScore() throws Exception {
        // the dimension has two questions; one is now missing, so coverage is 50 percent, below the required 75 percent
        JsonNode result = calculate(withSelection(List.of()));
        assertThat(result.get("scoreStatus").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.get("dimensions").get("TEAM_HEALTH").get("status").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.get("dimensions").get("TEAM_HEALTH").has("rawScore")).isFalse();
        assertThat(result.has("leadershipIntelligenceScore")).isFalse();
    }

    @Test
    void aSelectionIsStoredSortedAndReadBackAsAList() throws Exception {
        JsonNode result = calculate(withSelection(List.of("ON_CALL", "MONITORING")));
        JsonNode answers = json.readTree(mvc.perform(get("/api/v1/scoring-runs/" + result.get("scoringRunId").asText() + "/answers").with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("answers");
        JsonNode q06 = null;
        for (JsonNode a : answers) {
            if ("Q06".equals(a.get("questionId").asText())) {
                q06 = a;
            }
        }
        assertThat(q06).isNotNull();
        assertThat(q06.get("answer").isArray()).isTrue();
        assertThat(q06.get("answer")).extracting(JsonNode::asText).containsExactly("MONITORING", "ON_CALL");
        assertThat(jdbc.queryForObject("select answer::text from assessment_answer where question_id = 'Q06'", String.class))
                .isEqualTo("[\"MONITORING\", \"ON_CALL\"]");
    }

    @Test
    void anythingThatIsNotAListOfKnownOptionsIsRefusedAndNothingIsStored() throws Exception {
        expect(withSelection("MONITORING"), 422);
        expect(withSelection(List.of("TELEPORT")), 422);
        expect(withSelection(List.of("MONITORING", "TELEPORT")), 422);
        assertThat(jdbc.queryForObject("select count(*) from assessment", Integer.class)).isZero();

        ObjectNode numbers = request();
        ArrayNode inputs = numbers.putArray("inputs");
        others(inputs, 2);
        inputs.addObject().put("questionId", "Q06").putArray("answer").add(3);
        expect(numbers, 422);
    }

    @Test
    void aSelectionLongerThanTheLimitIsARequestError() throws Exception {
        List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            many.add("option-" + i);
        }
        expect(withSelection(many), 400);
    }

    @Test
    void aDeclaredQuestionTypeIsCheckedAgainstTheConfiguredOne() throws Exception {
        ObjectNode body = withSelection(List.of("RUNBOOKS"));
        ((ObjectNode) body.get("inputs").get(5)).put("questionType", "MULTI_SELECT");
        expect(body, 200);
        ((ObjectNode) body.get("inputs").get(5)).put("questionType", "MULTIPLE_CHOICE");
        expect(body, 422);
    }

    @Test
    void membersWhoEachChooseOptionsAreCombinedPerQuestionAndListedOnlyAsAGroup() throws Exception {
        ObjectNode body = request();
        ArrayNode responses = body.putArray("responses");
        List<List<String>> choices = List.of(List.of("MONITORING"), List.of("MONITORING", "ON_CALL"), List.of("ON_CALL"),
                List.of("RUNBOOKS", "MONITORING"), List.of("NONE"));
        for (int member = 0; member < 5; member++) {
            for (String[] answer : new String[][]{{"Q01", "3"}, {"Q02", "OFTEN"}, {"Q03", "true"}, {"Q04", "fine"}, {"Q05", "60"}}) {
                ObjectNode node = responses.addObject().put("questionId", answer[0]).put("scope", "MEMBER").put("teamId", "alpha");
                switch (answer[0]) {
                    case "Q01", "Q05" -> node.put("answer", Integer.parseInt(answer[1]));
                    case "Q03" -> node.put("answer", true);
                    default -> node.put("answer", answer[1]);
                }
            }
            ObjectNode q6 = responses.addObject().put("questionId", "Q06").put("scope", "MEMBER").put("teamId", "alpha");
            ArrayNode array = q6.putArray("answer");
            choices.get(member).forEach(array::add);
        }
        JsonNode result = calculate(body);
        assertThat(result.get("scoreStatus").asText()).isEqualTo("VALID");

        JsonNode q6Summary = null;
        for (JsonNode q : result.get("responses").get("questions")) {
            if ("Q06".equals(q.get("questionId").asText())) {
                q6Summary = q;
            }
        }
        assertThat(q6Summary.get("respondents").asInt()).isEqualTo(5);

        // member values per member: 100, 75, 50, 90, 0 -> median 75; with Q01 = 3 (50) the dimension is 62.5
        assertThat(new java.math.BigDecimal(teamHealth(result))).isEqualByComparingTo("62.5");

        JsonNode answers = json.readTree(mvc.perform(get("/api/v1/scoring-runs/" + result.get("scoringRunId").asText() + "/answers").with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("answers");
        JsonNode group = null;
        for (JsonNode a : answers) {
            if ("Q06".equals(a.get("questionId").asText())) {
                group = a;
            }
        }
        assertThat(group.get("scope").asText()).isEqualTo("MEMBER");
        assertThat(group.get("count").asInt()).isEqualTo(5);
        assertThat(group.get("values")).hasSize(5);
        group.get("values").forEach(v -> assertThat(v.isArray()).isTrue());
    }
}
