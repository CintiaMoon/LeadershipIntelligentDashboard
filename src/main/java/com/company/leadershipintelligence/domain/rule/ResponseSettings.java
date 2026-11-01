package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.AggregationMethod;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

import java.math.BigDecimal;

/**
 * Versioned settings for answers from several respondents (specv3.md Sections 4.11, 4.12, 14.2.1; decision D45).
 * Every default is unvalidated and part of the configuration hash when the document sets it.
 *
 * @param defaultMethod           how several answers to one question are combined unless the question says otherwise
 * @param minResponses            fewer responses than this make the question missing (anonymity and sufficiency)
 * @param signalShare             share of responses (0 exclusive to 1 inclusive) that must carry a text or flag signal
 * @param fullConfidenceResponses respondents needed for the participation factor to reach 1
 */
public record ResponseSettings(AggregationMethod defaultMethod, int minResponses, BigDecimal signalShare,
                               int fullConfidenceResponses) {

    public ResponseSettings {
        if (defaultMethod == null || signalShare == null) {
            throw invalid("defaultMethod and signalShare are required");
        }
        if (minResponses < 1) {
            throw invalid("minResponses must be at least 1 but was " + minResponses);
        }
        if (signalShare.signum() <= 0 || signalShare.compareTo(BigDecimal.ONE) > 0) {
            throw invalid("signalShare must be above 0 and at most 1 but was " + signalShare);
        }
        if (fullConfidenceResponses < minResponses) {
            throw invalid("fullConfidenceResponses (" + fullConfidenceResponses + ") must not be below minResponses ("
                    + minResponses + ")");
        }
    }

    /** The unvalidated defaults: MEDIAN, 5 responses, share 0.5, full participation confidence at 10 respondents. */
    public static ResponseSettings defaults() {
        return new ResponseSettings(AggregationMethod.MEDIAN, 5, new BigDecimal("0.5"), 10);
    }

    private static ScoringException invalid(String message) {
        return new ScoringException(ScoringErrorCode.INVALID_RESPONSE_CONFIGURATION, message);
    }
}
