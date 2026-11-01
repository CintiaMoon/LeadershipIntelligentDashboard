package com.company.leadershipintelligence.repository.persistence.mapper;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/**
 * Stores an answer as JSON and reads it back as a number (BigDecimal), text, boolean, a list of options or null. A list is
 * stored as a sorted set of option identifiers, so the stored form does not depend on the order it was sent in.
 */
public final class AnswerJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private AnswerJson() {
    }

    /** The most options a selection may hold. */
    public static final int MAX_SELECTION = 100;

    public static String write(String questionId, Object answer) {
        try {
            if (answer instanceof java.util.Collection<?> selection) {
                java.util.SortedSet<String> options = new java.util.TreeSet<>();
                for (Object element : selection) {
                    if (!(element instanceof CharSequence option)) {
                        throw new ScoringException(ScoringErrorCode.INVALID_ANSWER_TYPE,
                                "the answer for question " + questionId + " holds something other than option identifiers",
                                Map.of("questionId", questionId));
                    }
                    options.add(option.toString());
                }
                if (options.size() > MAX_SELECTION) {
                    throw new ScoringException(ScoringErrorCode.INVALID_ANSWER_TYPE,
                            "the answer for question " + questionId + " has too many options", Map.of("questionId", questionId));
                }
                return MAPPER.writeValueAsString(options);
            }
            if (answer == null || answer instanceof Boolean || answer instanceof CharSequence || answer instanceof BigDecimal
                    || answer instanceof BigInteger || answer instanceof Integer || answer instanceof Long || answer instanceof Short
                    || answer instanceof Byte || (answer instanceof Double d && Double.isFinite(d))
                    || (answer instanceof Float f && Float.isFinite(f))) {
                return MAPPER.writeValueAsString(answer instanceof CharSequence ? answer.toString() : answer);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        throw new ScoringException(ScoringErrorCode.INVALID_ANSWER_TYPE, "the answer for question " + questionId + " has an unsupported type",
                Map.of("questionId", questionId));
    }

    public static Object read(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node.isNull()) {
                return null;
            }
            if (node.isBoolean()) {
                return node.booleanValue();
            }
            if (node.isArray()) {
                java.util.List<String> options = new java.util.ArrayList<>();
                node.forEach(element -> options.add(element.asText()));
                return java.util.List.copyOf(options);
            }
            if (node.isNumber()) {
                return node.decimalValue();
            }
            return node.asText();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a stored answer is not valid JSON", e);
        }
    }
}
