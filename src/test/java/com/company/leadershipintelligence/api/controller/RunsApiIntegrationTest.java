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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
 * Roster, assessment runs and the respondent flow (decision D48): the whole journey from a team to a scored organization result, the
 * anonymity of stored answers, pinning of the questionnaire version, and who may reach what.
 */
@AutoConfigureMockMvc
class RunsApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ConfigurationService configurations;

    private String questionnaire;
    private String teamA;
    private String teamB;

    @BeforeEach
    void setUp() throws Exception {
        tenant("qa");
        tenant("qb");
        Caller platform = Caller.platform("ops");
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "platform", ConfigurationSamples.sampleText());
        configurations.submitForValidation(platform, draft.id());
        configurations.activate(platform, draft.id(), new Approval("approver", null));

        questionnaire = body(mvc.perform(post("/api/v1/questionnaires").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pulse\"}"))
                .andExpect(status().isCreated())).get("id").asText();
        mvc.perform(put("/api/v1/questionnaires/" + questionnaire).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(QuestionnaireFixtures.full()))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/questionnaires/" + questionnaire + "/publish").with(admin())).andExpect(status().isOk());

        teamA = createTeam("org-1", "Alpha");
        teamB = createTeam("org-1", "Beta");
    }

    // ---------------- helpers ----------------

    private static RequestPostProcessor user(String tenant, List<String> orgs, String email, String... roles) {
        SimpleGrantedAuthority[] authorities = java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toArray(SimpleGrantedAuthority[]::new);
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("sub-" + (email == null ? tenant : email));
            j.claim("tenant_id", tenant);
            j.claim("org_ids", orgs);
            j.claim("roles", List.of(roles));
            if (email != null) {
                j.claim("email", email);
            }
        }).authorities(authorities);
    }

    private static RequestPostProcessor admin() {
        return user("qa", List.of("org-1"), null, "ADMIN");
    }

    private static RequestPostProcessor respondent(String email) {
        return user("qa", List.of(), email, "RESPONDENT");
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private String createTeam(String organization, String name) throws Exception {
        return body(mvc.perform(post("/api/v1/teams").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationId\":\"" + organization + "\",\"name\":\"" + name + "\"}")).andExpect(status().isCreated())).get("id").asText();
    }

    private JsonNode addPerson(String team, String name, String email) throws Exception {
        return body(mvc.perform(post("/api/v1/people").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + team + "\",\"name\":\"" + name + "\",\"email\":\"" + email + "\"}")).andExpect(status().isCreated()));
    }

    private void people(String team, int count, String prefix) throws Exception {
        for (int i = 1; i <= count; i++) {
            addPerson(team, prefix + " " + i, prefix.toLowerCase() + i + "@acme.test");
        }
    }

    private String startRun(String... teams) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (String t : teams) {
            ids.append(ids.length() == 0 ? "" : ",").append('"').append(t).append('"');
        }
        return body(mvc.perform(post("/api/v1/runs").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Q4 pulse\",\"organizationId\":\"org-1\",\"questionnaireId\":\"" + questionnaire + "\",\"teamIds\":[" + ids + "]}"))
                .andExpect(status().isCreated())).at("/run/id").asText();
    }

    private static final String FULL_ANSWERS = "{\"answers\":[{\"questionId\":\"Q01\",\"answer\":2},{\"questionId\":\"Q02\",\"answer\":\"SOMETIMES\"},"
            + "{\"questionId\":\"Q03\",\"answer\":true},{\"questionId\":\"Q04\",\"answer\":4},{\"questionId\":\"Q05\",\"answer\":[\"A\",\"B\"]},"
            + "{\"questionId\":\"Q06\",\"answer\":\"More coffee please\"}]}";

    private void answerAndSubmit(String email, String run) throws Exception {
        mvc.perform(put("/api/v1/respond/runs/" + run + "/draft").with(respondent(email)).contentType(MediaType.APPLICATION_JSON).content(FULL_ANSWERS))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/respond/runs/" + run + "/submit").with(respondent(email))).andExpect(status().isOk())
                .andExpect(jsonPath("$.myStatus").value("SUBMITTED"));
    }

    // ---------------- roster ----------------

    @Test
    void teamsAndPeopleAreCreatedRenamedAndKeptUnique() throws Exception {
        mvc.perform(get("/api/v1/teams").with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alpha"));
        mvc.perform(post("/api/v1/teams").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"organizationId\":\"org-1\",\"name\":\"ALPHA\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROSTER_CONFLICT"));
        mvc.perform(put("/api/v1/teams/" + teamB).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Beta team\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Beta team"));
        // an organization the token does not carry is refused
        mvc.perform(post("/api/v1/teams").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"organizationId\":\"org-9\",\"name\":\"X\"}"))
                .andExpect(status().isForbidden());

        JsonNode person = addPerson(teamA, "Ada Lovelace", "  Ada@Acme.Test ");
        assertThat(person.get("email").asText()).isEqualTo("ada@acme.test");
        assertThat(person.get("active").asBoolean()).isTrue();
        mvc.perform(post("/api/v1/people").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + teamB + "\",\"name\":\"Other Ada\",\"email\":\"ADA@acme.test\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/people").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + teamA + "\",\"name\":\"No Mail\",\"email\":\"not-an-address\"}")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROSTER_INVALID"));
        mvc.perform(post("/api/v1/people").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"t-nonexistent\",\"name\":\"Lost\",\"email\":\"lost@acme.test\"}")).andExpect(status().isNotFound());

        String id = person.get("id").asText();
        mvc.perform(put("/api/v1/people/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + teamB + "\",\"name\":\"Ada L.\",\"email\":\"ada@acme.test\",\"active\":false}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(teamB)).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/teams").with(admin())).andExpect(jsonPath("$[0].activePeople").value(0));
        mvc.perform(get("/api/v1/people?teamId=" + teamB).with(admin())).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void readingTheRosterIsAuditedWithoutNamesOrAddresses() throws Exception {
        addPerson(teamA, "Ada Lovelace", "ada@acme.test");
        mvc.perform(get("/api/v1/people").with(admin())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from scoring_access_audit where action = 'ROSTER_READ' and tenant_id = 'qa'", Integer.class)).isEqualTo(1);
        assertThat(String.join(" ", jdbc.queryForList("select coalesce(detail::text, '') from scoring_access_audit", String.class)))
                .doesNotContain("Ada").doesNotContain("acme.test");
    }

    @Test
    void theRosterOfOneTenantIsInvisibleToAnother() throws Exception {
        addPerson(teamA, "Ada", "ada@acme.test");
        RequestPostProcessor other = user("qb", List.of("org-1"), null, "ADMIN");
        mvc.perform(get("/api/v1/teams").with(other)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/people").with(other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/v1/people").with(other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + teamA + "\",\"name\":\"Mallory\",\"email\":\"m@x.test\"}")).andExpect(status().isNotFound());
        // the same organization id belongs to the first tenant, so the second cannot put a team in it
        mvc.perform(post("/api/v1/teams").with(other).contentType(MediaType.APPLICATION_JSON).content("{\"organizationId\":\"org-1\",\"name\":\"Mine\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------------- starting a run ----------------

    @Test
    void startingARunPinsTheQuestionnaireAndSnapshotsOnlyActivePeopleOfTheChosenTeams() throws Exception {
        people(teamA, 3, "Alpha");
        JsonNode inactive = addPerson(teamA, "Gone", "gone@acme.test");
        mvc.perform(put("/api/v1/people/" + inactive.get("id").asText()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + teamA + "\",\"name\":\"Gone\",\"email\":\"gone@acme.test\",\"active\":false}")).andExpect(status().isOk());
        people(teamB, 2, "Beta");

        JsonNode run = body(mvc.perform(post("/api/v1/runs").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Q4 pulse\",\"organizationId\":\"org-1\",\"questionnaireId\":\"" + questionnaire + "\",\"teamIds\":[\"" + teamA + "\"]}"))
                .andExpect(status().isCreated()));
        assertThat(run.at("/run/status").asText()).isEqualTo("OPEN");
        assertThat(run.at("/run/questionnaireName").asText()).isEqualTo("Team pulse");
        assertThat(run.at("/run/questionnaireVersion").asInt()).isEqualTo(1);
        assertThat(run.at("/run/participants").asInt()).isEqualTo(3);
        assertThat(run.get("participants")).hasSize(3);
        assertThat(run.get("participants").toString()).doesNotContain("Gone").doesNotContain("Beta");
        assertThat(run.at("/teams/0/notStarted").asInt()).isEqualTo(3);
        assertThat(run.get("notice").asText()).contains("work out who is missing");
        assertThat(jdbc.queryForObject("select count(*) from scoring_configuration_version v join assessment_run r on r.configuration_version_id = v.id "
                + "where v.status = 'ACTIVE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void aRunThatCannotBeStartedIsRefusedWithAReason() throws Exception {
        people(teamA, 2, "Alpha");
        String other = createTeam("org-1", "Gamma");
        // no active people
        mvc.perform(post("/api/v1/runs").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"r\",\"organizationId\":\"org-1\",\"questionnaireId\":\"" + questionnaire + "\",\"teamIds\":[\"" + other + "\"]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("RUN_INVALID"))
                .andExpect(jsonPath("$.message").value("the teams have no active people to ask"));
        // unknown questionnaire, unknown team, past due date, no teams
        mvc.perform(post("/api/v1/runs").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"r\",\"organizationId\":\"org-1\",\"questionnaireId\":\"q-nope\",\"teamIds\":[\"" + teamA + "\"]}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/runs").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"r\",\"organizationId\":\"org-1\",\"questionnaireId\":\"" + questionnaire + "\",\"teamIds\":[\"t-nope\"]}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/runs").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"r\",\"organizationId\":\"org-1\",\"questionnaireId\":\"" + questionnaire + "\",\"teamIds\":[\"" + teamA
                        + "\"],\"dueDate\":\"2020-01-01\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/runs").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"r\",\"organizationId\":\"org-1\",\"questionnaireId\":\"" + questionnaire + "\",\"teamIds\":[]}"))
                .andExpect(status().isBadRequest());
        // an organization the caller does not have
        mvc.perform(post("/api/v1/runs").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"r\",\"organizationId\":\"org-9\",\"questionnaireId\":\"" + questionnaire + "\",\"teamIds\":[\"" + teamA + "\"]}"))
                .andExpect(status().isForbidden());
        // an unpublished and an archived questionnaire
        String draftOnly = body(mvc.perform(post("/api/v1/questionnaires").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"D\"}"))
                .andExpect(status().isCreated())).get("id").asText();
        mvc.perform(post("/api/v1/runs").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"r\",\"organizationId\":\"org-1\",\"questionnaireId\":\"" + draftOnly + "\",\"teamIds\":[\"" + teamA + "\"]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value("the questionnaire has no published version"));
        mvc.perform(post("/api/v1/questionnaires/" + questionnaire + "/archive").with(admin())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/runs").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"r\",\"organizationId\":\"org-1\",\"questionnaireId\":\"" + questionnaire + "\",\"teamIds\":[\"" + teamA + "\"]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value("the questionnaire is archived"));
        assertThat(jdbc.queryForObject("select count(*) from assessment_run", Integer.class)).isZero();
    }

    // ---------------- the respondent ----------------

    @Test
    void aRespondentSeesOnlyTheirOwnRunsAndNothingAboutScoring() throws Exception {
        people(teamA, 2, "Alpha");
        people(teamB, 1, "Beta");
        String runA = startRun(teamA);
        String runB = startRun(teamB);

        mvc.perform(get("/api/v1/respond/runs").with(respondent("alpha1@acme.test"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].runId").value(runA))
                .andExpect(jsonPath("$[0].questionnaireName").value("Team pulse")).andExpect(jsonPath("$[0].myStatus").value("NOT_STARTED"));
        // a run of another team looks like a missing one, and so does anything for an e-mail that is not on the roster
        mvc.perform(get("/api/v1/respond/runs/" + runB).with(respondent("alpha1@acme.test"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/respond/runs").with(respondent("stranger@acme.test"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/respond/runs/" + runA).with(respondent("stranger@acme.test"))).andExpect(status().isNotFound());

        String view = mvc.perform(get("/api/v1/respond/runs/" + runA).with(respondent("alpha1@acme.test"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(6)).andExpect(jsonPath("$.questions[0].min").value(1))
                .andExpect(jsonPath("$.questions[0].max").value(5)).andExpect(jsonPath("$.questions[1].options.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        for (String leak : List.of("dimension", "weight", "score", "desirable", "higherIsBetter", "rules", "penalty", "TEAM_HEALTH")) {
            assertThat(view).as("the respondent view must not contain " + leak).doesNotContain(leak);
        }
    }

    @Test
    void aDraftCanBeSavedResumedAndChangedAndEveryAnswerIsCheckedWhenItIsGiven() throws Exception {
        people(teamA, 1, "Alpha");
        String run = startRun(teamA);
        RequestPostProcessor me = respondent("alpha1@acme.test");

        mvc.perform(put("/api/v1/respond/runs/" + run + "/draft").with(me).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":\"Q01\",\"answer\":4},{\"questionId\":\"Q02\",\"answer\":\"ALWAYS\"}]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.myStatus").value("IN_PROGRESS")).andExpect(jsonPath("$.answers.Q01").value(4))
                .andExpect(jsonPath("$.answers.Q02").value("ALWAYS"));
        // changing one answer and removing another leaves the rest alone
        mvc.perform(put("/api/v1/respond/runs/" + run + "/draft").with(me).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":\"Q01\",\"answer\":5},{\"questionId\":\"Q02\",\"answer\":null}]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.Q01").value(5)).andExpect(jsonPath("$.answers.Q02").doesNotExist());
        // resuming in another session shows the same draft
        mvc.perform(get("/api/v1/respond/runs/" + run).with(me)).andExpect(jsonPath("$.answers.Q01").value(5));

        String[] bad = {
                "{\"questionId\":\"Q01\",\"answer\":6}", "{\"questionId\":\"Q01\",\"answer\":2.5}", "{\"questionId\":\"Q01\",\"answer\":\"3\"}",
                "{\"questionId\":\"Q02\",\"answer\":\"MAYBE\"}", "{\"questionId\":\"Q03\",\"answer\":\"yes\"}", "{\"questionId\":\"Q04\",\"answer\":\"many\"}",
                "{\"questionId\":\"Q05\",\"answer\":\"A\"}", "{\"questionId\":\"Q05\",\"answer\":[\"Z\"]}", "{\"questionId\":\"Q99\",\"answer\":1}"};
        for (String answer : bad) {
            mvc.perform(put("/api/v1/respond/runs/" + run + "/draft").with(me).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"answers\":[" + answer + "]}")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("RUN_INVALID"));
        }
        // one bad answer in a request stops the whole request
        mvc.perform(put("/api/v1/respond/runs/" + run + "/draft").with(me).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":\"Q01\",\"answer\":1},{\"questionId\":\"Q02\",\"answer\":\"MAYBE\"}]}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/respond/runs/" + run).with(me)).andExpect(jsonPath("$.answers.Q01").value(5));
        // the same question twice in one request is a malformed request
        mvc.perform(put("/api/v1/respond/runs/" + run + "/draft").with(me).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":\"Q01\",\"answer\":1},{\"questionId\":\"Q01\",\"answer\":2}]}")).andExpect(status().isBadRequest());
    }

    @Test
    void submittingNeedsEveryRequiredQuestionAndOnlyWorksOnce() throws Exception {
        people(teamA, 1, "Alpha");
        String run = startRun(teamA);
        RequestPostProcessor me = respondent("alpha1@acme.test");
        mvc.perform(post("/api/v1/respond/runs/" + run + "/submit").with(me)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RUN_INVALID")).andExpect(jsonPath("$.details.missing01").value("Q01"));
        assertThat(jdbc.queryForObject("select count(*) from run_response", Integer.class)).isZero();

        answerAndSubmit("alpha1@acme.test", run);
        mvc.perform(post("/api/v1/respond/runs/" + run + "/submit").with(me)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RUN_STATE"));
        mvc.perform(put("/api/v1/respond/runs/" + run + "/draft").with(me).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":\"Q01\",\"answer\":1}]}")).andExpect(status().isConflict());
    }

    @Test
    void submittedAnswersCarryTheTeamAndTheQuestionButNeverThePerson() throws Exception {
        people(teamA, 3, "Alpha");
        String run = startRun(teamA);
        for (int i = 1; i <= 3; i++) {
            answerAndSubmit("alpha" + i + "@acme.test", run);
        }
        // the table cannot say who answered: no participant, person, user or time column exists
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_name = 'run_response' order by column_name", String.class))
                .containsExactly("answer", "id", "question_id", "run_id", "team_id", "tenant_id");
        assertThat(jdbc.queryForObject("select count(*) from run_response", Integer.class)).isEqualTo(18);
        assertThat(jdbc.queryForObject("select count(distinct team_id) from run_response", Integer.class)).isEqualTo(1);
        // the drafts are gone and the completion record says who finished
        assertThat(jdbc.queryForObject("select count(*) from run_draft", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from run_participant where status = 'SUBMITTED'", Integer.class)).isEqualTo(3);
        // no answer value appears anywhere that names a person
        assertThat(String.join(" ", jdbc.queryForList("select coalesce(detail::text, '') from scoring_access_audit", String.class))).doesNotContain("coffee");
        JsonNode detail = body(mvc.perform(get("/api/v1/runs/" + run).with(admin())).andExpect(status().isOk()));
        assertThat(detail.toString()).doesNotContain("coffee").doesNotContain("SOMETIMES");
        assertThat(detail.at("/teams/0/submitted").asInt()).isEqualTo(3);
        assertThat(detail.get("participants")).allSatisfy(p -> assertThat(p.get("status").asText()).isEqualTo("SUBMITTED"));
    }

    // ---------------- closing ----------------

    @Test
    void closingScoresTheOrganizationAndEachTeamAndNeverFreeText() throws Exception {
        people(teamA, 5, "Alpha");
        people(teamB, 2, "Beta");
        String run = startRun(teamA, teamB);
        for (int i = 1; i <= 5; i++) {
            answerAndSubmit("alpha" + i + "@acme.test", run);
        }
        for (int i = 1; i <= 2; i++) {
            answerAndSubmit("beta" + i + "@acme.test", run);
        }
        JsonNode closed = body(mvc.perform(post("/api/v1/runs/" + run + "/close").with(admin())).andExpect(status().isOk()));
        assertThat(closed.at("/run/status").asText()).isEqualTo("CLOSED");
        assertThat(closed.at("/run/scoringRunId").asText()).isNotEmpty();
        assertThat(closed.at("/run/assessmentId").asText()).startsWith("asm-");

        String scoringRun = closed.at("/run/scoringRunId").asText();
        mvc.perform(get("/api/v1/scoring-runs/" + scoringRun).with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.scoreStatus").value("VALID"))
                .andExpect(jsonPath("$.dimensions.TEAM_HEALTH.rawScore").value(25.0))
                .andExpect(jsonPath("$.dimensions.DELIVERY_STABILITY.rawScore").value(50.0));
        // one organization run and a run for each team; only the team with five answers gets a score, the team of two has too few
        assertThat(jdbc.queryForObject("select count(*) from scoring_run where parent_run_id = ?::uuid and scope_type = 'TEAM'", Integer.class, scoringRun)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select score_status from scoring_run where parent_run_id = ?::uuid and scope_id = ?", String.class, scoringRun, teamA))
                .isEqualTo("VALID");
        assertThat(jdbc.queryForObject("select score_status from scoring_run where parent_run_id = ?::uuid and scope_id = ?", String.class, scoringRun, teamB))
                .isEqualTo("INSUFFICIENT_DATA");
        // free text was collected, kept with the run, and never scored
        assertThat(jdbc.queryForObject("select count(*) from assessment_answer where question_id = 'Q06'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from run_response where question_id = 'Q06'", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from assessment_answer where question_id = 'Q01'", Integer.class)).isEqualTo(7);
        // the run was audited and the run list shows it closed
        assertThat(jdbc.queryForObject("select count(*) from scoring_access_audit where action = 'RUN_CLOSED'", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/v1/runs?status=CLOSED").with(admin())).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].submitted").value(7));
        mvc.perform(get("/api/v1/runs?status=OPEN").with(admin())).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aClosedRunCannotBeClosedOrAnsweredAgainAndAnEmptyRunCannotBeClosedAtAll() throws Exception {
        people(teamA, 5, "Alpha");
        String run = startRun(teamA);
        mvc.perform(post("/api/v1/runs/" + run + "/close").with(admin())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RUN_STATE"));
        for (int i = 1; i <= 5; i++) {
            answerAndSubmit("alpha" + i + "@acme.test", run);
        }
        mvc.perform(post("/api/v1/runs/" + run + "/close").with(admin())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/runs/" + run + "/close").with(admin())).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/respond/runs/" + run + "/draft").with(respondent("alpha1@acme.test")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":[{\"questionId\":\"Q01\",\"answer\":1}]}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from scoring_run where scope_type = 'ORGANIZATION'", Integer.class)).isEqualTo(1);
    }

    @Test
    void anOpenRunKeepsTheQuestionnaireVersionItWasStartedWithEvenIfItIsRepublished() throws Exception {
        people(teamA, 5, "Alpha");
        String run = startRun(teamA);
        String pinned = jdbc.queryForObject("select configuration_version_id::text from assessment_run", String.class);

        // republish a different questionnaire under the same id while the run is open
        QuestionnaireDocument full = QuestionnaireFixtures.full();
        mvc.perform(put("/api/v1/questionnaires/" + questionnaire).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new QuestionnaireDocument("Pulse 2", full.description(), full.questions(), List.of())))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/questionnaires/" + questionnaire + "/publish").with(admin())).andExpect(status().isOk());
        // respondents still see version 1 of the questionnaire
        mvc.perform(get("/api/v1/respond/runs/" + run).with(respondent("alpha1@acme.test"))).andExpect(jsonPath("$.questionnaireName").value("Pulse 2"));

        for (int i = 1; i <= 5; i++) {
            answerAndSubmit("alpha" + i + "@acme.test", run);
        }
        JsonNode closed = body(mvc.perform(post("/api/v1/runs/" + run + "/close").with(admin())).andExpect(status().isOk()));
        String scoringRun = closed.at("/run/scoringRunId").asText();
        assertThat(closed.at("/run/questionnaireVersion").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select configuration_selection from scoring_run where id = ?::uuid", String.class, scoringRun)).isEqualTo("EXPLICIT");
        assertThat(jdbc.queryForObject("select layers::text from scoring_run where id = ?::uuid", String.class, scoringRun)).contains(pinned);
        // the pattern of version 1 was evaluated, which version 2 no longer has
        mvc.perform(get("/api/v1/scoring-runs/" + scoringRun).with(admin())).andExpect(jsonPath("$.patterns[0].patternId").value("X-1"));
    }

    // ---------------- who may reach what ----------------

    @Test
    void aRunOfAnotherTenantLooksMissingAndNothingLeaksAcrossTenants() throws Exception {
        people(teamA, 1, "Alpha");
        String run = startRun(teamA);
        RequestPostProcessor other = user("qb", List.of("org-1"), null, "ADMIN");
        mvc.perform(get("/api/v1/runs/" + run).with(other)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
        mvc.perform(post("/api/v1/runs/" + run + "/close").with(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/runs").with(other)).andExpect(jsonPath("$.length()").value(0));
        // a respondent with the same e-mail in another tenant is nobody there
        mvc.perform(get("/api/v1/respond/runs/" + run).with(user("qb", List.of(), "alpha1@acme.test", "RESPONDENT"))).andExpect(status().isNotFound());
        // an administrator whose token lacks the organization does not see the run
        mvc.perform(get("/api/v1/runs/" + run).with(user("qa", List.of("org-2"), null, "ADMIN"))).andExpect(status().isNotFound());
    }

    @Test
    void onlyOwnersAndAdministratorsManageAndOnlyRespondentsRespond() throws Exception {
        people(teamA, 1, "Alpha");
        String run = startRun(teamA);
        for (String role : List.of("ENGINEERING_LEADER", "ANALYST", "VIEWER", "RESPONDENT")) {
            RequestPostProcessor denied = user("qa", List.of("org-1"), "alpha1@acme.test", role);
            for (String path : List.of("/api/v1/teams", "/api/v1/people", "/api/v1/runs", "/api/v1/runs/" + run)) {
                mvc.perform(get(path).with(denied)).andExpect(status().isForbidden());
            }
            mvc.perform(post("/api/v1/runs/" + run + "/close").with(denied)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/teams").with(denied).contentType(MediaType.APPLICATION_JSON).content("{\"organizationId\":\"org-1\",\"name\":\"X\"}"))
                    .andExpect(status().isForbidden());
        }
        // a respondent reaches no tenant data at all, not even what every other role may read
        RequestPostProcessor respondent = respondent("alpha1@acme.test");
        mvc.perform(get("/api/v1/scoring-runs").with(respondent)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/questionnaires").with(respondent)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/calculate").with(respondent).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/respond/runs").with(respondent)).andExpect(status().isOk());
        // and the tenant roles have no part in the respondent flow
        for (String role : List.of("OWNER", "ADMIN", "ENGINEERING_LEADER", "ANALYST", "VIEWER")) {
            mvc.perform(get("/api/v1/respond/runs").with(user("qa", List.of("org-1"), "alpha1@acme.test", role))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/respond/runs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/runs")).andExpect(status().isUnauthorized());
        // a respondent token without an e-mail identifies nobody
        mvc.perform(get("/api/v1/respond/runs").with(user("qa", List.of(), null, "RESPONDENT"))).andExpect(status().isForbidden());
    }

    @Test
    void readingWhoHasNotFinishedIsAudited() throws Exception {
        people(teamA, 2, "Alpha");
        String run = startRun(teamA);
        mvc.perform(get("/api/v1/runs/" + run).with(admin())).andExpect(status().isOk());
        List<String> actions = new ArrayList<>(jdbc.queryForList("select action from scoring_access_audit order by id", String.class));
        assertThat(actions).contains("RUN_STARTED", "RUN_PARTICIPANTS_READ");
        assertThat(String.join(" ", jdbc.queryForList("select coalesce(detail::text, '') from scoring_access_audit", String.class))).doesNotContain("acme.test");
    }
}
