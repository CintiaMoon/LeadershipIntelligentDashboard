package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import com.company.leadershipintelligence.support.RecordingObservationHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@AutoConfigureObservability
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIntegrationTest extends PostgresIntegrationTest {

    private static final RecordingObservationHandler HANDLER = new RecordingObservationHandler();
    private static boolean handlerRegistered;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    MeterRegistry meters;

    @Autowired
    ObservationRegistry observations;

    @Autowired
    ConfigurationService configurations;

    private static final String INPUTS = "[{\"questionId\":\"Q01\",\"answer\":2},{\"questionId\":\"Q02\",\"answer\":\"OFTEN\"},"
            + "{\"questionId\":\"Q03\",\"answer\":true},{\"questionId\":\"Q04\",\"answer\":\"only one key person\"},{\"questionId\":\"Q05\",\"answer\":80}]";

    @BeforeEach
    void setup() {
        tenant("api-obs");
        tenant("api-obs-b");
        Caller platform = Caller.platform("ops");
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "platform", ConfigurationSamples.sampleText());
        configurations.submitForValidation(platform, draft.id());
        configurations.activate(platform, draft.id(), new Approval("approver", null));
        synchronized (ObservabilityIntegrationTest.class) {
            if (!handlerRegistered) {
                observations.observationConfig().observationHandler(HANDLER);
                handlerRegistered = true;
            }
        }
    }

    private static RequestPostProcessor user(String tenant, String... roles) {
        SimpleGrantedAuthority[] authorities = java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toArray(SimpleGrantedAuthority[]::new);
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("user-1");
            j.claim("tenant_id", tenant);
            j.claim("org_ids", List.of("org-1"));
            j.claim("roles", List.of(roles));
        }).authorities(authorities);
    }

    private RequestPostProcessor admin() {
        return user("api-obs", "ADMIN");
    }

    private static String body(String inputs) {
        return "{\"assessment\":{\"organizationId\":\"org-1\",\"templateId\":\"tpl-1\",\"templateVersion\":\"1\",\"status\":\"COMPLETED\","
                + "\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":" + inputs + "}";
    }

    private MvcResult calculate(RequestPostProcessor who, String inputs) throws Exception {
        return mvc.perform(post("/api/v1/calculate").with(who).contentType(MediaType.APPLICATION_JSON).content(body(inputs))).andReturn();
    }

    private double counter(String name, String... tags) {
        var counter = meters.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private long timerCount(String name) {
        return meters.find(name).timers().stream().mapToLong(t -> t.count()).sum();
    }

    // ---------------- metrics ----------------

    @Test
    void scoringRequestsAreCountedAndEveryStageIsTimed() throws Exception {
        double requests = counter("scoring.requests", "source", "CALCULATE");
        double successes = counter("scoring.success", "source", "CALCULATE");
        long total = timerCount("scoring.duration");
        long normalization = timerCount("normalization.duration");
        long dimensions = timerCount("dimension.scoring.duration");
        long patterns = timerCount("pattern.evaluation.duration");
        long penalties = timerCount("penalty.evaluation.duration");
        long explainability = timerCount("explainability.duration");

        assertThat(calculate(admin(), INPUTS).getResponse().getStatus()).isEqualTo(200);

        assertThat(counter("scoring.requests", "source", "CALCULATE")).isEqualTo(requests + 1);
        assertThat(counter("scoring.success", "source", "CALCULATE")).isEqualTo(successes + 1);
        assertThat(timerCount("scoring.duration")).isEqualTo(total + 1);
        assertThat(timerCount("normalization.duration")).isEqualTo(normalization + 1);
        assertThat(timerCount("dimension.scoring.duration")).isEqualTo(dimensions + 1);
        assertThat(timerCount("pattern.evaluation.duration")).isEqualTo(patterns + 1);
        assertThat(timerCount("penalty.evaluation.duration")).isEqualTo(penalties + 1);
        assertThat(timerCount("explainability.duration")).isEqualTo(explainability + 1);
    }

    @Test
    void failuresAreCountedByErrorCode() throws Exception {
        double failures = counter("scoring.failures", "source", "CALCULATE", "code", "QUESTION_NOT_CONFIGURED");
        double requests = counter("scoring.requests", "source", "CALCULATE");
        calculate(admin(), "[{\"questionId\":\"NOPE\",\"answer\":1}]");
        assertThat(counter("scoring.failures", "source", "CALCULATE", "code", "QUESTION_NOT_CONFIGURED")).isEqualTo(failures + 1);
        assertThat(counter("scoring.requests", "source", "CALCULATE")).isEqualTo(requests + 1);
    }

    @Test
    void insufficientDataRunsAreCountedAsSuccessfulRequests() throws Exception {
        double insufficient = counter("insufficient.data", "source", "CALCULATE");
        double successes = counter("scoring.success", "source", "CALCULATE");
        calculate(admin(), "[{\"questionId\":\"Q01\",\"answer\":2},{\"questionId\":\"Q02\",\"answer\":\"OFTEN\"},{\"questionId\":\"Q05\",\"answer\":80}]");
        assertThat(counter("insufficient.data", "source", "CALCULATE")).isEqualTo(insufficient + 1);
        assertThat(counter("scoring.success", "source", "CALCULATE")).isEqualTo(successes + 1);
    }

    @Test
    void configurationCacheHitsAndMissesAreExposed() throws Exception {
        calculate(admin(), INPUTS);
        double hits = meters.find("configuration.cache.hits").functionCounter().count();
        double misses = meters.find("configuration.cache.misses").functionCounter().count();
        calculate(admin(), INPUTS);
        assertThat(meters.find("configuration.cache.hits").functionCounter().count()).isGreaterThan(hits);
        assertThat(meters.find("configuration.cache.misses").functionCounter().count()).isGreaterThanOrEqualTo(misses);
    }

    @Test
    void thePrometheusEndpointUsesTheSpecifiedMetricNamesForOwnersAndAdminsOnly() throws Exception {
        calculate(admin(), INPUTS);
        calculate(admin(), "[{\"questionId\":\"NOPE\",\"answer\":1}]");
        calculate(admin(), "[{\"questionId\":\"Q01\",\"answer\":2}]");
        String text = mvc.perform(get("/actuator/prometheus").with(admin())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String name : List.of("scoring_requests_total", "scoring_success_total", "scoring_failures_total", "scoring_duration_seconds",
                "normalization_duration_seconds", "dimension_scoring_duration_seconds", "pattern_evaluation_duration_seconds",
                "penalty_evaluation_duration_seconds", "explainability_duration_seconds", "configuration_cache_hits_total",
                "configuration_cache_misses_total", "insufficient_data_total")) {
            assertThat(text).as(name).contains(name);
        }
        assertThat(text).doesNotContain("api-obs").doesNotContain("asm-").doesNotContain("key person");
        mvc.perform(get("/actuator/prometheus").with(user("api-obs", "VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").with(user("api-obs", "ANALYST"))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    // ---------------- tracing observations ----------------

    @Test
    void aRequestProducesTheDocumentedSpanTreeWithOnlyLowCardinalityValues() throws Exception {
        HANDLER.start();
        calculate(admin(), INPUTS);
        List<RecordingObservationHandler.Started> started = HANDLER.stop();
        List<String> names = started.stream().map(RecordingObservationHandler.Started::name).toList();
        assertThat(names).contains("scoring.duration");
        assertThat(names.indexOf("scoring.duration")).isLessThan(names.indexOf("lis.configuration.resolve"));
        assertThat(names).contains("lis.configuration.resolve", "lis.engine.execute", "normalization.duration", "dimension.scoring.duration",
                "pattern.evaluation.duration", "penalty.evaluation.duration", "explainability.duration", "lis.persistence.store");
        assertThat(names.indexOf("lis.configuration.resolve")).isLessThan(names.indexOf("lis.engine.execute"));
        assertThat(names.indexOf("lis.engine.execute")).isLessThan(names.indexOf("lis.persistence.store"));
        for (RecordingObservationHandler.Started s : started.stream().filter(x -> !x.name().startsWith("http.") && !x.name().startsWith("spring.")).toList()) {
            assertThat(s.keys()).as(s.name()).isSubsetOf("source", "stage");
            assertThat(String.join(" ", s.values())).doesNotContain("api-obs").doesNotContain("asm-").doesNotContain("key person");
        }
    }

    // ---------------- structured logs ----------------

    @Test
    void aFinishedRunIsLoggedWithTheSpecifiedFieldsAndNoTenantOrAnswers(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/v1/calculate").with(admin()).header("X-Correlation-Id", "obs-corr-1").contentType(MediaType.APPLICATION_JSON)
                .content(body(INPUTS))).andExpect(status().isOk());
        String line = output.getAll().lines().filter(l -> l.contains("scoring run finished")).findFirst().orElseThrow();
        assertThat(line).contains("scoringRunId", "assessmentId", "engineVersion", "3.0.0", "configurationVersion", "platform_default:v1",
                "durationMs", "COMPLETED", "obs-corr-1");
        assertThat(line).doesNotContain("api-obs").doesNotContain("key person");
    }

    @Test
    void aFailureIsLoggedWithItsCategoryAndStillNoAnswers(CapturedOutput output) throws Exception {
        calculate(admin(), "[{\"questionId\":\"Q04\",\"answer\":\"only one key person\"},{\"questionId\":\"NOPE\",\"answer\":1}]");
        String line = output.getAll().lines().filter(l -> l.contains("scoring run failed")).findFirst().orElseThrow();
        assertThat(line).contains("QUESTION_NOT_CONFIGURED", "durationMs", "FAILED");
        // MockMvc echoes the request body to the console; only the application's own JSON log lines are in scope
        assertThat(output.getAll().lines().filter(l -> l.startsWith("{\"@timestamp\"")).collect(java.util.stream.Collectors.joining(" ")))
                .doesNotContain("key person");
    }

    // ---------------- access audit ----------------

    @Test
    void openingAnExplanationIsAuditedWithWhoAndWhetherItWasRedactedButNeverWhat() throws Exception {
        String run = json.readTree(calculate(admin(), INPUTS).getResponse().getContentAsString()).get("scoringRunId").asText();
        assertThat(count("select count(*) from scoring_access_audit")).isZero();

        mvc.perform(get("/api/v1/scoring-runs/" + run + "/explanation").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/explanation").with(user("api-obs", "VIEWER"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/scoring-runs/" + run).with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/dimensions").with(admin())).andExpect(status().isOk());
        // a foreign tenant is refused and leaves no trace in this tenant's audit
        mvc.perform(get("/api/v1/scoring-runs/" + run + "/explanation").with(user("api-obs-b", "ADMIN"))).andExpect(status().isNotFound());

        assertThat(count("select count(*) from scoring_access_audit")).isEqualTo(2);
        assertThat(jdbc.queryForList("select action || '/' || actor || '/' || (detail->>'redacted') from scoring_access_audit order by id", String.class))
                .containsExactly("EXPLANATION_READ/user-1/false", "EXPLANATION_READ/user-1/true");
        assertThat(count("select count(*) from scoring_access_audit where tenant_id <> 'api-obs'")).isZero();
        String stored = String.join(" ", jdbc.queryForList("select coalesce(detail::text,'') from scoring_access_audit", String.class));
        assertThat(stored).doesNotContain("TEAM_HEALTH").doesNotContain("key person");
        assertThatThrownBy(() -> jdbc.update("update scoring_access_audit set actor = 'x'")).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    @SuppressWarnings("unused")
    private JsonNode unused() {
        return null;
    }
}
