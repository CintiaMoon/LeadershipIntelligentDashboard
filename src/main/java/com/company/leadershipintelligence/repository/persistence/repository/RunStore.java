package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.MyParticipationRow;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.ParticipantRow;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.ResponseRow;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.RunRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped storage of assessment runs, their participants, drafts and submitted answers (decision D48).
 *
 * <p>Anonymity is a property of this class: a submitted answer is written by {@link #insertResponse} with the team and the question
 * only (a random id, no participant, no timestamp), and nothing here can read a response together with a person. A draft is the
 * only place answers sit next to a participant, and it is deleted on submit and on close.
 */
@Repository
public class RunStore {

    private static final String RUN_COLUMNS = "id, organization_id, name, questionnaire_id, questionnaire_version, configuration_version_id, status, "
            + "due_date, created_by, created_at, closed_by, closed_at, assessment_id, scoring_run_id";

    private static final RowMapper<RunRow> RUN = (rs, n) -> new RunRow(rs.getObject("id", UUID.class), rs.getString("organization_id"),
            rs.getString("name"), rs.getString("questionnaire_id"), rs.getInt("questionnaire_version"),
            rs.getObject("configuration_version_id", UUID.class), rs.getString("status"),
            rs.getDate("due_date") == null ? null : rs.getDate("due_date").toLocalDate(), rs.getString("created_by"),
            rs.getTimestamp("created_at").toInstant(), rs.getString("closed_by"),
            rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant(), rs.getString("assessment_id"),
            rs.getObject("scoring_run_id", UUID.class));

    private static final RowMapper<ParticipantRow> PARTICIPANT = (rs, n) -> new ParticipantRow(rs.getObject("id", UUID.class),
            rs.getObject("run_id", UUID.class), rs.getObject("person_id", UUID.class), rs.getString("team_id"), rs.getString("status"),
            rs.getString("name"), rs.getString("email"));

    private final JdbcTemplate jdbc;

    public RunStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------- runs ----------------

    @Transactional
    public void insertRun(String tenantId, UUID id, String organizationId, String name, String questionnaireId, int questionnaireVersion,
                          UUID configurationVersionId, LocalDate dueDate, String actor, Instant now) {
        jdbc.update("insert into assessment_run (id, tenant_id, organization_id, name, questionnaire_id, questionnaire_version, "
                        + "configuration_version_id, status, due_date, created_by, created_at) values (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?)",
                id, tenantId, organizationId, name, questionnaireId, questionnaireVersion, configurationVersionId,
                dueDate == null ? null : Date.valueOf(dueDate), actor, Timestamp.from(now));
    }

    @Transactional
    public void insertRunTeam(UUID runId, String teamId) {
        jdbc.update("insert into assessment_run_team (run_id, team_id) values (?, ?)", runId, teamId);
    }

    @Transactional
    public void insertParticipant(String tenantId, UUID id, UUID runId, UUID personId, String teamId) {
        jdbc.update("insert into run_participant (id, run_id, tenant_id, person_id, team_id, status) values (?, ?, ?, ?, ?, 'NOT_STARTED')",
                id, runId, tenantId, personId, teamId);
    }

    public Optional<RunRow> run(String tenantId, UUID id) {
        return jdbc.query("select " + RUN_COLUMNS + " from assessment_run where tenant_id = ? and id = ?", RUN, tenantId, id).stream().findFirst();
    }

    /** Serializes closing and submitting for one run. */
    @Transactional
    public Optional<RunRow> lockRun(String tenantId, UUID id) {
        return jdbc.query("select " + RUN_COLUMNS + " from assessment_run where tenant_id = ? and id = ? for update", RUN, tenantId, id).stream().findFirst();
    }

    /** Shared lock: a submit proceeds alongside other submits but not alongside a close. */
    @Transactional
    public Optional<RunRow> shareRun(String tenantId, UUID id) {
        return jdbc.query("select " + RUN_COLUMNS + " from assessment_run where tenant_id = ? and id = ? for share", RUN, tenantId, id).stream().findFirst();
    }

    /** Runs of the given organizations, newest first, optionally of one status. */
    public List<RunRow> runs(String tenantId, Collection<String> organizationIds, String status) {
        if (organizationIds.isEmpty()) {
            return List.of();
        }
        String marks = String.join(",", organizationIds.stream().map(o -> "?").toList());
        java.util.ArrayList<Object> arguments = new java.util.ArrayList<>();
        arguments.add(tenantId);
        arguments.addAll(organizationIds);
        String sql = "select " + RUN_COLUMNS + " from assessment_run where tenant_id = ? and organization_id in (" + marks + ")";
        if (status != null) {
            sql += " and status = ?";
            arguments.add(status);
        }
        return jdbc.query(sql + " order by created_at desc, id", RUN, arguments.toArray());
    }

    public List<String> teamIdsOf(UUID runId) {
        return jdbc.queryForList("select team_id from assessment_run_team where run_id = ? order by team_id", String.class, runId);
    }

    @Transactional
    public void close(String tenantId, UUID id, String actor, Instant now, String assessmentId, UUID scoringRunId) {
        jdbc.update("update assessment_run set status = 'CLOSED', closed_by = ?, closed_at = ?, assessment_id = ?, scoring_run_id = ? "
                + "where tenant_id = ? and id = ? and status = 'OPEN'", actor, Timestamp.from(now), assessmentId, scoringRunId, tenantId, id);
    }

    // ---------------- participants ----------------

    /** Administrators only: the people of a run with whether each has finished. Never any answer. */
    public List<ParticipantRow> participants(String tenantId, UUID runId) {
        return jdbc.query("select rp.id, rp.run_id, rp.person_id, rp.team_id, rp.status, p.name, p.email from run_participant rp "
                + "join person p on p.id = rp.person_id where rp.tenant_id = ? and rp.run_id = ? order by rp.team_id, lower(p.name), rp.id",
                PARTICIPANT, tenantId, runId);
    }

    /** Status counts per team: team, status, count. */
    public Map<String, Map<String, Integer>> counts(String tenantId, UUID runId) {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        jdbc.query("select team_id, status, count(*) as n from run_participant where tenant_id = ? and run_id = ? group by team_id, status "
                        + "order by team_id, status", rs -> {
            result.computeIfAbsent(rs.getString("team_id"), k -> new LinkedHashMap<>()).put(rs.getString("status"), rs.getInt("n"));
        }, tenantId, runId);
        return result;
    }

    /** The participations of the person in runs of the tenant, newest run first. */
    public List<MyParticipationRow> participationsOf(String tenantId, UUID personId) {
        return jdbc.query("select rp.id, rp.run_id, rp.team_id, rp.status from run_participant rp join assessment_run r on r.id = rp.run_id "
                        + "where rp.tenant_id = ? and rp.person_id = ? order by r.created_at desc, r.id",
                (rs, n) -> new MyParticipationRow(rs.getObject("id", UUID.class), rs.getObject("run_id", UUID.class), rs.getString("team_id"),
                        rs.getString("status")), tenantId, personId);
    }

    public Optional<MyParticipationRow> participation(String tenantId, UUID personId, UUID runId) {
        return jdbc.query("select id, run_id, team_id, status from run_participant where tenant_id = ? and person_id = ? and run_id = ?",
                (rs, n) -> new MyParticipationRow(rs.getObject("id", UUID.class), rs.getObject("run_id", UUID.class), rs.getString("team_id"),
                        rs.getString("status")), tenantId, personId, runId).stream().findFirst();
    }

    /** Locks the participant row, so two submits of one person cannot both go through. */
    @Transactional
    public Optional<MyParticipationRow> lockParticipation(String tenantId, UUID personId, UUID runId) {
        return jdbc.query("select id, run_id, team_id, status from run_participant where tenant_id = ? and person_id = ? and run_id = ? for update",
                (rs, n) -> new MyParticipationRow(rs.getObject("id", UUID.class), rs.getObject("run_id", UUID.class), rs.getString("team_id"),
                        rs.getString("status")), tenantId, personId, runId).stream().findFirst();
    }

    @Transactional
    public void setStatus(UUID participantId, String status) {
        jdbc.update("update run_participant set status = ? where id = ?", status, participantId);
    }

    // ---------------- drafts ----------------

    /** The respondent's own unfinished answers, as JSON text by question. */
    public Map<String, String> draft(UUID participantId) {
        Map<String, String> answers = new LinkedHashMap<>();
        jdbc.query("select question_id, answer::text as answer from run_draft where participant_id = ? order by question_id",
                rs -> {
                    answers.put(rs.getString("question_id"), rs.getString("answer"));
                }, participantId);
        return answers;
    }

    @Transactional
    public void saveDraftAnswer(UUID participantId, String questionId, String answerJson, Instant now) {
        jdbc.update("insert into run_draft (participant_id, question_id, answer, updated_at) values (?, ?, ?::jsonb, ?) "
                        + "on conflict (participant_id, question_id) do update set answer = excluded.answer, updated_at = excluded.updated_at",
                participantId, questionId, answerJson, Timestamp.from(now));
    }

    @Transactional
    public void deleteDraftAnswer(UUID participantId, String questionId) {
        jdbc.update("delete from run_draft where participant_id = ? and question_id = ?", participantId, questionId);
    }

    @Transactional
    public void deleteDraft(UUID participantId) {
        jdbc.update("delete from run_draft where participant_id = ?", participantId);
    }

    @Transactional
    public void deleteDraftsOfRun(UUID runId) {
        jdbc.update("delete from run_draft where participant_id in (select id from run_participant where run_id = ?)", runId);
    }

    // ---------------- responses ----------------

    /** Writes one submitted answer with the team and the question only: no person, no time, a random id. */
    @Transactional
    public void insertResponse(String tenantId, UUID runId, String teamId, String questionId, String answerJson) {
        jdbc.update("insert into run_response (id, run_id, tenant_id, team_id, question_id, answer) values (?, ?, ?, ?, ?, ?::jsonb)",
                UUID.randomUUID(), runId, tenantId, teamId, questionId, answerJson);
    }

    /** The answers of a run in a fixed order that says nothing about who gave them (team, question, then the answer itself). */
    public List<ResponseRow> responses(String tenantId, UUID runId) {
        return jdbc.query("select team_id, question_id, answer::text as answer from run_response where tenant_id = ? and run_id = ? "
                        + "order by team_id, question_id, answer::text",
                (rs, n) -> new ResponseRow(rs.getString("team_id"), rs.getString("question_id"), rs.getString("answer")), tenantId, runId);
    }

    @Transactional
    public void deleteResponses(UUID runId) {
        jdbc.update("delete from run_response where run_id = ?", runId);
    }
}
