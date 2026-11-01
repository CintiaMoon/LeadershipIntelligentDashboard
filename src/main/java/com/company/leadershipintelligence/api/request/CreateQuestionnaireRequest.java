package com.company.leadershipintelligence.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /questionnaires} and of {@code POST /questionnaires/from-initial} (decision D47): a name, nothing else. */
public record CreateQuestionnaireRequest(@NotBlank @Size(max = 200) String name) {
}
