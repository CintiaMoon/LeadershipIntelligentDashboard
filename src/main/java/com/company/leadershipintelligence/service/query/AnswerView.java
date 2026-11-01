package com.company.leadershipintelligence.service.query;

import java.util.List;

/**
 * The stored answers of one question in the unit a run was scored with. Only evidence roles may read these (decision
 * D19).
 *
 * <p>An organization or team answer has {@code answer} (and, if the caller declared one, {@code declaredRespondents}).
 * Member answers are never listed one by one: they come as one group with the number of answers and the values in
 * sorted order, which carries no submission order and no identity (decision D45). A group with fewer answers than the
 * configured minimum is {@code suppressed}: only its count is shown, because a very small group could point to a person.
 */
public record AnswerView(String questionId, Object answer, String scope, String teamId, Integer declaredRespondents,
                         Integer count, List<Object> values, Boolean suppressed) {

    /** One organization-level answer (the only kind before response scopes). */
    public AnswerView(String questionId, Object answer) {
        this(questionId, answer, "ORGANIZATION", null, null, null, null, null);
    }
}
