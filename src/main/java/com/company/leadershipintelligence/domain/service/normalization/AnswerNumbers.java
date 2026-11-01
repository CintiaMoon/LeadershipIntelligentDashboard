package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/** Converts numeric answers to BigDecimal without going through binary floating point arithmetic. */
final class AnswerNumbers {

    private AnswerNumbers() {
    }

    static BigDecimal toDecimal(Object answer, String questionId, ScoringErrorCode invalidValueCode) {
        if (answer instanceof BigDecimal value) {
            return value;
        }
        if (answer instanceof Integer || answer instanceof Long || answer instanceof Short || answer instanceof Byte) {
            return BigDecimal.valueOf(((Number) answer).longValue());
        }
        if (answer instanceof BigInteger value) {
            return new BigDecimal(value);
        }
        if (answer instanceof Double || answer instanceof Float) {
            double d = ((Number) answer).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new ScoringException(invalidValueCode, "answer is not a finite number", Map.of("questionId", questionId));
            }
            return new BigDecimal(answer.toString());
        }
        throw new ScoringException(ScoringErrorCode.INVALID_ANSWER_TYPE,
                "answer for question " + questionId + " has an incompatible type", Map.of("questionId", questionId));
    }
}
