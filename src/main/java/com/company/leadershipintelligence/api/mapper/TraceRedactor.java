package com.company.leadershipintelligence.api.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes per-question and per-signal values for callers who may see results but not the evidence behind them
 * (the VIEWER role, decision D19; specv3.md Section 19.5). Dimension-level results, findings, statuses, reasons and
 * penalties stay. The input is never modified; a copy is returned.
 */
public final class TraceRedactor {

    private TraceRedactor() {
    }

    public static JsonNode trace(JsonNode trace) {
        ObjectNode copy = trace.deepCopy();
        ArrayNode kept = copy.arrayNode();
        for (JsonNode step : copy.withArray("steps")) {
            String stage = step.path("stage").asText();
            if (stage.equals("NORMALIZATION") || stage.equals("SIGNALS")) {
                continue;
            }
            switch (stage) {
                case "RAW_DIMENSIONS" -> redactDimensions(step.path("dimensions"));
                case "PATTERNS" -> redactPatterns(step.path("patterns"));
                case "PENALTIES" -> redactPenalties(step.path("aggregation").path("results"));
                default -> { }
            }
            kept.add(step);
        }
        copy.set("steps", kept);
        return copy;
    }

    /** The dimension list of the RAW_DIMENSIONS step without contributions and drivers. */
    public static JsonNode dimensions(JsonNode dimensions) {
        JsonNode copy = dimensions.deepCopy();
        redactDimensions(copy);
        return copy;
    }

    public static JsonNode patterns(JsonNode patterns) {
        JsonNode copy = patterns.deepCopy();
        redactPatterns(copy);
        return copy;
    }

    public static JsonNode penalties(JsonNode penalties) {
        JsonNode copy = penalties.deepCopy();
        redactPenalties(copy);
        return copy;
    }

    private static void redactDimensions(JsonNode dimensions) {
        for (JsonNode dimension : dimensions) {
            ObjectNode entry = (ObjectNode) dimension;
            entry.remove("positiveDrivers");
            entry.remove("negativeDrivers");
            if (entry.has("score") && entry.get("score").isObject()) {
                ((ObjectNode) entry.get("score")).remove("contributions");
            } else {
                entry.remove("contributions");
            }
        }
    }

    private static void redactPatterns(JsonNode patterns) {
        for (JsonNode pattern : patterns) {
            ObjectNode entry = (ObjectNode) pattern;
            for (JsonNode evidence : entry.path("evidence")) {
                ObjectNode item = (ObjectNode) evidence;
                item.remove("numericValue");
                item.remove("flag");
                item.remove("sourceQuestionIds");
            }
            stripDetails(entry.path("conditionResult"));
        }
    }

    /** Condition details read like "signal:X=80 GTE 75"; they carry values, so only operators and outcomes remain. */
    private static void stripDetails(JsonNode condition) {
        if (condition instanceof ObjectNode node) {
            node.remove("detail");
            List<JsonNode> children = new ArrayList<>();
            node.path("children").forEach(children::add);
            children.forEach(TraceRedactor::stripDetails);
        }
    }

    private static void redactPenalties(JsonNode results) {
        for (JsonNode result : results) {
            ((ObjectNode) result).remove("scoreBaseValue");
        }
    }
}
