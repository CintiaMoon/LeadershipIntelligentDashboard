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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The configuration management endpoints (decisions D52 to D58): who may reach what, the approval journey, the gates and isolation. */
@AutoConfigureMockMvc
class ConfigurationApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ConfigurationService configurations;

    private static final String WEIGHTS = "{\"overall\":{\"weights\":{\"TEAM_HEALTH\":0.7,\"DELIVERY_STABILITY\":0.1,\"TECHNICAL_RISK\":0.1,\"LEADERSHIP_LOAD\":0.1}}}";
    private static final String ANSWERS = "[{\"questionId\":\"Q01\",\"answer\":2},{\"questionId\":\"Q02\",\"answer\":\"OFTEN\"},{\"questionId\":\"Q03\",\"answer\":true},"
            + "{\"questionId\":\"Q04\",\"answer\":\"only one key person\"},{\"questionId\":\"Q05\",\"answer\":80}]";

    @BeforeEach
    void platformDefault() {
        tenant("ca-a");
        tenant("ca-b");
        Caller ops = Caller.platform("ops");
        ConfigurationVersionView draft = configurations.createDraft(ops, ConfigurationTarget.platform(), "platform", ConfigurationSamples.sampleText());
        configurations.submitForValidation(ops, draft.id());
        configurations.activate(ops, draft.id(), new Approval("ops-approver", null));
    }

    private static RequestPostProcessor user(String tenant, String subject, String... roles) {
        SimpleGrantedAuthority[] authorities = java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toArray(SimpleGrantedAuthority[]::new);
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject(subject);
            j.claim("tenant_id", tenant);
            j.claim("org_ids", List.of("org-1"));
            j.claim("roles", List.of(roles));
        }).authorities(authorities);
    }

    private static RequestPostProcessor platform(String subject) {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject(subject);
            j.claim("roles", List.of("PLATFORM_ADMIN"));
        }).authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }

    private static final RequestPostProcessor ADMIN = user("ca-a", "admin-1", "ADMIN");
    private static final RequestPostProcessor OWNER = user("ca-a", "owner-1", "OWNER");

    private JsonNode body(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private String createTenantDraft(RequestPostProcessor who, String document) throws Exception {
        return body(mvc.perform(post("/api/v1/configuration/versions").with(who).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"TENANT\"" + (document == null ? "" : ",\"document\":" + document) + "}")).andExpect(status().isCreated())).get("id").asText();
    }

    private JsonNode postJson(String path, RequestPostProcessor who, String content) throws Exception {
        return body(mvc.perform(post(path).with(who).contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isOk()));
    }

    // ---------------- who may reach what ----------------

    @Test
    void onlyOwnersAndAdministratorsChangeAndAnalystsMayRead() throws Exception {
        String id = createTenantDraft(ADMIN, "{}");
        for (String role : List.of("ENGINEERING_LEADER", "VIEWER", "RESPONDENT")) {
            RequestPostProcessor denied = user("ca-a", "x-" + role, role);
            mvc.perform(get("/api/v1/configuration/layers").with(denied)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/configuration/versions/" + id).with(denied)).andExpect(status().isForbidden());
        }
        RequestPostProcessor analyst = user("ca-a", "analyst-1", "ANALYST");
        mvc.perform(get("/api/v1/configuration/layers").with(analyst)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/configuration/versions/" + id).with(analyst)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/configuration/versions/" + id + "/submit").with(analyst)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/configuration/versions/" + id + "/preview").with(analyst)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/configuration/versions").with(analyst).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/configuration/layers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/configuration/layers").with(platform("pa-1"))).andExpect(status().isForbidden());
    }

    @Test
    void thePlatformPathIsForThePlatformAdministratorOnly() throws Exception {
        for (String role : List.of("OWNER", "ADMIN", "ANALYST", "VIEWER")) {
            mvc.perform(get("/api/v1/platform/configuration/layers").with(user("ca-a", "u-" + role, role))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/platform/configuration/layers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/platform/configuration/layers").with(platform("pa-1"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Platform default"))
                .andExpect(jsonPath("$[0].active.version").value(1)).andExpect(jsonPath("$[0].active.unvalidated").value(true));
    }

    // ---------------- the journey over HTTP ----------------

    @Test
    void anAdministratorDraftsAnOwnerApprovesAndScoringThenUsesTheNewVersion() throws Exception {
        String id = createTenantDraft(ADMIN, WEIGHTS);
        mvc.perform(get("/api/v1/configuration/versions/" + id).with(ADMIN)).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.unvalidated").value(true)).andExpect(jsonPath("$.review").doesNotExist());
        assertThat(postJson("/api/v1/configuration/versions/" + id + "/validate", ADMIN, "").get("valid").asBoolean()).isTrue();

        JsonNode submitted = postJson("/api/v1/configuration/versions/" + id + "/submit", ADMIN, "");
        assertThat(submitted.get("submittedBy").asText()).isEqualTo("admin-1");
        assertThat(submitted.get("validationProblems")).isEmpty();

        // the author is refused, the note is required and unknown fields are rejected
        mvc.perform(post("/api/v1/configuration/versions/" + id + "/approve").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"mine\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONFIGURATION_APPROVAL_REFUSED"));
        mvc.perform(post("/api/v1/configuration/versions/" + id + "/approve").with(OWNER).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/configuration/versions/" + id + "/approve").with(OWNER).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ok\",\"approvedBy\":\"x\"}"))
                .andExpect(status().isBadRequest());

        JsonNode approved = postJson("/api/v1/configuration/versions/" + id + "/approve", OWNER,
                "{\"note\":\"Our teams weigh health most.\",\"calibrationReference\":\"Pilot Q3 2026\",\"knownLimitations\":\"12 teams\"}");
        assertThat(approved.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(approved.get("unvalidated").asBoolean()).isFalse();
        assertThat(approved.at("/review/approvedBy").asText()).isEqualTo("owner-1");
        assertThat(approved.at("/review/calibrationReference").asText()).isEqualTo("Pilot Q3 2026");

        // the layer lists it as active, and an assessment is now scored with the tenant layer
        mvc.perform(get("/api/v1/configuration/layers").with(ADMIN)).andExpect(jsonPath("$[0].name").value("Tenant override"))
                .andExpect(jsonPath("$[0].active.version").value(1)).andExpect(jsonPath("$[0].active.unvalidated").value(false));
        String request = "{\"assessment\":{\"organizationId\":\"org-1\",\"templateId\":\"tpl-1\",\"templateVersion\":\"1\",\"status\":\"COMPLETED\","
                + "\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":" + ANSWERS + "}";
        mvc.perform(post("/api/v1/calculate").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk())
                .andExpect(jsonPath("$.configurationVersion").value("platform_default:v1|tenant:v1"));
        mvc.perform(post("/api/v1/calculate").with(user("ca-b", "admin-b", "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk())
                .andExpect(jsonPath("$.configurationVersion").value("platform_default:v1"));
    }

    @Test
    void aDraftWithProblemsReportsAllOfThemAndStaysADraft() throws Exception {
        String id = createTenantDraft(ADMIN, "{\"overall\":{\"weights\":{\"TEAM_HEALTH\":\"heavy\"}},\"surprise\":1,\"questions\":5}");
        JsonNode check = postJson("/api/v1/configuration/versions/" + id + "/validate", ADMIN, "");
        assertThat(check.get("valid").asBoolean()).isFalse();
        assertThat(check.get("problems").size()).isGreaterThanOrEqualTo(2);
        JsonNode submitted = postJson("/api/v1/configuration/versions/" + id + "/submit", ADMIN, "");
        assertThat(submitted.get("validationProblems").size()).isEqualTo(check.get("problems").size());
        mvc.perform(get("/api/v1/configuration/versions/" + id).with(ADMIN)).andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void aRejectionSendsItBackWithTheReasonAndTheDocumentCanBeEdited() throws Exception {
        String id = createTenantDraft(ADMIN, WEIGHTS);
        postJson("/api/v1/configuration/versions/" + id + "/submit", ADMIN, "");
        mvc.perform(put("/api/v1/configuration/versions/" + id).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/configuration/versions/" + id + "/reject").with(OWNER).contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"\"}"))
                .andExpect(status().isBadRequest());
        JsonNode rejected = postJson("/api/v1/configuration/versions/" + id + "/reject", OWNER, "{\"comment\":\"Health should not outweigh the rest that much.\"}");
        assertThat(rejected.get("status").asText()).isEqualTo("DRAFT");
        assertThat(rejected.at("/review/rejectionComment").asText()).startsWith("Health should not");
        mvc.perform(put("/api/v1/configuration/versions/" + id).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }

    // ---------------- fixtures and preview ----------------

    @Test
    void aFailingFixtureBlocksApprovalAndTheResponseSaysWhich() throws Exception {
        String id = createTenantDraft(ADMIN, WEIGHTS);
        String layer = body(mvc.perform(get("/api/v1/configuration/versions/" + id).with(ADMIN))).get("layerId").asText();
        String fixture = "{\"name\":\"Typical team\",\"answers\":" + ANSWERS + ",\"expectations\":{\"status\":\"VALID\",\"overall\":{\"min\":99,\"max\":100}}}";
        JsonNode saved = body(mvc.perform(post("/api/v1/configuration/layers/" + layer + "/fixtures").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(fixture))
                .andExpect(status().isCreated()));
        mvc.perform(post("/api/v1/configuration/layers/" + layer + "/fixtures").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(fixture)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FIXTURE_INVALID"));
        mvc.perform(post("/api/v1/configuration/layers/" + layer + "/fixtures").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"surprise\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/configuration/layers/" + layer + "/fixtures").with(user("ca-a", "analyst-1", "ANALYST"))).andExpect(jsonPath("$.length()").value(1));

        postJson("/api/v1/configuration/versions/" + id + "/submit", ADMIN, "");
        mvc.perform(post("/api/v1/configuration/versions/" + id + "/approve").with(OWNER).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ok\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFIGURATION_FIXTURES_FAILED"))
                .andExpect(jsonPath("$.details.fixture01").value(org.hamcrest.Matchers.startsWith("Typical team: the overall score is")));

        // delete the fixture and the approval goes through
        mvc.perform(delete("/api/v1/configuration/layers/" + layer + "/fixtures/" + saved.get("id").asText()).with(ADMIN)).andExpect(status().isNoContent());
        postJson("/api/v1/configuration/versions/" + id + "/approve", OWNER, "{\"note\":\"ok\"}");
        mvc.perform(get("/api/v1/configuration/versions/" + id).with(ADMIN)).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void aPreviewShowsNumbersAndNeverAnAnswer() throws Exception {
        String request = "{\"assessment\":{\"organizationId\":\"org-1\",\"templateId\":\"tpl-1\",\"templateVersion\":\"1\",\"status\":\"COMPLETED\","
                + "\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":" + ANSWERS + "}";
        mvc.perform(post("/api/v1/calculate").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk());
        String id = createTenantDraft(ADMIN, WEIGHTS);
        String result = mvc.perform(post("/api/v1/configuration/versions/" + id + "/preview").with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.assessments").value(1)).andExpect(jsonPath("$.summary.assessmentsUsed").value(true))
                .andExpect(jsonPath("$.items[0].kind").value("ASSESSMENT")).andExpect(jsonPath("$.items[0].organizationId").value("org-1"))
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("only one key person").doesNotContain("OFTEN");
        assertThat(jdbc.queryForObject("select count(*) from scoring_access_audit where action = 'CONFIGURATION_PREVIEW' and tenant_id = 'ca-a'", Integer.class)).isEqualTo(1);
    }

    // ---------------- the platform default ----------------

    @Test
    void thePlatformDefaultNeedsTwoPlatformAdministrators() throws Exception {
        String id = body(mvc.perform(post("/api/v1/platform/configuration/versions").with(platform("pa-1")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())).get("id").asText();
        postJson("/api/v1/platform/configuration/versions/" + id + "/submit", platform("pa-1"), "");
        mvc.perform(post("/api/v1/platform/configuration/versions/" + id + "/approve").with(platform("pa-1")).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"mine\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONFIGURATION_APPROVAL_REFUSED"));
        JsonNode approved = postJson("/api/v1/platform/configuration/versions/" + id + "/approve", platform("pa-2"), "{\"note\":\"Checked.\",\"calibrationReference\":\"Pilot\"}");
        assertThat(approved.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(approved.at("/review/approvedBy").asText()).isEqualTo("pa-2");
        // every tenant now resolves the new platform default, and a version is shown in the layer history
        mvc.perform(get("/api/v1/platform/configuration/versions").with(platform("pa-1"))).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2)).andExpect(jsonPath("$[0].payload").doesNotExist());
    }

    // ---------------- isolation ----------------

    @Test
    void aVersionOfOneTenantOrThePlatformLooksMissingToEveryoneElse() throws Exception {
        String mine = createTenantDraft(ADMIN, "{}");
        RequestPostProcessor other = user("ca-b", "admin-b", "ADMIN");
        String theirs = body(mvc.perform(get("/api/v1/platform/configuration/layers").with(platform("pa-1")))).get(0).at("/active/id").asText();
        mvc.perform(get("/api/v1/configuration/versions/" + mine).with(other)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONFIGURATION_VERSION_NOT_FOUND"));
        mvc.perform(put("/api/v1/configuration/versions/" + mine).with(other).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/configuration/versions/" + mine + "/submit").with(other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/configuration/versions/" + mine + "/approve").with(user("ca-b", "owner-b", "OWNER")).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/platform/configuration/versions/" + mine).with(platform("pa-1"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/configuration/versions/" + theirs).with(ADMIN)).andExpect(status().isNotFound());
        // an organization the token does not carry is refused, and an unknown scope is a bad request
        mvc.perform(post("/api/v1/configuration/versions").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"ORGANIZATION\",\"organizationId\":\"org-9\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/configuration/versions").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"PLATFORM_DEFAULT\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/configuration/versions").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"ORGANIZATION\"}")).andExpect(status().isBadRequest());
    }
}
