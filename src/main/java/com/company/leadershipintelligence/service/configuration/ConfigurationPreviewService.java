package com.company.leadershipintelligence.service.configuration;

import com.company.leadershipintelligence.domain.enums.ConfigurationScope;
import com.company.leadershipintelligence.domain.enums.ResponseScope;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.ScopedResponse;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.persistence.entity.AssessmentRow;
import com.company.leadershipintelligence.repository.persistence.entity.SnapshotRow;
import com.company.leadershipintelligence.repository.persistence.mapper.AnswerJson;
import com.company.leadershipintelligence.repository.persistence.repository.AccessAuditStore;
import com.company.leadershipintelligence.repository.persistence.repository.AssessmentStore;
import com.company.leadershipintelligence.repository.persistence.repository.ConfigurationReviewStore;
import com.company.leadershipintelligence.repository.persistence.repository.ConfigurationReviewStore.FixtureRow;
import com.company.leadershipintelligence.service.EffectiveConfigurationService;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.service.query.ConfigurationViews.ConfigurationOutcome;
import com.company.leadershipintelligence.service.query.ConfigurationViews.Distribution;
import com.company.leadershipintelligence.service.query.ConfigurationViews.FixtureResultView;
import com.company.leadershipintelligence.service.query.ConfigurationViews.PreviewItem;
import com.company.leadershipintelligence.service.query.ConfigurationViews.PreviewResult;
import com.company.leadershipintelligence.service.query.ConfigurationViews.PreviewSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Preview and impact analysis of a configuration draft (specv3.md Sections 23.6 and 42.3, decisions D55 and D56). The draft is merged
 * over the layers it sits on and scored, in memory, against the fixtures of its layer and, when allowed, the caller's own tenant's
 * recent assessments, next to the configuration as it is now. Nothing is stored or logged and an answer never leaves this class: a
 * result is ids, scores, bands, patterns and penalties. The platform administrator belongs to no tenant, so a platform preview uses
 * fixtures only. Reading assessments is audited by action and count.
 */
@Service
public class ConfigurationPreviewService {

    private final EffectiveConfigurationService effective;
    private final ConfigurationEvaluator evaluator;
    private final ConfigurationReviewStore store;
    private final AssessmentStore assessments;
    private final AccessAuditStore audit;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean useRecentAssessments;
    private final int maxAssessments;

    public ConfigurationPreviewService(EffectiveConfigurationService effective, ConfigurationEvaluator evaluator, ConfigurationReviewStore store,
                                       AssessmentStore assessments, AccessAuditStore audit, ObjectMapper json, Clock clock,
                                       @Value("${lis.configuration.preview.use-recent-assessments:true}") boolean useRecentAssessments,
                                       @Value("${lis.configuration.preview.max-assessments:50}") int maxAssessments) {
        this.effective = effective;
        this.evaluator = evaluator;
        this.store = store;
        this.assessments = assessments;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
        this.useRecentAssessments = useRecentAssessments;
        this.maxAssessments = maxAssessments;
    }

    @Transactional
    public PreviewResult preview(Manager manager, ConfigurationVersionView version) {
        UUID layerId = version.configurationId();
        TenantId tenant = manager.caller().tenantId();
        Optional<String> candidate = Optional.of(version.payload());
        List<FixtureResultView> fixtures = runFixtures(manager, version);
        List<PreviewItem> items = new ArrayList<>();
        for (FixtureResultView f : fixtures) {
            ConfigurationOutcome before = outcomeOfFixture(tenant, layerId, Optional.empty(), f.name());
            items.add(new PreviewItem("FIXTURE", f.name(), null, before, f.outcome(), changes(before, f.outcome())));
        }
        int assessmentCount = 0;
        boolean used = useRecentAssessments && tenant != null;
        if (used) {
            Map<String, ScoringConfigurationSnapshot> beforeCache = new HashMap<>();
            Map<String, ScoringConfigurationSnapshot> afterCache = new HashMap<>();
            for (String id : recentAssessmentIds(manager, version)) {
                Optional<AssessmentRow> row = assessments.findAssessment(tenant.value(), id);
                Optional<SnapshotRow> snapshot = row.flatMap(r -> assessments.latestSnapshot(tenant.value(), id));
                if (row.isEmpty() || snapshot.isEmpty()) {
                    continue;
                }
                List<ScopedResponse> responses = assessments.answers(tenant.value(), snapshot.get().id()).stream().map(a ->
                        new ScopedResponse(a.questionId(), AnswerJson.read(a.answerJson()), ResponseScope.valueOf(a.scopeLevel()),
                                a.teamId().isEmpty() ? null : a.teamId(), a.declaredRespondents())).toList();
                String key = row.get().organizationId() + "|" + row.get().templateId();
                ConfigurationOutcome before = outcome(beforeCache, key, tenant, row.get(), layerId, Optional.empty(), responses);
                ConfigurationOutcome after = outcome(afterCache, key, tenant, row.get(), layerId, candidate, responses);
                items.add(new PreviewItem("ASSESSMENT", id, row.get().organizationId(), before, after, changes(before, after)));
                assessmentCount++;
            }
            audit.record(tenant.value(), manager.actor(), "CONFIGURATION_PREVIEW", null,
                    "{\"versionId\":\"" + version.id() + "\",\"assessments\":" + assessmentCount + ",\"fixtures\":" + fixtures.size() + "}", clock.instant());
        }
        return new PreviewResult(version.id(), summarize(items, fixtures, assessmentCount, used), items, fixtures);
    }

    /** The fixtures of the layer scored with the draft in place; one result each, never an exception. */
    @Transactional(readOnly = true)
    public List<FixtureResultView> runFixtures(Manager manager, ConfigurationVersionView version) {
        TenantId tenant = manager.caller().tenantId();
        ScoringConfigurationSnapshot after;
        String problem = null;
        try {
            after = effective.resolveWithCandidate(tenant, null, null, version.configurationId(), Optional.of(version.payload()));
        } catch (ScoringException e) {
            after = null;
            problem = e.code() + ": " + e.getMessage();
        }
        List<FixtureResultView> results = new ArrayList<>();
        for (FixtureRow row : store.fixtures(version.configurationId())) {
            ConfigurationOutcome outcome;
            FixtureSpec spec;
            try {
                spec = spec(row);
            } catch (IllegalStateException e) {
                results.add(new FixtureResultView(row.id(), row.name(), false, List.of("the fixture cannot be read"), ConfigurationEvaluator.error("the fixture cannot be read")));
                continue;
            }
            outcome = after == null ? ConfigurationEvaluator.error(problem) : evaluator.evaluate(after, ConfigurationEvaluator.answersOf(spec));
            List<String> failures = ConfigurationEvaluator.failures(outcome, spec.expectations());
            results.add(new FixtureResultView(row.id(), row.name(), failures.isEmpty(), failures, outcome));
        }
        return results;
    }

    // ---------------- internals ----------------

    private FixtureSpec spec(FixtureRow row) {
        try {
            return new FixtureSpec(row.name(), json.readValue(row.answersJson(), new TypeReference<List<FixtureSpec.Answer>>() {
            }), json.readValue(row.expectationsJson(), FixtureSpec.Expectations.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a stored fixture cannot be read", e);
        }
    }

    /** The assessments the preview may read: the caller's own organizations, and only the organization of an organization layer. */
    private List<String> recentAssessmentIds(Manager manager, ConfigurationVersionView version) {
        Collection<String> organizations = version.scope() == ConfigurationScope.ORGANIZATION
                ? (manager.scope().organizationIds().contains(version.scopeId()) ? List.of(version.scopeId()) : List.of())
                : manager.scope().organizationIds();
        return store.recentAssessmentIds(manager.caller().tenantId().value(), organizations, maxAssessments);
    }

    private ConfigurationOutcome outcomeOfFixture(TenantId tenant, UUID layerId, Optional<String> candidate, String name) {
        // the "before" of a fixture: the same answers against the configuration as it is now
        try {
            ScoringConfigurationSnapshot snapshot = effective.resolveWithCandidate(tenant, null, null, layerId, candidate);
            FixtureRow row = store.fixtures(layerId).stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
            return evaluator.evaluate(snapshot, ConfigurationEvaluator.answersOf(spec(row)));
        } catch (ScoringException e) {
            return ConfigurationEvaluator.error(e.code() + ": " + e.getMessage());
        }
    }

    private ConfigurationOutcome outcome(Map<String, ScoringConfigurationSnapshot> cache, String key, TenantId tenant, AssessmentRow row, UUID layerId,
                                         Optional<String> candidate, List<ScopedResponse> responses) {
        try {
            ScoringConfigurationSnapshot snapshot = cache.get(key);
            if (snapshot == null) {
                snapshot = effective.resolveWithCandidate(tenant, new OrganizationId(row.organizationId()), row.templateId(), layerId, candidate);
                cache.put(key, snapshot);
            }
            return evaluator.evaluate(snapshot, responses);
        } catch (ScoringException e) {
            return ConfigurationEvaluator.error(e.code() + ": " + e.getMessage());
        }
    }

    /** What differs between two outcomes of the same answers, in words, for the reader of the preview. */
    static List<String> changes(ConfigurationOutcome before, ConfigurationOutcome after) {
        List<String> changes = new ArrayList<>();
        if (!before.status().equals(after.status())) {
            changes.add("the result changes from " + before.status() + " to " + after.status());
        }
        if (before.score() != null && after.score() != null && before.score().compareTo(after.score()) != 0) {
            changes.add("the overall score changes from " + before.score().toPlainString() + " to " + after.score().toPlainString());
        }
        Set<String> appearing = new HashSet<>(after.patterns());
        appearing.removeAll(before.patterns());
        Set<String> disappearing = new HashSet<>(before.patterns());
        disappearing.removeAll(after.patterns());
        appearing.stream().sorted().forEach(p -> changes.add("pattern " + p + " is now detected"));
        disappearing.stream().sorted().forEach(p -> changes.add("pattern " + p + " is no longer detected"));
        after.penalties().forEach((id, amount) -> {
            BigDecimal was = before.penalties().get(id);
            if (was == null) {
                changes.add("penalty " + id + " now applies (" + amount.toPlainString() + ")");
            } else if (was.compareTo(amount) != 0) {
                changes.add("penalty " + id + " changes from " + was.toPlainString() + " to " + amount.toPlainString());
            }
        });
        before.penalties().keySet().stream().filter(id -> !after.penalties().containsKey(id)).sorted().forEach(id -> changes.add("penalty " + id + " no longer applies"));
        if (before.error() != null && after.error() == null) {
            changes.add("it could not be scored before and can now");
        } else if (before.error() == null && after.error() != null) {
            changes.add("it could be scored before and cannot now: " + after.error());
        }
        return changes;
    }

    private static PreviewSummary summarize(List<PreviewItem> items, List<FixtureResultView> fixtures, int assessmentCount, boolean used) {
        List<PreviewItem> subjects = items.stream().filter(i -> "ASSESSMENT".equals(i.kind())).toList();
        int appearing = 0;
        int disappearing = 0;
        int changed = 0;
        for (PreviewItem i : subjects) {
            Set<String> gained = new HashSet<>(i.after().patterns());
            gained.removeAll(i.before().patterns());
            Set<String> lost = new HashSet<>(i.before().patterns());
            lost.removeAll(i.after().patterns());
            appearing += gained.size();
            disappearing += lost.size();
            if (i.before().score() != null && i.after().score() != null && i.before().score().compareTo(i.after().score()) != 0) {
                changed++;
            }
        }
        return new PreviewSummary(fixtures.size(), (int) fixtures.stream().filter(FixtureResultView::passed).count(), assessmentCount, used,
                distribution(subjects.stream().map(PreviewItem::before).toList()), distribution(subjects.stream().map(PreviewItem::after).toList()),
                appearing, disappearing, changed);
    }

    static Distribution distribution(List<ConfigurationOutcome> outcomes) {
        List<BigDecimal> scores = outcomes.stream().map(ConfigurationOutcome::score).filter(s -> s != null).sorted().toList();
        Map<String, Integer> bands = new TreeMap<>();
        outcomes.stream().filter(o -> o.band() != null).forEach(o -> bands.merge(o.band(), 1, Integer::sum));
        if (scores.isEmpty()) {
            return new Distribution(0, null, null, null, bands);
        }
        int n = scores.size();
        BigDecimal median = n % 2 == 1 ? scores.get(n / 2) : scores.get(n / 2 - 1).add(scores.get(n / 2)).divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
        return new Distribution(n, scores.get(0), median, scores.get(n - 1), bands);
    }

    /** Kept for symmetry with the other services: a preview is refused for a layer the caller does not manage. */
    static void requireManaged(ConfigurationVersionView version) {
        if (version.scope() == ConfigurationScope.TEMPLATE) {
            throw new ScoringException(ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND, "configuration version not found", Map.of());
        }
    }
}
