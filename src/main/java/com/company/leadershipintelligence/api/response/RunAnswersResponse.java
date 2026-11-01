package com.company.leadershipintelligence.api.response;

import com.company.leadershipintelligence.service.query.AnswerView;

import java.util.List;
import java.util.UUID;

/** The stored answers of a run. Never part of the result, the explanation or any log (specv3.md Sections 19.5, 33.3). */
public record RunAnswersResponse(UUID scoringRunId, List<AnswerView> answers) {
}
