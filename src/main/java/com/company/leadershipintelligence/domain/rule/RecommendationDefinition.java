package com.company.leadershipintelligence.domain.rule;

/**
 * One entry of the versioned recommendation catalog (decision D28): the text shown for an action key that a matched
 * pattern or a detected imbalance refers to. Lower {@code priority} numbers come first. No text is ever generated.
 */
public record RecommendationDefinition(String key, String title, String description, int priority) {

    public RecommendationDefinition {
        if (key == null || key.isBlank() || title == null || title.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("a recommendation needs a key, a title and a description");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("recommendation priority must not be negative: " + key);
        }
    }
}
