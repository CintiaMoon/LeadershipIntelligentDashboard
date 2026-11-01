package com.company.leadershipintelligence.repository.persistence.entity;

/**
 * One stored answer as JSON text. Raw answers may be sensitive and are never logged. {@code scopeLevel} says who the
 * answer represents; {@code teamId} is empty when it names no team; {@code ordinal} only distinguishes several
 * member answers (a member has no identity, decision D45).
 */
public record AnswerRow(String questionId, String answerJson, String scopeLevel, String teamId, int ordinal, Integer declaredRespondents) {

    /** An organization-level answer, the only kind before response scopes. */
    public AnswerRow(String questionId, String answerJson) {
        this(questionId, answerJson, "ORGANIZATION", "", 0, null);
    }
}
