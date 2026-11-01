package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.rule.RecommendationDefinition;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.persistence.repository.HistoryStore;
import com.company.leadershipintelligence.repository.persistence.repository.HistoryStore.ActionSourceRow;
import com.company.leadershipintelligence.service.command.OrganizationScope;
import com.company.leadershipintelligence.service.query.RecommendationViews.Recommendation;
import com.company.leadershipintelligence.service.query.RecommendationViews.RunRecommendations;
import com.company.leadershipintelligence.service.query.RecommendationViews.Source;
import com.company.leadershipintelligence.service.query.ScoringRunView;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Recommendations for a stored run (decision D28). The text comes only from the versioned catalog of the
 * configuration the run was scored with; nothing is generated and no score is touched. Order: severity of the
 * strongest source (highest first), the configured priority (lowest number first), then the key.
 */
@Service
public class RecommendationService {

    private final ScoreAssessmentService scoring;
    private final EffectiveConfigurationService configurations;
    private final HistoryStore store;

    public RecommendationService(ScoreAssessmentService scoring, EffectiveConfigurationService configurations, HistoryStore store) {
        this.scoring = scoring;
        this.configurations = configurations;
        this.store = store;
    }

    public RunRecommendations forRun(TenantId tenant, OrganizationScope scope, UUID runId) {
        ScoringRunView run = scoring.get(tenant, runId, scope);
        if (run.traceJson() == null) {
            return new RunRecommendations(runId, List.of(), List.of());
        }
        ScoringConfigurationSnapshot snapshot = configurations.resolveForRun(tenant, run.layersJson(), run.configurationHash());
        Map<String, List<ActionSourceRow>> byKey = new LinkedHashMap<>();
        for (ActionSourceRow row : store.actionSources(tenant.value(), runId)) {
            byKey.computeIfAbsent(row.actionKey(), k -> new ArrayList<>()).add(row);
        }
        List<Recommendation> found = new ArrayList<>();
        TreeSet<String> unmapped = new TreeSet<>();
        for (Map.Entry<String, List<ActionSourceRow>> entry : byKey.entrySet()) {
            RecommendationDefinition definition = snapshot.recommendations().get(entry.getKey());
            if (definition == null) {
                unmapped.add(entry.getKey());
                continue;
            }
            List<Source> sources = entry.getValue().stream().map(r -> new Source(r.sourceType(), r.sourceId(), r.severity())).toList();
            Severity strongest = entry.getValue().stream().map(r -> Severity.valueOf(r.severity())).max(Comparator.naturalOrder()).orElseThrow();
            found.add(new Recommendation(definition.key(), definition.title(), definition.description(), definition.priority(),
                    strongest.name(), sources));
        }
        found.sort(Comparator.comparing((Recommendation r) -> Severity.valueOf(r.severity())).reversed()
                .thenComparingInt(Recommendation::priority).thenComparing(Recommendation::key));
        return new RunRecommendations(runId, List.copyOf(found), List.copyOf(unmapped));
    }
}
