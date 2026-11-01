package com.company.leadershipintelligence.repository.configuration;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Strict reader for one JSON object of a configuration document. Every field read is recorded; {@link #done()}
 * rejects any field that was not read, so unknown fields are never ignored (specv3.md Section 23.3). Errors
 * carry the JSON path.
 */
final class DocumentFields {

    private final JsonNode node;
    private final String path;
    private final Set<String> used = new HashSet<>();

    private DocumentFields(JsonNode node, String path) {
        this.node = node;
        this.path = path;
    }

    static DocumentFields of(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw error(path + " must be an object");
        }
        return new DocumentFields(node, path);
    }

    static ScoringException error(String message) {
        return new ScoringException(ScoringErrorCode.CONFIGURATION_SNAPSHOT_INVALID, message);
    }

    String path() {
        return path;
    }

    boolean rawIsBoolean(String name) {
        JsonNode value = node.get(name);
        return value != null && value.isBoolean();
    }

    boolean has(String name) {
        return node.has(name) && !node.get(name).isNull();
    }

    private JsonNode required(String name) {
        used.add(name);
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            throw error(path + "." + name + " is required");
        }
        return value;
    }

    private Optional<JsonNode> optional(String name) {
        used.add(name);
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? Optional.empty() : Optional.of(value);
    }

    String text(String name) {
        JsonNode value = required(name);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw error(path + "." + name + " must be a non-blank string");
        }
        return value.asText();
    }

    Optional<String> optionalText(String name) {
        return optional(name).map(v -> {
            if (!v.isTextual() || v.asText().isBlank()) {
                throw error(path + "." + name + " must be a non-blank string");
            }
            return v.asText();
        });
    }

    BigDecimal decimal(String name) {
        return number(required(name), name);
    }

    Optional<BigDecimal> optionalDecimal(String name) {
        return optional(name).map(v -> number(v, name));
    }

    private BigDecimal number(JsonNode value, String name) {
        if (!value.isNumber()) {
            throw error(path + "." + name + " must be a number");
        }
        return value.decimalValue();
    }

    int integer(String name) {
        JsonNode value = required(name);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw error(path + "." + name + " must be an integer");
        }
        return value.intValue();
    }

    int optionalInteger(String name, int fallback) {
        return optional(name).map(v -> {
            if (!v.isIntegralNumber() || !v.canConvertToInt()) {
                throw error(path + "." + name + " must be an integer");
            }
            return v.intValue();
        }).orElse(fallback);
    }

    boolean bool(String name) {
        JsonNode value = required(name);
        if (!value.isBoolean()) {
            throw error(path + "." + name + " must be true or false");
        }
        return value.booleanValue();
    }

    boolean optionalBool(String name, boolean fallback) {
        return optional(name).map(v -> {
            if (!v.isBoolean()) {
                throw error(path + "." + name + " must be true or false");
            }
            return v.booleanValue();
        }).orElse(fallback);
    }

    <E extends Enum<E>> E enumeration(Class<E> type, String name) {
        return toEnum(type, name, text(name));
    }

    <E extends Enum<E>> Optional<E> optionalEnumeration(Class<E> type, String name) {
        return optionalText(name).map(v -> toEnum(type, name, v));
    }

    private <E extends Enum<E>> E toEnum(Class<E> type, String name, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw error(path + "." + name + " has unknown value '" + value + "'");
        }
    }

    DocumentFields object(String name) {
        return of(required(name), path + "." + name);
    }

    Optional<DocumentFields> optionalObject(String name) {
        return optional(name).map(v -> of(v, path + "." + name));
    }

    List<DocumentFields> objects(String name) {
        return objectList(required(name), name);
    }

    List<DocumentFields> optionalObjects(String name) {
        return optional(name).map(v -> objectList(v, name)).orElse(List.of());
    }

    private List<DocumentFields> objectList(JsonNode value, String name) {
        if (!value.isArray()) {
            throw error(path + "." + name + " must be an array");
        }
        List<DocumentFields> list = new ArrayList<>();
        for (int i = 0; i < value.size(); i++) {
            list.add(of(value.get(i), path + "." + name + "[" + i + "]"));
        }
        return list;
    }

    List<String> texts(String name) {
        return textList(required(name), name);
    }

    List<String> optionalTexts(String name) {
        return optional(name).map(v -> textList(v, name)).orElse(List.of());
    }

    private List<String> textList(JsonNode value, String name) {
        if (!value.isArray()) {
            throw error(path + "." + name + " must be an array");
        }
        List<String> list = new ArrayList<>();
        value.forEach(item -> {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw error(path + "." + name + " must contain non-blank strings");
            }
            list.add(item.asText());
        });
        return list;
    }

    List<BigDecimal> decimals(String name) {
        JsonNode value = required(name);
        if (!value.isArray()) {
            throw error(path + "." + name + " must be an array");
        }
        List<BigDecimal> list = new ArrayList<>();
        value.forEach(item -> list.add(number(item, name)));
        return list;
    }

    /** An object whose keys are free-form and whose values are numbers. */
    Map<String, BigDecimal> decimalMap(String name) {
        JsonNode value = required(name);
        if (!value.isObject()) {
            throw error(path + "." + name + " must be an object");
        }
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        value.properties().forEach(entry -> map.put(entry.getKey(), number(entry.getValue(), name + "." + entry.getKey())));
        return map;
    }

    /** An object whose keys are free-form and whose values are objects. */
    Map<String, DocumentFields> objectMap(String name) {
        JsonNode value = required(name);
        if (!value.isObject()) {
            throw error(path + "." + name + " must be an object");
        }
        Map<String, DocumentFields> map = new LinkedHashMap<>();
        value.properties().forEach(entry -> map.put(entry.getKey(), of(entry.getValue(), path + "." + name + "." + entry.getKey())));
        return map;
    }

    void done() {
        node.fieldNames().forEachRemaining(field -> {
            if (!used.contains(field)) {
                throw error(path + "." + field + " is not a known field");
            }
        });
    }
}
