package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.company.leadershipintelligence.service.ConfigurationService;
import com.company.leadershipintelligence.service.ScoreAssessmentService;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.CalculateCommand;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ScoringApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ConfigurationService configurations;

    @MockitoSpyBean
    ScoreAssessmentService scoringSpy;

    private static final String INPUTS = "[{\"questionId\":\"Q01\",\"answer\":2},{\"questionId\":\"Q02\",\"answer\":\"OFTEN\"},"
            + "{\"questionId\":\"Q03\",\"answer\":true},{\"questionId\":\"Q04\",\"answer\":\"only one key person\"},{\"questionId\":\"Q05\",\"answer\":80}]";

    @BeforeEach
    void platformDefault() {
        tenant("api-a");
        tenant("api-b");
        Caller platform = Caller.platform("ops");
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "platform", ConfigurationSamples.sampleText());
        configurations.submitForValidation(platform, draft.id());
        configurations.activate(platform, draft.id(), new Approval("approver", null));
    }

    // ---------------- helpers ----------------

    private static RequestPostProcessor user(String tenant, List<String> organizations, String... roles) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("user-1");
            if (tenant != null) {
                j.claim("tenant_id", tenant);
            }
            j.claim("org_ids", organizations);
            j.claim("roles", List.of(roles));
        }).authorities(authorities.toArray(new SimpleGrantedAuthority[0]));
    }

    private static RequestPostProcessor admin() {
        return user("api-a", List.of("org-1"), "ADMIN");
    }

    private static String body(String organization, String status, String inputs) {
        return "{\"assessment\":{\"organizationId\":\"" + organization + "\",\"templateId\":\"tpl-1\",\"templateVersion\":\"1\","
                + "\"status\":\"" + status + "\",\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":" + inputs + "}";
    }

    private static String standardBody() {
        return body("org-1", "COMPLETED", INPUTS);
    }

    private MvcResult calculate(RequestPostProcessor who) throws Exception {
        return mvc.perform(post("/api/v1/calculate").with(who).contentType(MediaType.APPLICATION_JSON).content(standardBody()))
                .andExpect(status().isOk()).andReturn();
    }

    private JsonNode tree(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    // ---------------- the main contract ----------------

    @Test
    void calculateReturnsTheScoringResultInTheDocumentedShape() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(standardBody()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.scoreStatus").value("VALID"))
                .andExpect(jsonPath("$.leadershipIntelligenceScore").value(67.00))
                .andExpect(jsonPath("$.interpretation").value("HEALTHY"))
                .andExpect(jsonPath("$.tenantId").value("api-a"))
                .andExpect(jsonPath("$.organizationId").value("org-1"))
                .andExpect(jsonPath("$.engineVersion").value("3.0.0"))
                .andExpect(jsonPath("$.algorithmVersion").value("1.0.0"))
                .andExpect(jsonPath("$.configurationVersion").value("platform_default:v1"))
                .andExpect(jsonPath("$.dimensions.TEAM_HEALTH.rawScore").value(25.00))
                .andExpect(jsonPath("$.dimensions.TEAM_HEALTH.effectivePenalty").value(12.00))
                .andExpect(jsonPath("$.dimensions.TEAM_HEALTH.adjustedScore").value(13.00))
                .andExpect(jsonPath("$.dimensions.TEAM_HEALTH.label").value("CRITICAL"))
                .andExpect(jsonPath("$.dimensions.TEAM_HEALTH.status").value("VALID"))
                .andExpect(jsonPath("$.overall.finalScore").value(67.00))
                .andExpect(jsonPath("$.overall.effectiveOverallPenalty").value(0))
                .andExpect(jsonPath("$.patterns[0].status").value("MATCHED"))
                .andExpect(jsonPath("$.penalties[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.counts.matchedPatterns").value(1))
                .andExpect(jsonPath("$.counts.appliedPenalties").value(1))
                .andReturn();
        JsonNode body = tree(result);
        assertThat(body.get("assessmentId").asText()).startsWith("asm-");
        assertThat(body.get("explanationReference").asText()).isEqualTo(body.get("scoringRunId").asText());
        assertThat(body.get("configurationHash").asText()).matches("sha256:[0-9a-f]{64}");
        assertThat(result.getResponse().getContentAsString()).contains("\"leadershipIntelligenceScore\":67.00");
    }

    @Test
    void theResponseExposesOnlyDocumentedFields() throws Exception {
        JsonNode body = tree(calculate(admin()));
        Set<String> allowed = Set.of("scoringRunId", "assessmentId", "assessmentSnapshotId", "tenantId", "organizationId", "source",
                "configurationSelection", "engineVersion", "algorithmVersion", "configurationVersion", "configurationHash", "inputHash",
                "status", "scoreStatus", "leadershipIntelligenceScore", "interpretation", "dimensions", "overall", "imbalances", "patterns",
                "penalties", "counts", "errorCode", "calculatedAt", "explanationReference", "notice",
                "scope", "teamId", "organizationScoringRunId", "responses", "teamRuns");
        body.fieldNames().forEachRemaining(name -> assertThat(allowed).as(name).contains(name));
    }

    @Test
    void scoreLatestAndRecalculateFollowTheDocumentedFlow() throws Exception {
        JsonNode first = tree(calculate(admin()));
        String assessment = first.get("assessmentId").asText();
        mvc.perform(post("/api/v1/assessments/" + assessment + "/score").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scoringRunId").value(first.get("scoringRunId").asText()));
        mvc.perform(get("/api/v1/assessments/" + assessment + "/score").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scoringRunId").value(first.get("scoringRunId").asText()));
        clock.advance(java.time.Duration.ofMinutes(1));
        MvcResult recalculated = mvc.perform(post("/api/v1/assessments/" + assessment + "/score/recalculate").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("RECALCULATE"))
                .andExpect(jsonPath("$.leadershipIntelligenceScore").value(67.00)).andReturn();
        assertThat(tree(recalculated).get("scoringRunId").asText()).isNotEqualTo(first.get("scoringRunId").asText());
        mvc.perform(get("/api/v1/assessments/" + assessment + "/score").with(admin()))
                .andExpect(jsonPath("$.scoringRunId").value(tree(recalculated).get("scoringRunId").asText()));
    }

    @Test
    void recalculateWithAConfigurationVersionIsForOwnersAndAdminsOnly() throws Exception {
        JsonNode first = tree(calculate(admin()));
        String assessment = first.get("assessmentId").asText();
        String version = configurations.history(Caller.platform("ops"), ConfigurationTarget.platform()).get(0).id().toString();
        String requestBody = "{\"configurationVersion\":\"" + version + "\"}";
        mvc.perform(post("/api/v1/assessments/" + assessment + "/score/recalculate")
                        .with(user("api-a", List.of("org-1"), "ENGINEERING_LEADER")).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post("/api/v1/assessments/" + assessment + "/score/recalculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configurationSelection").value("EXPLICIT"))
                .andExpect(jsonPath("$.configurationVersion").value("explicit:platform_default:v1"));
        mvc.perform(post("/api/v1/assessments/" + assessment + "/score/recalculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configurationVersion\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        // without a body the leader may recalculate with the effective configuration
        mvc.perform(post("/api/v1/assessments/" + assessment + "/score/recalculate").with(user("api-a", List.of("org-1"), "ENGINEERING_LEADER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configurationSelection").value("DEFAULT"));
    }

    @Test
    void subResourcesReturnTheStoredFindings() throws Exception {
        String run = tree(calculate(admin())).get("scoringRunId").asText();
        mvc.perform(get("/api/v1/scoring-runs/" + run).with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.scoringRunId").value(run));
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/dimensions").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.redacted").value(false))
                .andExpect(jsonPath("$.dimensions[0].score.dimension").value("TEAM_HEALTH"))
                .andExpect(jsonPath("$.dimensions[0].score.contributions[0].questionId").value("Q01"))
                .andExpect(jsonPath("$.adjusted[0].adjustedScore").value(13.00));
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/patterns").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.patterns[0].patternId").value("TH-001"))
                .andExpect(jsonPath("$.patterns[0].confidence").value(1.00))
                .andExpect(jsonPath("$.patterns[0].evidence[0].numericValue").exists());
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/penalties").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.penalties[0].penaltyId").value("TH-P01"))
                .andExpect(jsonPath("$.penalties[0].effectiveDeduction").value(12.00))
                .andExpect(jsonPath("$.effectiveByTarget.TEAM_HEALTH").value(12.00))
                .andExpect(jsonPath("$.tieBreakVersion").value("1"));
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/imbalances").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.imbalances[0].ruleId").value("DELIVERY_TECHNICAL_GAP"))
                .andExpect(jsonPath("$.imbalances[0].status").value("NOT_DETECTED"));
    }

    // ---------------- explanation and redaction ----------------

    @Test
    void theExplanationIsTheStoredTraceAndViewersGetARedactedCopy() throws Exception {
        String run = tree(calculate(admin())).get("scoringRunId").asText();
        String stored = jdbc.queryForObject("select trace::text from scoring_run where id = ?::uuid", String.class, run);

        MvcResult full = mvc.perform(get("/api/v1/scoring-runs/" + run + "/explanation").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.redacted").value(false)).andReturn();
        assertThat(tree(full).get("trace")).isEqualTo(json.readTree(stored));
        assertThat(tree(full).get("trace").get("steps")).hasSize(8);

        for (String role : List.of("OWNER", "ANALYST")) {
            mvc.perform(get("/api/v1/scoring-runs/" + run + "/explanation").with(user("api-a", List.of("org-1"), role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.redacted").value(false));
        }
        MvcResult viewer = mvc.perform(get("/api/v1/scoring-runs/" + run + "/explanation").with(user("api-a", List.of("org-1"), "VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.redacted").value(true)).andReturn();
        JsonNode steps = tree(viewer).get("trace").get("steps");
        assertThat(steps).hasSize(6);
        assertThat(viewer.getResponse().getContentAsString()).doesNotContain("key person").doesNotContain("sourceQuestionIds")
                .doesNotContain("\"numericValue\"");
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/dimensions").with(user("api-a", List.of("org-1"), "VIEWER")))
                .andExpect(jsonPath("$.redacted").value(true)).andExpect(jsonPath("$.dimensions[0].score.contributions").doesNotExist());
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/patterns").with(user("api-a", List.of("org-1"), "VIEWER")))
                .andExpect(jsonPath("$.patterns[0].evidence[0].numericValue").doesNotExist())
                .andExpect(jsonPath("$.patterns[0].evidence[0].operandKey").exists());
        // the main result is redacted for a viewer too, but keeps scores and statuses
        mvc.perform(get("/api/v1/scoring-runs/" + run).with(user("api-a", List.of("org-1"), "VIEWER")))
                .andExpect(jsonPath("$.leadershipIntelligenceScore").value(67.00))
                .andExpect(jsonPath("$.patterns[0].evidence[0].numericValue").doesNotExist());
    }

    // ---------------- authentication and authorization ----------------

    @Test
    void withoutATokenEverythingIsRejectedInTheStandardFormat() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/calculate").contentType(MediaType.APPLICATION_JSON).content(standardBody()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists()).andReturn();
        assertThat(tree(result).get("correlationId").asText()).isEqualTo(result.getResponse().getHeader("X-Correlation-Id"));
        mvc.perform(get("/api/v1/scoring-runs/" + java.util.UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void anInvalidBearerTokenIsRejectedBecauseNoVerificationKeyIsConfigured() throws Exception {
        mvc.perform(get("/api/v1/scoring-runs/" + java.util.UUID.randomUUID()).header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void writesNeedOwnerAdminOrLeaderAndReadsAnyKnownRole() throws Exception {
        String run = tree(calculate(admin())).get("scoringRunId").asText();
        for (String role : List.of("OWNER", "ADMIN", "ENGINEERING_LEADER")) {
            mvc.perform(post("/api/v1/calculate").with(user("api-a", List.of("org-1"), role)).contentType(MediaType.APPLICATION_JSON)
                    .content(standardBody())).andExpect(status().isOk());
        }
        for (String role : List.of("ANALYST", "VIEWER")) {
            mvc.perform(post("/api/v1/calculate").with(user("api-a", List.of("org-1"), role)).contentType(MediaType.APPLICATION_JSON)
                            .content(standardBody())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            mvc.perform(post("/api/v1/assessments/asm-x/score").with(user("api-a", List.of("org-1"), role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/assessments/asm-x/score/recalculate").with(user("api-a", List.of("org-1"), role))).andExpect(status().isForbidden());
        }
        for (String role : List.of("OWNER", "ADMIN", "ENGINEERING_LEADER", "ANALYST", "VIEWER")) {
            mvc.perform(get("/api/v1/scoring-runs/" + run).with(user("api-a", List.of("org-1"), role))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/scoring-runs/" + run).with(user("api-a", List.of("org-1")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/scoring-runs/" + run).with(user("api-a", List.of("org-1"), "SUPERUSER"))).andExpect(status().isForbidden());
    }

    @Test
    void aTokenWithoutATenantIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/calculate").with(user(null, List.of("org-1"), "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(standardBody()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TENANT_CONTEXT_MISSING"));
        assertThat(count("select count(*) from assessment")).isZero();
    }

    @Test
    void anOrganizationOutsideTheTokenIsRefusedAndNothingIsStored() throws Exception {
        mvc.perform(post("/api/v1/calculate").with(user("api-a", List.of("org-9"), "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(standardBody()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ORGANIZATION_ACCESS_DENIED"));
        mvc.perform(post("/api/v1/calculate").with(user("api-a", List.of(), "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(standardBody()))
                .andExpect(status().isForbidden());
        assertThat(count("select count(*) from assessment")).isZero();
    }

    @Test
    void theTenantAndOrganizationOfAStoredRunAreCheckedOnEveryRead() throws Exception {
        JsonNode first = tree(calculate(admin()));
        String run = first.get("scoringRunId").asText();
        String assessment = first.get("assessmentId").asText();
        RequestPostProcessor otherOrganization = user("api-a", List.of("org-9"), "ADMIN");
        mvc.perform(get("/api/v1/scoring-runs/" + run).with(otherOrganization)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_ACCESS_DENIED"));
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/explanation").with(otherOrganization)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/assessments/" + assessment + "/score").with(otherOrganization)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assessments/" + assessment + "/score").with(otherOrganization)).andExpect(status().isForbidden());
        // another tenant: indistinguishable from "does not exist"
        RequestPostProcessor tenantB = user("api-b", List.of("org-1"), "ADMIN");
        mvc.perform(get("/api/v1/scoring-runs/" + run).with(tenantB)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCORING_RUN_NOT_FOUND"));
        mvc.perform(post("/api/v1/assessments/" + assessment + "/score").with(tenantB)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSESSMENT_NOT_FOUND"));
        mvc.perform(post("/api/v1/assessments/" + assessment + "/score/recalculate").with(tenantB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/assessments/" + assessment + "/score").with(tenantB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/explanation").with(tenantB)).andExpect(status().isNotFound());
        // tenant B's own request works and does not touch tenant A's data
        calculate(tenantB);
        assertThat(count("select count(*) from scoring_run where tenant_id = 'api-a'")).isEqualTo(1);
    }

    // ---------------- requests the API refuses ----------------

    @Test
    void aRequestCannotCarryATenantAnAssessmentIdConfigurationOrWeights() throws Exception {
        String[] extras = {"\"tenantId\":\"api-b\"", "\"assessmentId\":\"asm-chosen\"", "\"configuration\":{}", "\"weights\":{}", "\"organizationId\":\"org-1\""};
        for (String extra : extras) {
            String name = extra.substring(1, extra.indexOf('"', 1));
            mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                            .content(standardBody().replaceFirst("^\\{", "{" + extra + ",")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.details.field").value(name));
        }
        assertThat(count("select count(*) from assessment")).isZero();
    }

    @Test
    void malformedRequestsAreRejectedWithoutDetailsAboutTheInternals() throws Exception {
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details['assessment']").exists())
                .andExpect(jsonPath("$.details['inputs']").exists());
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body("org-1", "COMPLETED", "[]")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("org-1", "COMPLETED", "[{\"questionId\":\" \",\"answer\":1}]")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(standardBody().replace("\"templateVersion\":\"1\"", "\"templateVersion\":\"one\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("org-1", "COMPLETED", "[{\"questionId\":\"Q01\",\"answer\":1,\"questionType\":\"WHAT\"}]")))
                .andExpect(status().isBadRequest());
        String longText = "x".repeat(10_001);
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("org-1", "COMPLETED", "[{\"questionId\":\"Q04\",\"answer\":\"" + longText + "\"}]")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.TEXT_PLAIN).content(standardBody()))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/scoring-runs/not-a-uuid").with(admin())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(count("select count(*) from assessment")).isZero();
    }

    @Test
    void invalidAnswersAreBusinessErrorsWithTheQuestionNamed() throws Exception {
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("org-1", "COMPLETED", "[{\"questionId\":\"Q01\",\"answer\":9}]")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_SCALE_VALUE"))
                .andExpect(jsonPath("$.details.questionId").value("Q01"));
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("org-1", "COMPLETED", "[{\"questionId\":\"Q99\",\"answer\":1}]")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("QUESTION_NOT_CONFIGURED"));
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("org-1", "COMPLETED", "[{\"questionId\":\"Q01\",\"answer\":1},{\"questionId\":\"Q01\",\"answer\":2}]")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DUPLICATE_QUESTION_INPUT"));
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("org-1", "COMPLETED", "[{\"questionId\":\"Q01\",\"answer\":[1,2]}]")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_ANSWER_TYPE"));
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body("org-1", "IN_PROGRESS", INPUTS)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ASSESSMENT_NOT_COMPLETED"));
        assertThat(count("select count(*) from assessment")).isZero();
        assertThat(count("select count(*) from scoring_run")).isZero();
    }

    @Test
    void aDeclaredQuestionTypeIsCheckedNeverTrusted() throws Exception {
        String wrong = INPUTS.replace("{\"questionId\":\"Q01\",\"answer\":2}", "{\"questionId\":\"Q01\",\"answer\":2,\"questionType\":\"BOOLEAN\"}");
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body("org-1", "COMPLETED", wrong)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_ANSWER_TYPE"))
                .andExpect(jsonPath("$.details.questionId").value("Q01"));
        String right = INPUTS.replace("{\"questionId\":\"Q01\",\"answer\":2}", "{\"questionId\":\"Q01\",\"answer\":2,\"questionType\":\"SCALE\"}");
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body("org-1", "COMPLETED", right)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.leadershipIntelligenceScore").value(67.00));
    }

    @Test
    void unknownResourcesAreStandardNotFoundErrors() throws Exception {
        mvc.perform(post("/api/v1/assessments/asm-nope/score").with(admin())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSESSMENT_NOT_FOUND"));
        mvc.perform(get("/api/v1/scoring-runs/" + java.util.UUID.randomUUID()).with(admin())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCORING_RUN_NOT_FOUND"));
        mvc.perform(get("/api/v1/assessments/asm-nope/score").with(admin())).andExpect(status().isNotFound());
    }

    // ---------------- idempotency ----------------

    @Test
    void theSameIdempotencyKeyReturnsTheSameRunAndADifferentRequestConflicts() throws Exception {
        MvcResult first = mvc.perform(post("/api/v1/calculate").with(admin()).header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON).content(standardBody())).andExpect(status().isOk()).andReturn();
        MvcResult second = mvc.perform(post("/api/v1/calculate").with(admin()).header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON).content(standardBody())).andExpect(status().isOk()).andReturn();
        assertThat(tree(second).get("scoringRunId").asText()).isEqualTo(tree(first).get("scoringRunId").asText());
        assertThat(count("select count(*) from assessment")).isEqualTo(1);
        mvc.perform(post("/api/v1/calculate").with(admin()).header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON)
                        .content(body("org-1", "COMPLETED", INPUTS.replace("\"answer\":2}", "\"answer\":3}"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        mvc.perform(post("/api/v1/calculate").with(admin()).header("Idempotency-Key", "bad key").contentType(MediaType.APPLICATION_JSON)
                        .content(standardBody())).andExpect(status().isBadRequest());
        assertThat(count("select count(*) from assessment")).isEqualTo(1);
    }

    // ---------------- failures and observability ----------------

    @Test
    void anUnexpectedFailureIsAGenericErrorWithoutInternals() throws Exception {
        Mockito.doThrow(new IllegalStateException("secret detail jdbc:postgresql://host/db password=hunter2"))
                .when(scoringSpy).calculate(any(CalculateCommand.class));
        MvcResult result = mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(standardBody()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("SCORING_EXECUTION_FAILED"))
                .andExpect(jsonPath("$.correlationId").exists()).andReturn();
        String text = result.getResponse().getContentAsString();
        assertThat(text).doesNotContain("secret").doesNotContain("jdbc").doesNotContain("hunter2").doesNotContain("Exception").doesNotContain("at com.");
    }

    @Test
    void configurationFaultsAreServerErrorsThatDoNotExplainTheConfiguration() throws Exception {
        Mockito.doThrow(new ScoringException(ScoringErrorCode.INVALID_PENALTY_CONFIGURATION, "penalty TH-P01 references pattern TH-999"))
                .when(scoringSpy).calculate(any(CalculateCommand.class));
        MvcResult result = mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(standardBody()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INVALID_PENALTY_CONFIGURATION")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("TH-P01").doesNotContain("TH-999");
    }

    @Test
    void theCorrelationIdIsPropagatedWhenSafeAndReplacedWhenNot() throws Exception {
        mvc.perform(post("/api/v1/assessments/asm-nope/score").with(admin()).header("X-Correlation-Id", "req-123.abc"))
                .andExpect(header().string("X-Correlation-Id", "req-123.abc")).andExpect(jsonPath("$.correlationId").value("req-123.abc"));
        MvcResult replaced = mvc.perform(post("/api/v1/assessments/asm-nope/score").with(admin()).header("X-Correlation-Id", "bad id with spaces <script>"))
                .andReturn();
        assertThat(replaced.getResponse().getHeader("X-Correlation-Id")).matches("[0-9a-f-]{36}");
        assertThat(tree(replaced).get("correlationId").asText()).isEqualTo(replaced.getResponse().getHeader("X-Correlation-Id"));
    }

    @Test
    void rawAnswersNeverReachTheLogs(CapturedOutput output) throws Exception {
        calculate(admin());
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(body("org-1", "COMPLETED", "[{\"questionId\":\"Q04\",\"answer\":\"only one key person\"},{\"questionId\":\"Q99\",\"answer\":1}]")));
        Mockito.doThrow(new IllegalStateException("boom")).when(scoringSpy).calculate(any(CalculateCommand.class));
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(standardBody()));
        assertThat(output.getAll()).doesNotContain("key person");
    }

    @Test
    void healthIsOpenAndTheOpenApiDocumentDescribesTheContract() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        MvcResult docs = mvc.perform(get("/v3/api-docs").with(user("api-a", List.of("org-1"), "VIEWER"))).andExpect(status().isOk()).andReturn();
        JsonNode openApi = tree(docs);
        List<String> paths = new ArrayList<>();
        openApi.get("paths").fieldNames().forEachRemaining(paths::add);
        assertThat(paths).contains("/api/v1/calculate", "/api/v1/assessments/{assessmentId}/score",
                "/api/v1/assessments/{assessmentId}/score/recalculate", "/api/v1/scoring-runs/{scoringRunId}",
                "/api/v1/scoring-runs/{scoringRunId}/explanation", "/api/v1/scoring-runs/{scoringRunId}/dimensions",
                "/api/v1/scoring-runs/{scoringRunId}/patterns", "/api/v1/scoring-runs/{scoringRunId}/penalties",
                "/api/v1/scoring-runs/{scoringRunId}/imbalances");
        List<String> requestFields = new ArrayList<>();
        openApi.get("components").get("schemas").get("CalculateRequest").get("properties").fieldNames().forEachRemaining(requestFields::add);
        assertThat(requestFields).containsExactlyInAnyOrder("assessment", "inputs", "responses");
        assertThat(openApi.get("components").get("securitySchemes").has("bearer")).isTrue();
    }
}
