package com.company.leadershipintelligence.service.configuration;

import com.company.leadershipintelligence.config.properties.EngineProperties;
import com.company.leadershipintelligence.domain.enums.PatternStatus;
import com.company.leadershipintelligence.domain.enums.PenaltyStatus;
import com.company.leadershipintelligence.domain.enums.ResponseScope;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.AdjustedDimension;
import com.company.leadershipintelligence.domain.model.PatternEvaluation;
import com.company.leadershipintelligence.domain.model.PenaltyResult;
import com.company.leadershipintelligence.domain.model.ScopedResponse;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringContext;
import com.company.leadershipintelligence.domain.model.ScoringResult;
import com.company.leadershipintelligence.domain.service.ScoringOrchestrator;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.service.configuration.FixtureSpec.Expectations;
import com.company.leadershipintelligence.service.configuration.FixtureSpec.Range;
import com.company.leadershipintelligence.service.query.ConfigurationViews.ConfigurationOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Scores answers against a configuration without storing anything (decisions D55, D56): the engine runs on a throw-away context, and
 * what comes back is a small outcome of numbers and ids. It is the single place a preview and a fixture are evaluated, so both see
 * exactly what a real run would. It never persists, never logs and never returns an answer. Scoring problems of one case (a question
 * the configuration does not have, an answer that does not fit) are an outcome with status ERROR, never an exception, so one broken
 * case does not hide the others.
 */
@Component
public class ConfigurationEvaluator {

    public static final String ERROR = "ERROR";

    private static final TenantId PREVIEW_TENANT = new TenantId("preview");
    private static final OrganizationId PREVIEW_ORGANIZATION = new OrganizationId("preview");

    private final ScoringOrchestrator engine;
    private final EngineProperties properties;

    public ConfigurationEvaluator(ScoringOrchestrator engine, EngineProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    /** The answers of a fixture, as organization-level answers (the earlier form: one answer per question). */
    public static List<ScopedResponse> answersOf(FixtureSpec fixture) {
        return fixture.answers().stream().map(a -> ScopedResponse.organization(a.questionId(), a.answer())).toList();
    }

    public ConfigurationOutcome evaluate(ScoringConfigurationSnapshot configuration, List<ScopedResponse> responses) {
        try {
            ScoringContext context = new ScoringContext(PREVIEW_TENANT, PREVIEW_ORGANIZATION, new AssessmentId("preview"),
                    new AssessmentSnapshotId("preview"), configuration.version(), properties.version(), properties.algorithmVersion());
            boolean scoped = responses.stream().anyMatch(r -> r.scope() != ResponseScope.ORGANIZATION);
            ScoringResult result = scoped
                    ? engine.scoreResponses(context, responses, null, configuration)
                    : engine.score(context, responses.stream().map(ScopedResponse::asInput).toList(), configuration);
            return outcomeOf(result);
        } catch (ScoringException e) {
            return error(e.code() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            return error("scoring failed unexpectedly");
        }
    }

    public static ConfigurationOutcome error(String message) {
        return new ConfigurationOutcome(ERROR, null, null, Map.of(), List.of(), Map.of(), message);
    }

    private static ConfigurationOutcome outcomeOf(ScoringResult result) {
        Map<String, BigDecimal> dimensions = new TreeMap<>();
        for (AdjustedDimension d : result.overall().dimensions()) {
            if (d.adjustedScore() != null) {
                dimensions.put(d.dimension().name(), d.adjustedScore().value());
            }
        }
        List<String> patterns = new ArrayList<>();
        for (PatternEvaluation p : result.facts().patterns()) {
            if (p.status() == PatternStatus.MATCHED) {
                patterns.add(p.patternId());
            }
        }
        patterns.sort(String::compareTo);
        Map<String, BigDecimal> penalties = new TreeMap<>();
        for (PenaltyResult p : result.facts().penalties().results()) {
            if (p.status() == PenaltyStatus.APPLIED) {
                penalties.merge(p.definition().penaltyId(), p.effectiveDeduction().value(), BigDecimal::add);
            }
        }
        BigDecimal score = result.overall().finalScore() == null ? null : result.overall().finalScore().value();
        String band = result.facts().lisInterpretation() == null ? null : result.facts().lisInterpretation().label();
        return new ConfigurationOutcome(result.status().name(), score, band, dimensions, patterns, penalties, null);
    }

    /** What an outcome fails to meet; empty when every expectation holds. */
    public static List<String> failures(ConfigurationOutcome outcome, Expectations expected) {
        List<String> failures = new ArrayList<>();
        if (ERROR.equals(outcome.status())) {
            failures.add("it could not be scored: " + outcome.error());
            return failures;
        }
        if (expected == null) {
            return failures;
        }
        if (expected.status() != null && !expected.status().equals(outcome.status())) {
            failures.add("the status is " + outcome.status() + ", expected " + expected.status());
        }
        if (expected.overall() != null) {
            check("the overall score", outcome.score(), expected.overall(), failures);
        }
        expected.dimensions().forEach((dimension, range) -> check("the " + dimension + " score", outcome.dimensions().get(dimension), range, failures));
        for (String id : expected.patternsMatched()) {
            if (!outcome.patterns().contains(id)) {
                failures.add("pattern " + id + " was expected and was not detected");
            }
        }
        for (String id : expected.patternsNotMatched()) {
            if (outcome.patterns().contains(id)) {
                failures.add("pattern " + id + " was not expected and was detected");
            }
        }
        return failures;
    }

    private static void check(String what, BigDecimal value, Range range, List<String> failures) {
        if (value == null) {
            failures.add(what + " is missing");
        } else if (range.min() != null && value.compareTo(range.min()) < 0) {
            failures.add(what + " is " + value.toPlainString() + ", below the expected " + range.min().toPlainString());
        } else if (range.max() != null && value.compareTo(range.max()) > 0) {
            failures.add(what + " is " + value.toPlainString() + ", above the expected " + range.max().toPlainString());
        }
    }
}
