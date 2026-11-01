package com.company.leadershipintelligence.service.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Display text and score bands of the configuration a run was scored with (decision D39). */
public final class CatalogViews {

    private CatalogViews() {
    }

    public record Entry(String name, String description, String explanation) {
    }

    public record Band(String label, BigDecimal min, BigDecimal max) {
    }

    public record RunCatalog(UUID scoringRunId, Map<String, Entry> patterns, Map<String, Entry> penalties, Map<String, Entry> dimensions,
                             Map<String, Entry> signals, Map<String, Entry> questions, List<Band> lisBands, List<Band> dimensionBands) {
    }
}
