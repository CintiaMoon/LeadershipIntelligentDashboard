package com.company.leadershipintelligence.repository.configuration;

import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Field-level merge of configuration layers, lowest precedence first (specv3.md Sections 23.2, 23.3):
 * objects merge recursively; scalars are replaced; arrays are replaced as a whole (no generic array or
 * catalog merge, so a layer that overrides a list restates it); an object {@code {"$remove": true}} deletes
 * the field it replaces; an object {@code {"$replace": X}} replaces the field with {@code X} instead of merging
 * (specv3.md Section 23.3, decision D47), also when no lower layer has the field and also as a whole layer.
 * Inputs are never modified. Unknown fields are rejected later, by the parser.
 */
@Component
public final class ConfigurationMerger {

    public static final String REMOVE_MARKER = "$remove";
    public static final String REPLACE_MARKER = "$replace";

    public JsonNode merge(List<JsonNode> layersLowestFirst) {
        if (layersLowestFirst.isEmpty()) {
            throw new IllegalArgumentException("at least one configuration layer is required");
        }
        JsonNode first = layersLowestFirst.get(0);
        JsonNode result = isReplacement(first) ? first.get(REPLACE_MARKER).deepCopy() : first.deepCopy();
        for (int i = 1; i < layersLowestFirst.size(); i++) {
            result = overlay(result, layersLowestFirst.get(i));
        }
        return result;
    }

    private JsonNode overlay(JsonNode base, JsonNode over) {
        if (isReplacement(over)) {
            return over.get(REPLACE_MARKER).deepCopy();
        }
        if (!base.isObject() || !over.isObject()) {
            return over.deepCopy();
        }
        ObjectNode merged = JsonNodeFactory.instance.objectNode();
        base.properties().forEach(entry -> merged.set(entry.getKey(), entry.getValue().deepCopy()));
        over.properties().forEach(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (isRemoval(value)) {
                merged.remove(key);
            } else if (isReplacement(value)) {
                merged.set(key, value.get(REPLACE_MARKER).deepCopy());
            } else if (merged.has(key)) {
                merged.set(key, overlay(merged.get(key), value));
            } else {
                merged.set(key, value.deepCopy());
            }
        });
        return merged;
    }

    private boolean isRemoval(JsonNode value) {
        return value.isObject() && value.size() == 1 && value.path(REMOVE_MARKER).asBoolean(false);
    }

    private boolean isReplacement(JsonNode value) {
        return value.isObject() && value.size() == 1 && value.has(REPLACE_MARKER);
    }
}
