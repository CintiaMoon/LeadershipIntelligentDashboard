package com.company.leadershipintelligence.config;

import com.company.leadershipintelligence.config.beans.InitialConfigurationLoader;
import com.company.leadershipintelligence.config.beans.InitialConfigurationLoader.Result;
import com.company.leadershipintelligence.config.properties.InitialConfigurationProperties;
import com.company.leadershipintelligence.config.properties.InitialConfigurationProperties.Mode;
import com.company.leadershipintelligence.domain.enums.ConfigurationStatus;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.company.leadershipintelligence.repository.configuration.InitialConfigurationTest;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The platform configuration is a JSON file read at startup. These tests run the loader against a real database and
 * then score through the HTTP API with the bundled initial configuration.
 */
@AutoConfigureMockMvc
class InitialConfigurationLoaderIntegrationTest extends PostgresIntegrationTest {

    private static final String BUNDLED = "classpath:config/initial-platform-default.json";
    private static final Caller PLATFORM = Caller.platform("test");

    @Autowired
    ConfigurationService configurations;

    @Autowired
    ResourceLoader resources;

    @Autowired
    Environment environment;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private InitialConfigurationLoader loader(String location, Mode mode) {
        return new InitialConfigurationLoader(configurations, resources, new InitialConfigurationProperties(true, location, mode), environment);
    }

    private List<ConfigurationVersionView> history() {
        return configurations.history(PLATFORM, ConfigurationTarget.platform());
    }

    private void activateSample() {
        ConfigurationVersionView draft = configurations.createDraft(PLATFORM, ConfigurationTarget.platform(), "sample", ConfigurationSamples.sampleText());
        configurations.submitForValidation(PLATFORM, draft.id());
        configurations.activate(PLATFORM, draft.id(), new Approval("approver", null));
    }

    @Test
    void activatesTheBundledFileWhenNothingIsActive() throws Exception {
        assertThat(loader(BUNDLED, Mode.IF_MISSING).load()).isEqualTo(Result.ACTIVATED);

        assertThat(history()).hasSize(1);
        ConfigurationVersionView active = history().get(0);
        assertThat(active.status()).isEqualTo(ConfigurationStatus.ACTIVE);
        assertThat(active.approvedBy()).isEqualTo("initial-configuration");
        assertThat(active.configHash()).isEqualTo(configurations.hashOf(InitialConfigurationTest.text()));
    }

    @Test
    void doesNothingWhenTheActiveVersionIsAlreadyTheFile() {
        loader(BUNDLED, Mode.IF_MISSING).load();
        assertThat(loader(BUNDLED, Mode.IF_MISSING).load()).isEqualTo(Result.UP_TO_DATE);
        assertThat(loader(BUNDLED, Mode.SYNC).load()).isEqualTo(Result.UP_TO_DATE);
        assertThat(history()).hasSize(1);
    }

    @Test
    void ignoresFormattingAndKeyOrderWhenComparing(@TempDir Path dir) throws Exception {
        loader(BUNDLED, Mode.IF_MISSING).load();
        JsonNode document = json.readTree(InitialConfigurationTest.text());
        // the same document, written compactly
        Path file = Files.writeString(dir.resolve("same.json"), json.writeValueAsString(document));
        assertThat(loader(file.toUri().toString(), Mode.SYNC).load()).isEqualTo(Result.UP_TO_DATE);
        assertThat(history()).hasSize(1);
    }

    @Test
    void leavesADifferentActiveVersionAloneUnlessToldToSynchronize() {
        activateSample();
        ConfigurationVersionView before = history().get(0);

        assertThat(loader(BUNDLED, Mode.IF_MISSING).load()).isEqualTo(Result.DIFFERS_NOT_CHANGED);

        assertThat(history()).hasSize(1);
        assertThat(history().get(0).id()).isEqualTo(before.id());
        assertThat(history().get(0).status()).isEqualTo(ConfigurationStatus.ACTIVE);
    }

    @Test
    void synchronizingActivatesAChangedFileAsANewVersionAndArchivesTheOldOne() {
        activateSample();

        assertThat(loader(BUNDLED, Mode.SYNC).load()).isEqualTo(Result.REPLACED);

        List<ConfigurationVersionView> versions = history();
        assertThat(versions).hasSize(2);
        assertThat(versions).filteredOn(v -> v.status() == ConfigurationStatus.ACTIVE).hasSize(1)
                .allSatisfy(v -> assertThat(v.version()).isEqualTo(2));
        assertThat(versions).filteredOn(v -> v.status() == ConfigurationStatus.ARCHIVED).hasSize(1)
                .allSatisfy(v -> assertThat(v.configHash()).isEqualTo(configurations.hashOf(ConfigurationSamples.sampleText())));
    }

    @Test
    void aFileThatFailsValidationStopsStartupWithTheProblemAndActivatesNothing(@TempDir Path dir) throws Exception {
        ObjectNode broken = (ObjectNode) json.readTree(InitialConfigurationTest.text());
        ((ObjectNode) broken.withArray("penalties").get(0).get("trigger")).put("pattern", "TH-999");
        Path file = Files.writeString(dir.resolve("broken.json"), json.writeValueAsString(broken));

        assertThatThrownBy(() -> loader(file.toUri().toString(), Mode.IF_MISSING).load())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("is not valid").hasMessageContaining("TH-999");
        assertThat(history()).noneMatch(v -> v.status() == ConfigurationStatus.ACTIVE);
    }

    @Test
    void aFileThatIsNotJsonOrIsMissingFailsClearly(@TempDir Path dir) throws Exception {
        Path notJson = Files.writeString(dir.resolve("text.json"), "this is not json");
        assertThatThrownBy(() -> loader(notJson.toUri().toString(), Mode.IF_MISSING).load())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not a valid JSON document");
        assertThatThrownBy(() -> loader(dir.resolve("absent.json").toUri().toString(), Mode.IF_MISSING).load())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not exist").hasMessageContaining("lis.configuration.initial");
        assertThat(history()).isEmpty();
    }

    // ---------------- scoring through the HTTP API with the bundled configuration ----------------

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("user-1");
            j.claim("tenant_id", "init-a");
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

    private JsonNode calculate(ObjectNode body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static void putAnswers(ArrayNode out, Map<String, Object> answers, String team) {
        answers.forEach((question, answer) -> {
            ObjectNode node = out.addObject();
            node.put("questionId", question);
            switch (answer) {
                case Integer i -> node.put("answer", i);
                case Boolean b -> node.put("answer", b);
                default -> node.put("answer", answer.toString());
            }
            if (team != null) {
                node.put("scope", "MEMBER").put("teamId", team);
            }
        });
    }

    @Test
    void scoresAnAssessmentWithTheBundledConfigurationFromEmptyToResult() throws Exception {
        tenant("init-a");
        loader(BUNDLED, Mode.IF_MISSING).load();

        ObjectNode good = request();
        putAnswers(good.putArray("inputs"), InitialConfigurationTest.best(), null);
        JsonNode result = calculate(good);
        assertThat(result.get("scoreStatus").asText()).isEqualTo("VALID");
        assertThat(result.get("leadershipIntelligenceScore").decimalValue()).isEqualByComparingTo("100");
        assertThat(result.get("interpretation").asText()).isEqualTo("STRONG");
        assertThat(result.get("counts").get("matchedPatterns").asInt()).isZero();

        Map<String, Object> burnout = InitialConfigurationTest.best();
        burnout.put("TH01", 1);
        burnout.put("TH02", 5);
        ObjectNode risky = request();
        putAnswers(risky.putArray("inputs"), burnout, null);
        JsonNode burned = calculate(risky);
        assertThat(burned.get("counts").get("appliedPenalties").asInt()).isEqualTo(1);
        assertThat(burned.get("penalties").get(0).get("penaltyId").asText()).isEqualTo("TH-P01");
        assertThat(burned.get("dimensions").get("TEAM_HEALTH").get("effectivePenalty").decimalValue()).isEqualByComparingTo("12");

        // an unknown question is refused, so the questionnaire is exactly the configured one
        ObjectNode unknown = request();
        putAnswers(unknown.putArray("inputs"), Map.of("Q01", 3), null);
        mvc.perform(post("/api/v1/calculate").with(admin()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(unknown)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void scoresAnOrganizationAndItsTeamsFromMemberAnswersWithTheBundledConfiguration() throws Exception {
        tenant("init-a");
        loader(BUNDLED, Mode.IF_MISSING).load();

        ObjectNode body = request();
        ArrayNode responses = body.putArray("responses");
        for (int i = 0; i < 6; i++) {
            putAnswers(responses, InitialConfigurationTest.best(), "alpha");
        }
        for (int i = 0; i < 6; i++) {
            putAnswers(responses, InitialConfigurationTest.worst(), "beta");
        }
        for (int i = 0; i < 2; i++) {
            putAnswers(responses, InitialConfigurationTest.best(), "tiny");
        }
        JsonNode result = calculate(body);

        assertThat(result.get("scoreStatus").asText()).isEqualTo("VALID");
        JsonNode teams = result.get("teamRuns");
        assertThat(teams).hasSize(3);
        assertThat(teams.get(0).get("teamId").asText()).isEqualTo("alpha");
        assertThat(teams.get(0).get("leadershipIntelligenceScore").decimalValue()).isEqualByComparingTo("100");
        assertThat(teams.get(1).get("teamId").asText()).isEqualTo("beta");
        assertThat(teams.get(1).get("leadershipIntelligenceScore").decimalValue()).isEqualByComparingTo("0");
        assertThat(teams.get(2).get("teamId").asText()).isEqualTo("tiny");
        assertThat(teams.get(2).get("scoreStatus").asText()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(teams.get(2).has("leadershipIntelligenceScore")).isFalse();
    }
}
