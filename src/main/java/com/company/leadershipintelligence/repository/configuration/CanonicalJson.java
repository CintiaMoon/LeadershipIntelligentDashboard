package com.company.leadershipintelligence.repository.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical serialization and hash of a configuration (specv3.md Section 27.3): keys sorted, no whitespace,
 * numbers in plain normalized form (1.0 and 1 are the same), so equal content always gives the same text and
 * the same hash, whatever its original formatting.
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    public static String canonicalize(JsonNode node) {
        StringBuilder out = new StringBuilder();
        write(node, out);
        return out.toString();
    }

    /** {@code sha256:<64 hex digits>} of the canonical text. */
    public static String hash(JsonNode node) {
        return sha256(canonicalize(node));
    }

    public static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void write(JsonNode node, StringBuilder out) {
        if (node.isObject()) {
            Map<String, JsonNode> sorted = new TreeMap<>();
            node.properties().forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonNode> entry : sorted.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(quote(entry.getKey())).append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (node.isArray()) {
            out.append('[');
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(node.get(i), out);
            }
            out.append(']');
        } else if (node.isNumber()) {
            BigDecimal value = node.decimalValue();
            out.append(value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString());
        } else if (node.isTextual()) {
            out.append(quote(node.asText()));
        } else if (node.isBoolean()) {
            out.append(node.booleanValue());
        } else {
            out.append("null");
        }
    }

    private static String quote(String text) {
        try {
            return ConfigurationDocumentParser.MAPPER.writeValueAsString(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
