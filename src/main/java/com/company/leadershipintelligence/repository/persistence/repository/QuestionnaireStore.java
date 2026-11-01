package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.repository.persistence.entity.QuestionnaireRows.QuestionnaireListRow;
import com.company.leadershipintelligence.repository.persistence.entity.QuestionnaireRows.QuestionnaireRow;
import com.company.leadershipintelligence.repository.persistence.entity.QuestionnaireRows.VersionRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped storage of questionnaires and their versions (decisions D47, D50). Every statement names the tenant, so a
 * questionnaire of another tenant looks like a missing one. A published version is immutable here and by a database trigger.
 */
@Repository
public class QuestionnaireStore {

    private static final String VERSION_COLUMNS = "questionnaire_id, version, status, definition::text as definition, configuration_version_id, "
            + "created_by, created_at, updated_at, published_by, published_at";

    private static final RowMapper<VersionRow> VERSION = (rs, n) -> new VersionRow(rs.getString("questionnaire_id"), rs.getInt("version"),
            rs.getString("status"), rs.getString("definition"), rs.getObject("configuration_version_id", UUID.class),
            rs.getString("created_by"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")),
            rs.getString("published_by"), instant(rs.getTimestamp("published_at")));

    private final JdbcTemplate jdbc;

    public QuestionnaireStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void insertQuestionnaire(String tenantId, String id, String name, String actor, Instant now) {
        jdbc.update("insert into questionnaire (id, tenant_id, name, created_by, created_at) values (?, ?, ?, ?, ?)",
                id, tenantId, name, actor, Timestamp.from(now));
    }

    /** Serializes the writers of one questionnaire (a draft save, a publish) so a version number is never taken twice. */
    @Transactional
    public Optional<QuestionnaireRow> lock(String tenantId, String id) {
        return jdbc.query("select id, name, created_by, created_at, archived_at from questionnaire where tenant_id = ? and id = ? for update",
                (rs, n) -> new QuestionnaireRow(rs.getString("id"), rs.getString("name"), rs.getString("created_by"),
                        instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("archived_at"))), tenantId, id).stream().findFirst();
    }

    public Optional<QuestionnaireRow> find(String tenantId, String id) {
        return jdbc.query("select id, name, created_by, created_at, archived_at from questionnaire where tenant_id = ? and id = ?",
                (rs, n) -> new QuestionnaireRow(rs.getString("id"), rs.getString("name"), rs.getString("created_by"),
                        instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("archived_at"))), tenantId, id).stream().findFirst();
    }

    /** Newest first. */
    public List<QuestionnaireListRow> list(String tenantId) {
        return jdbc.query("""
                select q.id, q.name, q.created_at, q.archived_at,
                       (select max(version) from questionnaire_version d where d.questionnaire_id = q.id and d.status = 'DRAFT') as draft_version,
                       (select max(version) from questionnaire_version p where p.questionnaire_id = q.id and p.status = 'PUBLISHED') as published_version,
                       (select max(published_at) from questionnaire_version p where p.questionnaire_id = q.id and p.status = 'PUBLISHED') as published_at,
                       (select max(updated_at) from questionnaire_version u where u.questionnaire_id = q.id) as updated_at
                from questionnaire q where q.tenant_id = ? order by q.created_at desc, q.id
                """, (rs, n) -> new QuestionnaireListRow(rs.getString("id"), rs.getString("name"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("archived_at")), (Integer) rs.getObject("draft_version"), (Integer) rs.getObject("published_version"),
                instant(rs.getTimestamp("published_at")), instant(rs.getTimestamp("updated_at"))), tenantId);
    }

    /** All versions, newest first. */
    public List<VersionRow> versions(String tenantId, String id) {
        return jdbc.query("select " + VERSION_COLUMNS + " from questionnaire_version where tenant_id = ? and questionnaire_id = ? order by version desc",
                VERSION, tenantId, id);
    }

    public Optional<VersionRow> version(String tenantId, String id, int version) {
        return jdbc.query("select " + VERSION_COLUMNS + " from questionnaire_version where tenant_id = ? and questionnaire_id = ? and version = ?",
                VERSION, tenantId, id, version).stream().findFirst();
    }

    public Optional<VersionRow> draft(String tenantId, String id) {
        return jdbc.query("select " + VERSION_COLUMNS + " from questionnaire_version where tenant_id = ? and questionnaire_id = ? and status = 'DRAFT'",
                VERSION, tenantId, id).stream().findFirst();
    }

    public Optional<VersionRow> latestPublished(String tenantId, String id) {
        return jdbc.query("select " + VERSION_COLUMNS + " from questionnaire_version where tenant_id = ? and questionnaire_id = ? "
                        + "and status = 'PUBLISHED' order by version desc limit 1", VERSION, tenantId, id).stream().findFirst();
    }

    public int maxVersion(String tenantId, String id) {
        Integer max = jdbc.queryForObject("select coalesce(max(version), 0) from questionnaire_version where tenant_id = ? and questionnaire_id = ?",
                Integer.class, tenantId, id);
        return max == null ? 0 : max;
    }

    @Transactional
    public void insertDraft(String tenantId, String id, int version, String definition, String actor, Instant now) {
        jdbc.update("insert into questionnaire_version (questionnaire_id, version, tenant_id, status, definition, created_by, created_at, updated_at) "
                        + "values (?, ?, ?, 'DRAFT', ?::jsonb, ?, ?, ?)", id, version, tenantId, definition, actor, Timestamp.from(now), Timestamp.from(now));
    }

    @Transactional
    public void updateDraft(String tenantId, String id, int version, String definition, Instant now) {
        jdbc.update("update questionnaire_version set definition = ?::jsonb, updated_at = ? where tenant_id = ? and questionnaire_id = ? "
                + "and version = ? and status = 'DRAFT'", definition, Timestamp.from(now), tenantId, id, version);
    }

    @Transactional
    public void markPublished(String tenantId, String id, int version, UUID configurationVersionId, String definition, String actor, Instant now) {
        jdbc.update("update questionnaire_version set status = 'SUPERSEDED', updated_at = ? where tenant_id = ? and questionnaire_id = ? "
                + "and status = 'PUBLISHED'", Timestamp.from(now), tenantId, id);
        jdbc.update("update questionnaire_version set status = 'PUBLISHED', definition = ?::jsonb, configuration_version_id = ?, "
                        + "published_by = ?, published_at = ?, updated_at = ? where tenant_id = ? and questionnaire_id = ? and version = ? and status = 'DRAFT'",
                definition, configurationVersionId, actor, Timestamp.from(now), Timestamp.from(now), tenantId, id, version);
    }

    @Transactional
    public void rename(String tenantId, String id, String name) {
        jdbc.update("update questionnaire set name = ? where tenant_id = ? and id = ?", name, tenantId, id);
    }

    @Transactional
    public void archive(String tenantId, String id, Instant now) {
        jdbc.update("update questionnaire set archived_at = ? where tenant_id = ? and id = ? and archived_at is null", Timestamp.from(now), tenantId, id);
    }

    /**
     * Makes the questionnaire usable as an assessment template under its own id (the id is the template id) at the published version,
     * so assessments of it satisfy the template foreign keys.
     */
    @Transactional
    public void registerTemplate(String tenantId, String id, String name, int version) {
        jdbc.update("insert into assessment_template (id, tenant_id, name) values (?, ?, ?) on conflict (id) do update set name = excluded.name "
                + "where assessment_template.tenant_id = excluded.tenant_id", id, tenantId, name);
        jdbc.update("insert into assessment_template_version (template_id, version, tenant_id) values (?, ?, ?) on conflict do nothing",
                id, version, tenantId);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
