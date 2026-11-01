package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.repository.persistence.entity.AnswerRow;
import com.company.leadershipintelligence.repository.persistence.entity.AssessmentRow;
import com.company.leadershipintelligence.repository.persistence.entity.SnapshotRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped storage of assessments, their immutable snapshots and answers (specv3.md Sections 4.1, 26.1).
 * Every statement filters or writes tenant_id; there is no cross-tenant read. Organizations and template versions
 * are registered on first use because they arrive with the request (decision D13).
 */
@Repository
public class AssessmentStore {

    private final JdbcTemplate jdbc;

    public AssessmentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean tenantExists(String tenantId) {
        Integer count = jdbc.queryForObject("select count(*) from tenant where id = ?", Integer.class, tenantId);
        return count != null && count > 0;
    }

    @Transactional
    public void register(String tenantId, String organizationId, String templateId, int templateVersion) {
        jdbc.update("insert into organization (id, tenant_id, name) values (?, ?, ?) on conflict (id) do nothing",
                organizationId, tenantId, organizationId);
        jdbc.update("insert into assessment_template (id, tenant_id, name) values (?, ?, ?) on conflict (id) do nothing",
                templateId, tenantId, templateId);
        jdbc.update("insert into assessment_template_version (template_id, version, tenant_id) values (?, ?, ?) on conflict do nothing",
                templateId, templateVersion, tenantId);
    }

    @Transactional
    public void insertAssessment(AssessmentRow a) {
        jdbc.update("insert into assessment (id, tenant_id, organization_id, template_id, template_version, status, completed_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)", a.id(), a.tenantId(), a.organizationId(), a.templateId(), a.templateVersion(),
                a.status(), a.completedAt() == null ? null : Timestamp.from(a.completedAt()));
    }

    @Transactional
    public void insertSnapshot(SnapshotRow s, List<AnswerRow> answers) {
        jdbc.update("insert into assessment_snapshot (id, tenant_id, assessment_id, revision, input_hash) values (?, ?, ?, ?, ?)",
                s.id(), s.tenantId(), s.assessmentId(), s.revision(), s.inputHash());
        jdbc.batchUpdate("insert into assessment_answer (snapshot_id, tenant_id, question_id, answer, scope_level, team_id, ordinal, "
                        + "declared_respondents) values (?, ?, ?, ?::jsonb, ?, ?, ?, ?)",
                answers.stream().map(a -> new Object[]{s.id(), s.tenantId(), a.questionId(), a.answerJson(), a.scopeLevel(),
                        a.teamId(), a.ordinal(), a.declaredRespondents()}).toList());
    }

    public Optional<AssessmentRow> findAssessment(String tenantId, String assessmentId) {
        return jdbc.query("select id, tenant_id, organization_id, template_id, template_version, status, completed_at "
                        + "from assessment where tenant_id = ? and id = ?",
                (rs, n) -> new AssessmentRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5),
                        rs.getString(6), instant(rs.getTimestamp(7))), tenantId, assessmentId).stream().findFirst();
    }

    public Optional<SnapshotRow> latestSnapshot(String tenantId, String assessmentId) {
        return jdbc.query("select id, tenant_id, assessment_id, revision, input_hash from assessment_snapshot "
                        + "where tenant_id = ? and assessment_id = ? order by revision desc limit 1",
                (rs, n) -> new SnapshotRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5)),
                tenantId, assessmentId).stream().findFirst();
    }

    public List<AnswerRow> answers(String tenantId, String snapshotId) {
        return jdbc.query("select question_id, answer::text, scope_level, team_id, ordinal, declared_respondents from assessment_answer "
                        + "where tenant_id = ? and snapshot_id = ? order by question_id, scope_level, team_id, ordinal",
                (rs, n) -> new AnswerRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5),
                        (Integer) rs.getObject(6)), tenantId, snapshotId);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
