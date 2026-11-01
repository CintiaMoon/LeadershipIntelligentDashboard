package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.CalculateCommand;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import com.company.leadershipintelligence.support.StatementCountingConfiguration;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Persisting a run must not issue statements per answer or per finding (V3 Section 38.3 query count). */
@Import(StatementCountingConfiguration.class)
class ScoringStatementCountIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    ConfigurationService configurations;

    @Autowired
    ScoreAssessmentService scoring;

    private String documentWithQuestions(int extra) {
        ObjectNode doc = ConfigurationSamples.sampleDocument();
        ArrayNode questions = (ArrayNode) doc.get("questions");
        ArrayNode mappings = (ArrayNode) doc.get("dimensions").get("mappings");
        String[] dims = {"TEAM_HEALTH", "DELIVERY_STABILITY", "TECHNICAL_RISK", "LEADERSHIP_LOAD"};
        for (int i = 0; i < extra; i++) {
            String id = String.format("X%03d", i);
            ObjectNode q = questions.addObject();
            q.put("id", id).put("type", "SCALE").put("missingPolicy", "IGNORE");
            q.putObject("normalization").put("min", 1).put("max", 5).put("direction", "POSITIVE")
                    .put("outOfRangePolicy", "REJECT").put("allowDecimals", true);
            mappings.addObject().put("question", id).put("dimension", dims[i % 4]).put("weight", 1.0);
        }
        return doc.toString();
    }

    private void activate(String payload) {
        Caller platform = Caller.platform("ops");
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "platform", payload);
        configurations.submitForValidation(platform, draft.id());
        configurations.activate(platform, draft.id(), new Approval("approver", null));
    }

    private long statementsFor(int extra) {
        List<ScoringInput> inputs = new ArrayList<>(List.of(new ScoringInput("Q01", 2), new ScoringInput("Q02", "OFTEN"),
                new ScoringInput("Q03", true), new ScoringInput("Q04", "only one key person"), new ScoringInput("Q05", 80)));
        for (int i = 0; i < extra; i++) {
            inputs.add(new ScoringInput(String.format("X%03d", i), 1 + (i % 5)));
        }
        long before = StatementCountingConfiguration.STATEMENTS.get();
        scoring.calculate(new CalculateCommand(new TenantId("count-t"), new OrganizationId("org-1"), "tpl-1", 1, "COMPLETED",
                Instant.parse("2026-11-01T09:00:00Z"), inputs, null));
        return StatementCountingConfiguration.STATEMENTS.get() - before;
    }

    @Test
    void theNumberOfStatementsDoesNotGrowWithTheNumberOfAnswers() {
        tenant("count-t");
        activate(documentWithQuestions(50));
        statementsFor(0); // warm the configuration cache so only the per-run work is measured
        long small = statementsFor(0);
        long large = statementsFor(50);
        System.out.println("STATEMENTS per run: 5 answers = " + small + ", 55 answers = " + large);
        assertThat(large).as("statements for 55 answers vs 5").isEqualTo(small);
        assertThat(small).as("fixed upper bound for one scoring run").isLessThanOrEqualTo(80);
    }
}
