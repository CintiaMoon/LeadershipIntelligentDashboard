package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.AggregationMethod;
import com.company.leadershipintelligence.domain.enums.ResponseScope;

/**
 * How one question's answers became one normalized value (decision D45). It is kept in the trace so a reader can
 * see the method, the number of answers used and where the respondent count came from. It never carries a single
 * respondent's answer.
 *
 * @param respondents   the respondent count, or null when participation is unknown
 * @param participation OBSERVED (counted member answers), DECLARED (caller-supplied) or UNKNOWN
 * @param usedAnswers   answers that carried a usable value
 * @param belowMinimum  the count was under the minimum, so the question was treated as missing
 */
public record ResponseSummary(
        String questionId,
        ResponseScope scope,
        AggregationMethod method,
        Integer respondents,
        Participation participation,
        int usedAnswers,
        int minimum,
        boolean belowMinimum) {

    public enum Participation { OBSERVED, DECLARED, UNKNOWN }
}
