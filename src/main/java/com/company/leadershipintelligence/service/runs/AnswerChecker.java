package com.company.leadershipintelligence.service.runs;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.service.questionnaire.QuestionKind;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.OptionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.QuestionSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Checks a respondent's answer against the question it answers, so that an answer which is stored can always be scored. An answer
 * that does not fit is refused when it is given, never at the moment a run is closed. The checks mirror what the generated
 * configuration accepts: a rating is a whole number from 1 to 5, a Likert answer from 1 to its points, a score from 0 to 10, a choice
 * is a known option key, several choices are distinct known keys, yes/no is a boolean, a number is any finite number (the
 * engine clamps it to the question's range) and free text is bounded text.
 */
final class AnswerChecker {

    static final int MAX_TEXT = 4000;
    private static final BigDecimal LIMIT = new BigDecimal("1000000000000");

    private AnswerChecker() {
    }

    /** The answer to store, or null when it counts as no answer (null, empty selection, blank text). */
    static JsonNode check(QuestionSpec q, JsonNode answer) {
        if (answer == null || answer.isNull()) {
            return null;
        }
        return switch (q.kind()) {
            case RATING -> whole(q, answer, 1, 5);
            case LIKERT -> whole(q, answer, 1, q.points() == null ? 5 : q.points());
            case NPS -> whole(q, answer, 0, 10);
            case NUMERIC -> number(q, answer);
            case YES_NO -> {
                if (!answer.isBoolean()) {
                    throw invalid(q, "must be true or false");
                }
                yield answer;
            }
            case SINGLE_CHOICE -> {
                if (!answer.isTextual() || !keys(q).contains(answer.asText())) {
                    throw invalid(q, "must be one of the options");
                }
                yield answer;
            }
            case MULTI_CHOICE -> many(q, answer);
            case FREE_TEXT -> {
                if (!answer.isTextual() || answer.asText().length() > MAX_TEXT) {
                    throw invalid(q, "must be text of at most " + MAX_TEXT + " characters");
                }
                yield answer.asText().isBlank() ? null : answer;
            }
        };
    }

    private static JsonNode whole(QuestionSpec q, JsonNode answer, int min, int max) {
        if (!answer.isIntegralNumber() || !answer.canConvertToInt() || answer.asInt() < min || answer.asInt() > max) {
            throw invalid(q, "must be a whole number from " + min + " to " + max);
        }
        return answer;
    }

    private static JsonNode number(QuestionSpec q, JsonNode answer) {
        if (!answer.isNumber() || answer.decimalValue().abs().compareTo(LIMIT) > 0) {
            throw invalid(q, "must be a number");
        }
        return answer;
    }

    private static JsonNode many(QuestionSpec q, JsonNode answer) {
        if (!answer.isArray()) {
            throw invalid(q, "must be a list of options");
        }
        Set<String> allowed = keys(q);
        Set<String> chosen = new HashSet<>();
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (JsonNode item : answer) {
            if (!item.isTextual() || !allowed.contains(item.asText())) {
                throw invalid(q, "must only contain options of the question");
            }
            if (chosen.add(item.asText())) {
                result.add(item.asText());
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static Set<String> keys(QuestionSpec q) {
        Set<String> keys = new HashSet<>();
        for (OptionSpec option : q.options()) {
            keys.add(option.key());
        }
        return keys;
    }

    /** Whether a question of this kind takes part in scoring (free text never does). */
    static boolean scored(QuestionSpec q) {
        return q.kind() != QuestionKind.FREE_TEXT;
    }

    private static ScoringException invalid(QuestionSpec q, String message) {
        return new ScoringException(ScoringErrorCode.RUN_INVALID, "the answer to question " + q.id() + " " + message, Map.of("questionId", q.id()));
    }
}
