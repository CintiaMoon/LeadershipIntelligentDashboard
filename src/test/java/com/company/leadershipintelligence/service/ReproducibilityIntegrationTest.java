package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.CalculateCommand;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.service.query.ScoringRunView;
import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Same inputs + same configuration + same engine version = same result, across tenants and months (V3 Section 38.3). */
class ReproducibilityIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    ConfigurationService configurations;

    @Autowired
    ScoreAssessmentService scoring;

    @Autowired
    ObjectMapper json;

    private static final List<ScoringInput> INPUTS = List.of(new ScoringInput("Q01", 2), new ScoringInput("Q02", "OFTEN"),
            new ScoringInput("Q03", true), new ScoringInput("Q04", "only one key person"), new ScoringInput("Q05", 80));

    private void platformDefault() {
        Caller platform = Caller.platform("ops");
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "p", ConfigurationSamples.sampleText());
        configurations.submitForValidation(platform, draft.id());
        configurations.activate(platform, draft.id(), new Approval("approver", null));
    }

    private ScoringRunView calculate(String tenant) {
        return scoring.calculate(new CalculateCommand(new TenantId(tenant), new OrganizationId("org-1"), "tpl-1", 1, "COMPLETED",
                Instant.parse("2026-11-01T09:00:00Z"), INPUTS, null));
    }

    private JsonNode trace(ScoringRunView run) throws Exception {
        return json.readTree(run.traceJson());
    }

    @Test
    void identicalAssessmentsInDifferentTenantsGiveIdenticalFactsAndTraces() throws Exception {
        tenant("rep-a");
        tenant("rep-b");
        platformDefault();
        ScoringRunView a = calculate("rep-a");
        ScoringRunView b = calculate("rep-b");
        assertThat(b.configurationHash()).isEqualTo(a.configurationHash());
        assertThat(b.finalScore()).isEqualByComparingTo(a.finalScore());
        assertThat(b.interpretation()).isEqualTo(a.interpretation());
        assertThat(trace(b)).isEqualTo(trace(a));
    }

    @Test
    void recalculatingMonthsLaterWithTheSameVersionsReproducesTheStoredTrace() throws Exception {
        tenant("rep-c");
        platformDefault();
        ScoringRunView original = calculate("rep-c");
        clock.set(Instant.parse("2027-06-01T10:00:00Z"));
        ScoringRunView replay = scoring.recalculate(new TenantId("rep-c"), new AssessmentId(original.assessmentId()), null);

        assertThat(replay.id()).isNotEqualTo(original.id());
        assertThat(replay.inputHash()).isEqualTo(original.inputHash());
        assertThat(replay.configurationHash()).isEqualTo(original.configurationHash());
        assertThat(replay.finalScore()).isEqualByComparingTo(original.finalScore());
        assertThat(trace(replay)).isEqualTo(trace(original));
    }
}
