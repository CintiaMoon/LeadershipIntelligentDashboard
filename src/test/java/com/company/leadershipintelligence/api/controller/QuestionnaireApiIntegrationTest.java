package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.company.leadershipintelligence.service.ConfigurationService;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireFixtures;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The questionnaire library (decisions D47, D50): authoring, publishing as a tenant template layer, scoring with it, versions that
 * never change, and tenant isolation. The platform default is the illustrative test sample.
 */
@AutoConfigureMockMvc
class QuestionnaireApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ConfigurationService configurations;

    @BeforeEach
    void platformDefault() {
        tenant("qa");
        tenant("qb");
        Caller platform = Caller.platform("ops");
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "platform", ConfigurationSamples.sampleText());
        configurations.submitForValidation(platform, draft.id());
        configurations.activate(platform, draft.id(), new Approval("approver", null));
    }

    private static RequestPostProcessor user(String tenant, String... roles) {
        SimpleGrantedAuthority[] authorities = java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toArray(SimpleGrantedAuthority[]::new);
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("user-" + tenant);
            j.claim("tenant_id", tenant);
            j.claim("org_ids", List.of("org-1"));
            j.claim("roles", List.of(roles));
        }).authorities(authorities);
    }

    private RequestPostProcessor admin() {
        return user("qa", "ADMIN");
    }

    private JsonNode create(String name) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/questionnaires").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode save(String id, QuestionnaireDocument document) throws Exception {
        return json.readTree(mvc.perform(put("/api/v1/questionnaires/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(document))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode publish(String id) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/questionnaires/" + id + "/publish").with(admin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String publishedFull() throws Exception {
        String id = create("Team pulse").get("id").asText();
        save(id, QuestionnaireFixtures.full());
        publish(id);
        return id;
    }

    // ---------------- authoring ----------------

    @Test
    void anEmptyQuestionnaireIsCreatedAsADraftThatListsWhatBlocksPublishing() throws Exception {
        JsonNode created = create("  Quarterly pulse  ");
        assertThat(created.get("id").asText()).startsWith("q-");
        assertThat(created.get("name").asText()).isEqualTo("Quarterly pulse");
        assertThat(created.get("state").asText()).isEqualTo("DRAFT");
        assertThat(created.get("version").asInt()).isEqualTo(1);
        assertThat(created.get("versionStatus").asText()).isEqualTo("DRAFT");
        List<String> problems = new ArrayList<>();
        created.get("problems").forEach(p -> problems.add(p.asText()));
        assertThat(problems).contains("the questionnaire has no questions", "dimension TEAM_HEALTH has no scored question");
    }

    @Test
    void savingAssignsIdentifiersKeepsTheDraftIncompleteAndReportsTheRemainingProblems() throws Exception {
        String id = create("Pulse").get("id").asText();
        ObjectNode body = json.createObjectNode();
        body.put("name", "Pulse v2");
        ArrayNode questions = body.putArray("questions");
        questions.addObject().put("kind", "RATING").put("text", "How is morale?").put("dimension", "TEAM_HEALTH");
        ObjectNode choice = questions.addObject().put("kind", "SINGLE_CHOICE").put("text", "How often do releases slip?");
        choice.putArray("options").addObject().put("label", "Never");
        choice.withArray("options").addObject().put("label", "Often");

        JsonNode saved = json.readTree(mvc.perform(put("/api/v1/questionnaires/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(saved.get("name").asText()).isEqualTo("Pulse v2");
        assertThat(saved.at("/document/questions/0/id").asText()).isEqualTo("Q01");
        assertThat(saved.at("/document/questions/1/id").asText()).isEqualTo("Q02");
        assertThat(saved.at("/document/questions/1/options/0/key").asText()).isEqualTo("O1");
        assertThat(saved.at("/document/questions/1/options/1/key").asText()).isEqualTo("O2");
        assertThat(saved.get("problems").toString()).contains("dimension DELIVERY_STABILITY has no scored question");
        // the library shows the new name and still one draft
        mvc.perform(get("/api/v1/questionnaires").with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Pulse v2")).andExpect(jsonPath("$[0].draftVersion").value(1))
                .andExpect(jsonPath("$[0].publishedVersion").doesNotExist());
    }

    @Test
    void ashapeThatCanNeverBePublishedIsRefusedEvenAsADraftAndUnknownFieldsAreRejected() throws Exception {
        String id = create("Pulse").get("id").asText();
        mvc.perform(put("/api/v1/questionnaires/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"n\",\"questions\":[{\"id\":\"Q1\",\"kind\":\"RATING\"},{\"id\":\"Q1\",\"kind\":\"RATING\"}]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("QUESTIONNAIRE_INVALID"))
                .andExpect(jsonPath("$.details.problem01").value("question id Q1 is used twice"));
        mvc.perform(put("/api/v1/questionnaires/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"n\",\"questions\":[{\"kind\":\"RATING\",\"dimention\":\"TEAM_HEALTH\"}]}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/questionnaires/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"n\",\"questions\":[{\"kind\":\"NOT_A_KIND\"}]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/questionnaires").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/questionnaires").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"n\",\"tenantId\":\"qb\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------- publishing ----------------

    @Test
    void publishingActivatesATemplateLayerOfTheTenantUnderTheQuestionnaireId() throws Exception {
        String id = create("Team pulse").get("id").asText();
        save(id, QuestionnaireFixtures.full());
        JsonNode published = publish(id);

        assertThat(published.get("state").asText()).isEqualTo("PUBLISHED");
        assertThat(published.get("version").asInt()).isEqualTo(1);
        assertThat(published.get("versionStatus").asText()).isEqualTo("PUBLISHED");
        assertThat(published.at("/versions/0/publishedBy").asText()).isEqualTo("user-qa");
        assertThat(jdbc.queryForObject("select count(*) from scoring_configuration c join scoring_configuration_version v on v.configuration_id = c.id "
                + "where c.tenant_id = 'qa' and c.scope_type = 'TEMPLATE' and c.scope_id = ? and v.status = 'ACTIVE'", Integer.class, id)).isEqualTo(1);
        // the id is also the template id, registered at the published version
        assertThat(jdbc.queryForObject("select count(*) from assessment_template_version where template_id = ? and version = 1 and tenant_id = 'qa'",
                Integer.class, id)).isEqualTo(1);
        assertThat(published.at("/versions/0/configurationVersionId").asText()).isNotEmpty();
    }

    @Test
    void anAssessmentOfThePublishedQuestionnaireIsScoredWithItsQuestionsAndRules() throws Exception {
        String id = publishedFull();
        // Q01 = 2 is 25; Q02 SOMETIMES is 50; Q03 true with NO desirable is 0; Q04 = 4 with lower better is 60; Q05 is collected only
        String request = "{\"assessment\":{\"organizationId\":\"org-1\",\"templateId\":\"" + id + "\",\"templateVersion\":\"1\",\"status\":\"COMPLETED\","
                + "\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":[{\"questionId\":\"Q01\",\"answer\":2},{\"questionId\":\"Q02\",\"answer\":\"SOMETIMES\"},"
                + "{\"questionId\":\"Q03\",\"answer\":true},{\"questionId\":\"Q04\",\"answer\":4},{\"questionId\":\"Q05\",\"answer\":[\"A\"]}]}";
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreStatus").value("VALID"))
                .andExpect(jsonPath("$.dimensions.TEAM_HEALTH.rawScore").value(25.0))
                .andExpect(jsonPath("$.dimensions.DELIVERY_STABILITY.rawScore").value(50.0))
                .andExpect(jsonPath("$.dimensions.TECHNICAL_RISK.rawScore").value(0.0))
                .andExpect(jsonPath("$.dimensions.LEADERSHIP_LOAD.rawScore").value(60.0))
                .andExpect(jsonPath("$.patterns[0].patternId").value("X-1"))
                .andExpect(jsonPath("$.patterns[0].status").value("MATCHED"))
                .andExpect(jsonPath("$.penalties[0].penaltyId").value("X-P1"))
                .andExpect(jsonPath("$.penalties[0].status").value("APPLIED"));
    }

    @Test
    void theQuestionnaireShowsItsOwnTextAndNotThePlatformsInTheCatalogOfARun() throws Exception {
        String id = publishedFull();
        String request = "{\"assessment\":{\"organizationId\":\"org-1\",\"templateId\":\"" + id + "\",\"templateVersion\":\"1\",\"status\":\"COMPLETED\","
                + "\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":[{\"questionId\":\"Q01\",\"answer\":2},{\"questionId\":\"Q02\",\"answer\":\"SOMETIMES\"},"
                + "{\"questionId\":\"Q03\",\"answer\":true},{\"questionId\":\"Q04\",\"answer\":4}]}";
        JsonNode result = json.readTree(mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String run = result.get("scoringRunId").asText();
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/catalog").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.patterns['X-1'].name").value("Burnout and incidents"))
                .andExpect(jsonPath("$.questions['Q01'].description").value("How is Q01?"));
    }

    @Test
    void aDraftThatCannotBePublishedIsRefusedWithEveryProblemAndLeavesNoConfigurationBehind() throws Exception {
        String id = create("Pulse").get("id").asText();
        save(id, QuestionnaireFixtures.without(QuestionnaireFixtures.without(QuestionnaireFixtures.full(), "Q03"), "Q04"));
        // the rule refers to Q03, which is gone, and two dimensions have no question
        mvc.perform(post("/api/v1/questionnaires/" + id + "/publish").with(admin())).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUESTIONNAIRE_INVALID"))
                .andExpect(jsonPath("$.details.problem01").value("dimension TECHNICAL_RISK has no scored question"))
                .andExpect(jsonPath("$.details.problem02").value("dimension LEADERSHIP_LOAD has no scored question"))
                .andExpect(jsonPath("$.details.problem03").value("rule X-1 refers to an unknown question Q03"));
        assertThat(jdbc.queryForObject("select count(*) from scoring_configuration where scope_type = 'TEMPLATE'", Integer.class)).isZero();
        mvc.perform(get("/api/v1/questionnaires/" + id).with(admin())).andExpect(jsonPath("$.state").value("DRAFT"));
    }

    @Test
    void aLayerTheConfigurationParserRejectsIsRefusedAndRolledBack() throws Exception {
        // a CAPPED_STACK cap that differs between two penalties on one target passes the builder checks but not the engine's
        String id = create("Pulse").get("id").asText();
        QuestionnaireDocument full = QuestionnaireFixtures.full();
        QuestionnaireDocument.PenaltySpec other = new QuestionnaireDocument.PenaltySpec("TEAM_HEALTH", "FIXED", QuestionnaireFixtures.n("5"),
                "CAPPED_STACK", QuestionnaireFixtures.n("30"), null, null);
        List<QuestionnaireDocument.RuleSpec> rules = new ArrayList<>(full.rules());
        rules.add(new QuestionnaireDocument.RuleSpec(null, "Second", "Second is happening.", null, "HIGH", null,
                QuestionnaireFixtures.leq("Q01", "50"), 1, null, other, null));
        save(id, new QuestionnaireDocument(full.name(), null, full.questions(), rules));
        mvc.perform(post("/api/v1/questionnaires/" + id + "/publish").with(admin())).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUESTIONNAIRE_INVALID"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("share one cumulative cap")));
        assertThat(jdbc.queryForObject("select count(*) from scoring_configuration where scope_type = 'TEMPLATE'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from questionnaire_version where status <> 'DRAFT'", Integer.class)).isZero();
    }

    // ---------------- versions ----------------

    @Test
    void editingAPublishedQuestionnaireOpensANewDraftAndPublishingItSupersedesTheOldVersion() throws Exception {
        String id = publishedFull();
        String firstLayer = jdbc.queryForObject("select id::text from scoring_configuration_version where status = 'ACTIVE' and tenant_id = 'qa'", String.class);

        QuestionnaireDocument full = QuestionnaireFixtures.full();
        JsonNode editing = save(id, new QuestionnaireDocument("Team pulse 2", full.description(), full.questions(), List.of()));
        assertThat(editing.get("state").asText()).isEqualTo("PUBLISHED_WITH_DRAFT");
        assertThat(editing.get("version").asInt()).isEqualTo(2);
        assertThat(editing.get("versionStatus").asText()).isEqualTo("DRAFT");
        // version 1 is still what is active and what a version query returns
        assertThat(jdbc.queryForObject("select count(*) from scoring_configuration_version where status = 'ACTIVE' and tenant_id = 'qa'", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/v1/questionnaires/" + id + "?version=1").with(admin())).andExpect(jsonPath("$.versionStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.document.rules.length()").value(1));

        JsonNode second = publish(id);
        assertThat(second.get("state").asText()).isEqualTo("PUBLISHED");
        assertThat(second.get("version").asInt()).isEqualTo(2);
        assertThat(second.at("/versions/0/status").asText()).isEqualTo("PUBLISHED");
        assertThat(second.at("/versions/1/status").asText()).isEqualTo("SUPERSEDED");
        assertThat(jdbc.queryForObject("select status from scoring_configuration_version where id = ?::uuid", String.class, firstLayer)).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("select count(*) from scoring_configuration_version where status = 'ACTIVE' and tenant_id = 'qa'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select name from assessment_template where id = ?", String.class, id)).isEqualTo("Team pulse 2");
    }

    @Test
    void aPublishedVersionCannotBeChangedOrDeletedEvenWithDirectSql() throws Exception {
        String id = publishedFull();
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("update questionnaire_version set definition = '{}'::jsonb where questionnaire_id = ?", id));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("delete from questionnaire_version where questionnaire_id = ?", id));
    }

    @Test
    void publishingWithoutAnOpenDraftIsAConflict() throws Exception {
        String id = publishedFull();
        mvc.perform(post("/api/v1/questionnaires/" + id + "/publish").with(admin())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTIONNAIRE_STATE"));
    }

    // ---------------- archive and duplicate ----------------

    @Test
    void archivingHidesTheQuestionnaireFromChangeButKeepsItsLayerForPastAssessments() throws Exception {
        String id = publishedFull();
        mvc.perform(post("/api/v1/questionnaires/" + id + "/archive").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ARCHIVED")).andExpect(jsonPath("$.archived").value(true));
        mvc.perform(put("/api/v1/questionnaires/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(QuestionnaireFixtures.full()))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTIONNAIRE_STATE"));
        mvc.perform(post("/api/v1/questionnaires/" + id + "/publish").with(admin())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from scoring_configuration_version where status = 'ACTIVE' and tenant_id = 'qa'", Integer.class)).isEqualTo(1);
        // archiving twice changes nothing
        mvc.perform(post("/api/v1/questionnaires/" + id + "/archive").with(admin())).andExpect(status().isOk());
    }

    @Test
    void duplicatingCopiesTheCurrentContentAsAnIndependentFirstDraft() throws Exception {
        String id = publishedFull();
        JsonNode copy = json.readTree(mvc.perform(post("/api/v1/questionnaires/" + id + "/duplicate").with(admin()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(copy.get("id").asText()).isNotEqualTo(id).startsWith("q-");
        assertThat(copy.get("name").asText()).isEqualTo("Team pulse (copy)");
        assertThat(copy.get("state").asText()).isEqualTo("DRAFT");
        assertThat(copy.get("version").asInt()).isEqualTo(1);
        assertThat(copy.at("/document/questions").size()).isEqualTo(6);
        assertThat(copy.get("problems")).isEmpty();
    }

    @Test
    void aCopyOfThePlatformDefaultIsADraftWithNotesAndCanBePublished() throws Exception {
        JsonNode copy = json.readTree(mvc.perform(post("/api/v1/questionnaires/from-initial").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Our questionnaire\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(copy.get("name").asText()).isEqualTo("Our questionnaire");
        assertThat(copy.get("state").asText()).isEqualTo("DRAFT");
        assertThat(copy.at("/document/questions").size()).isGreaterThanOrEqualTo(4);
        assertThat(copy.get("notes").toString()).contains("free text");
        assertThat(copy.get("problems")).isEmpty();
        // it is only a draft until someone publishes it; publishing the copy works like any other questionnaire
        assertThat(jdbc.queryForObject("select count(*) from scoring_configuration where scope_type = 'TEMPLATE'", Integer.class)).isZero();
        publish(copy.get("id").asText());
        assertThat(jdbc.queryForObject("select count(*) from scoring_configuration where scope_type = 'TEMPLATE' and tenant_id = 'qa'", Integer.class)).isEqualTo(1);
        // the notes are shown once, when the copy is made
        mvc.perform(get("/api/v1/questionnaires/" + copy.get("id").asText()).with(admin())).andExpect(jsonPath("$.notes.length()").value(0));
    }

    // ---------------- tenancy and roles ----------------

    @Test
    void aQuestionnaireOfAnotherTenantLooksMissingAndIsNeverListed() throws Exception {
        String id = publishedFull();
        RequestPostProcessor other = user("qb", "ADMIN");
        mvc.perform(get("/api/v1/questionnaires/" + id).with(other)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTIONNAIRE_NOT_FOUND"));
        mvc.perform(put("/api/v1/questionnaires/" + id).with(other).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(QuestionnaireFixtures.full()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/questionnaires/" + id + "/publish").with(other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/questionnaires/" + id + "/archive").with(other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/questionnaires/" + id + "/duplicate").with(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/questionnaires").with(other)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/questionnaires").with(admin())).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void thePublishedLayerOfOneTenantDoesNotApplyToAnother() throws Exception {
        String id = publishedFull();
        String request = "{\"assessment\":{\"organizationId\":\"org-1\",\"templateId\":\"" + id + "\",\"templateVersion\":\"1\",\"status\":\"COMPLETED\","
                + "\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":[{\"questionId\":\"Q02\",\"answer\":\"SOMETIMES\"}]}";
        // the other tenant has no layer under this template id, so only the platform default applies to it
        mvc.perform(post("/api/v1/calculate").with(user("qb", "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configurationVersion").value("platform_default:v1"))
                .andExpect(jsonPath("$.patterns[?(@.patternId == 'X-1')]").isEmpty());
    }

    @Test
    void onlyOwnersAndAdministratorsReachTheLibrary() throws Exception {
        String id = publishedFull();
        for (String role : List.of("ENGINEERING_LEADER", "ANALYST", "VIEWER")) {
            RequestPostProcessor denied = user("qa", role);
            mvc.perform(get("/api/v1/questionnaires").with(denied)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/questionnaires/" + id).with(denied)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/questionnaires").with(denied).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"n\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/questionnaires/" + id).with(denied).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/questionnaires/" + id + "/publish").with(denied)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/questionnaires").with(user("qa", "OWNER"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/questionnaires")).andExpect(status().isUnauthorized());
        // the platform administrator belongs to no tenant and reaches no tenant data
        RequestPostProcessor platform = SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("platform-admin-user");
            j.claim("roles", List.of("PLATFORM_ADMIN"));
        }).authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
        mvc.perform(get("/api/v1/questionnaires").with(platform)).andExpect(status().isForbidden());
    }
}
