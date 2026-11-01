package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.rule.InterpretationConfig;
import com.company.leadershipintelligence.domain.rule.PresentationCatalog;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.service.command.OrganizationScope;
import com.company.leadershipintelligence.service.query.CatalogViews.Band;
import com.company.leadershipintelligence.service.query.CatalogViews.Entry;
import com.company.leadershipintelligence.service.query.CatalogViews.RunCatalog;
import com.company.leadershipintelligence.service.query.ScoringRunView;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The display catalog and score bands of the configuration a stored run was scored with (decision D39). Rebuilt from
 * the run's own layer versions (hash-checked), so a later catalog change never rewrites the words shown for an
 * older run. Nothing here influences or recalculates a score.
 */
@Service
public class CatalogService {

    private final ScoreAssessmentService scoring;
    private final EffectiveConfigurationService configurations;

    public CatalogService(ScoreAssessmentService scoring, EffectiveConfigurationService configurations) {
        this.scoring = scoring;
        this.configurations = configurations;
    }

    public RunCatalog forRun(TenantId tenant, OrganizationScope scope, UUID runId) {
        ScoringRunView run = scoring.get(tenant, runId, scope);
        ScoringConfigurationSnapshot snapshot = configurations.resolveForRun(tenant, run.layersJson(), run.configurationHash());
        PresentationCatalog p = snapshot.presentation();
        return new RunCatalog(runId, entries(p.patterns()), entries(p.penalties()), entries(p.dimensions()), entries(p.signals()),
                entries(p.questions()), bands(snapshot.overall().lisInterpretation()), bands(snapshot.overall().dimensionInterpretation()));
    }

    private static Map<String, Entry> entries(Map<String, PresentationCatalog.Entry> source) {
        Map<String, Entry> result = new LinkedHashMap<>();
        source.forEach((key, e) -> result.put(key, new Entry(e.name(), e.description(), e.explanation())));
        return result;
    }

    private static List<Band> bands(InterpretationConfig config) {
        if (config == null) {
            return List.of();
        }
        return config.ranges().stream().map(r -> new Band(r.label(), r.min(), r.max())).toList();
    }
}
