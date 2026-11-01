package com.company.leadershipintelligence.service.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Teams, people, assessment runs and the respondent view of a run (decision D48). */
public final class RunViews {

    private RunViews() {
    }

    public record TeamView(String id, String organizationId, String name, int activePeople, Instant createdAt) {
    }

    public record PersonView(UUID id, String teamId, String organizationId, String name, String email, boolean active) {
    }

    public record AssessmentRunSummary(UUID id, String name, String organizationId, String questionnaireId, String questionnaireName,
                             int questionnaireVersion, String status, LocalDate dueDate, Instant createdAt, Instant closedAt,
                             int participants, int submitted, String assessmentId, UUID scoringRunId) {
    }

    public record TeamProgress(String teamId, String teamName, int total, int notStarted, int inProgress, int submitted) {
    }

    /** Administrators only: the person behind a participant and whether they have finished. Never an answer. */
    public record ParticipantView(String teamId, String name, String email, String status) {
    }

    /**
     * @param notice what the completion list can reveal in a small team (specv3.md Section 4.14); the interface shows it next to the list
     */
    public record AssessmentRunDetail(AssessmentRunSummary run, List<TeamProgress> teams, List<ParticipantView> participants, String notice) {
    }

    // ---------------- respondent ----------------

    public record RespondentRun(UUID runId, String name, String questionnaireName, LocalDate dueDate, String runStatus, String myStatus) {
    }

    public record OptionView(String key, String label) {
    }

    /**
     * A question as a respondent sees it. It carries nothing about scoring: no dimension, weight, direction, desirable answer or
     * option score. {@code min} and {@code max} are the bounds of a rating, scale or number.
     */
    public record RespondentQuestion(String id, String kind, String title, String text, String help, boolean required, List<OptionView> options,
                                     List<String> labels, BigDecimal min, BigDecimal max) {
    }

    public record RespondentRunDetail(UUID runId, String name, String questionnaireName, LocalDate dueDate, String runStatus, String myStatus,
                                      List<RespondentQuestion> questions, java.util.Map<String, Object> answers) {
    }
}
