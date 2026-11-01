package com.company.leadershipintelligence.service.query;

import java.util.List;
import java.util.UUID;

/** The recommendations of one stored run, from the configuration catalog it was scored with (decision D28). */
public final class RecommendationViews {

    private RecommendationViews() {
    }

    public record Source(String type, String id, String severity) {
    }

    public record Recommendation(String key, String title, String description, int priority, String severity, List<Source> sources) {
    }

    public record RunRecommendations(UUID scoringRunId, List<Recommendation> recommendations, List<String> unmappedActionKeys) {
    }
}
