package com.company.leadershipintelligence.domain.rule;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Display text for configuration items (decision D39): names, descriptions and a plain-language explanation of why a
 * pattern was detected or a penalty deducted. It is versioned with the configuration, never used by scoring, and
 * the only source of such text for the frontend (decision D25). Keys are pattern ids, penalty ids, dimension names,
 * signal ids and question ids; every key is checked against the configuration it belongs to.
 */
public record PresentationCatalog(Map<String, Entry> patterns, Map<String, Entry> penalties, Map<String, Entry> dimensions,
                                  Map<String, Entry> signals, Map<String, Entry> questions) {

    public record Entry(String name, String description, String explanation) {

        public Entry {
            if (name == null || name.isBlank() || description == null || description.isBlank()) {
                throw new IllegalArgumentException("a presentation entry needs a name and a description");
            }
            if (explanation != null && explanation.isBlank()) {
                throw new IllegalArgumentException("a presentation explanation must not be blank");
            }
        }
    }

    public PresentationCatalog {
        patterns = sorted(patterns);
        penalties = sorted(penalties);
        dimensions = sorted(dimensions);
        signals = sorted(signals);
        questions = sorted(questions);
    }

    public static PresentationCatalog empty() {
        return new PresentationCatalog(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static Map<String, Entry> sorted(Map<String, Entry> map) {
        if (map == null) {
            throw new IllegalArgumentException("presentation sections are required");
        }
        return Collections.unmodifiableSortedMap(new TreeMap<>(map));
    }
}
