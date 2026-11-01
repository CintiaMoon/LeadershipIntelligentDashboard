package com.company.leadershipintelligence.repository.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Rows of the roster, assessment runs and the respondent flow (decision D48). */
public final class RosterRows {

    private RosterRows() {
    }

    public record TeamRow(String id, String organizationId, String name, Instant createdAt, int activePeople) {
    }

    public record PersonRow(UUID id, String teamId, String organizationId, String name, String email, boolean active, Instant createdAt,
                            Instant updatedAt) {
    }

    public record RunRow(UUID id, String organizationId, String name, String questionnaireId, int questionnaireVersion,
                         UUID configurationVersionId, String status, LocalDate dueDate, String createdBy, Instant createdAt,
                         String closedBy, Instant closedAt, String assessmentId, UUID scoringRunId) {
    }

    /** A participant with the person behind it; only administrators ever read the person. */
    public record ParticipantRow(UUID id, UUID runId, UUID personId, String teamId, String status, String personName, String email) {
    }

    /** What a respondent needs to know about their own participation. */
    public record MyParticipationRow(UUID participantId, UUID runId, String teamId, String status) {
    }

    /** One stored answer of a submitted respondent: the team and the question, never the person. */
    public record ResponseRow(String teamId, String questionId, String answerJson) {
    }
}
