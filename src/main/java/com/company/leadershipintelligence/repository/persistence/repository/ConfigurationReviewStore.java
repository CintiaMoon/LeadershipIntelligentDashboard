package com.company.leadershipintelligence.repository.persistence.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage of the review record of a configuration version and of the fixtures of a configuration slot (decisions D56, D57).
 * Everything here is numbers, ids, rules and the words people wrote; no answer is stored. Tenant scoping is done by the service
 * (a version or slot is looked up for the caller first); the review row and the fixtures carry the tenant so a query by tenant is
 * possible and cannot cross.
 */
@Repository
public class ConfigurationReviewStore {

    public record ReviewRow(UUID versionId, String tenantId, String submittedBy, Instant submittedAt, String validationJson, String previewJson,
                            String fixturesJson, String approvedBy, Instant approvedAt, String approvalNote, String calibrationReference,
                            String knownLimitations, String rejectedBy, Instant rejectedAt, String rejectionComment) {
    }

    public record FixtureRow(UUID id, UUID configurationId, String tenantId, String name, String answersJson, String expectationsJson,
                             String createdBy, Instant updatedAt) {
    }

    private static final RowMapper<ReviewRow> REVIEW = (rs, n) -> new ReviewRow(rs.getObject("version_id", UUID.class), rs.getString("tenant_id"),
            rs.getString("submitted_by"), instant(rs.getTimestamp("submitted_at")), rs.getString("validation_result"), rs.getString("preview_summary"),
            rs.getString("fixtures_result"), rs.getString("approved_by"), instant(rs.getTimestamp("approved_at")), rs.getString("approval_note"),
            rs.getString("calibration_reference"), rs.getString("known_limitations"), rs.getString("rejected_by"), instant(rs.getTimestamp("rejected_at")),
            rs.getString("rejection_comment"));

    private static final String REVIEW_COLUMNS = "version_id, tenant_id, submitted_by, submitted_at, validation_result::text as validation_result, "
            + "preview_summary::text as preview_summary, fixtures_result::text as fixtures_result, approved_by, approved_at, approval_note, "
            + "calibration_reference, known_limitations, rejected_by, rejected_at, rejection_comment";

    private static final RowMapper<FixtureRow> FIXTURE = (rs, n) -> new FixtureRow(rs.getObject("id", UUID.class), rs.getObject("configuration_id", UUID.class),
            rs.getString("tenant_id"), rs.getString("name"), rs.getString("answers"), rs.getString("expectations"), rs.getString("created_by"),
            instant(rs.getTimestamp("updated_at")));

    private static final String FIXTURE_COLUMNS = "id, configuration_id, tenant_id, name, answers::text as answers, expectations::text as expectations, "
            + "created_by, updated_at";

    private final JdbcTemplate jdbc;

    public ConfigurationReviewStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------- review ----------------

    public Optional<ReviewRow> review(UUID versionId) {
        return jdbc.query("select " + REVIEW_COLUMNS + " from configuration_review where version_id = ?", REVIEW, versionId).stream().findFirst();
    }

    /** Records a submission; a version that was rejected and corrected is submitted again, which clears the earlier rejection. */
    @Transactional
    public void submit(UUID versionId, String tenantId, String actor, Instant now, String validationJson, String previewJson, String fixturesJson) {
        jdbc.update("insert into configuration_review (version_id, tenant_id, submitted_by, submitted_at, validation_result, preview_summary, fixtures_result) "
                        + "values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb) "
                        + "on conflict (version_id) do update set submitted_by = excluded.submitted_by, submitted_at = excluded.submitted_at, "
                        + "validation_result = excluded.validation_result, preview_summary = excluded.preview_summary, fixtures_result = excluded.fixtures_result, "
                        + "rejected_by = null, rejected_at = null, rejection_comment = null",
                versionId, tenantId, actor, Timestamp.from(now), validationJson, previewJson, fixturesJson);
    }

    @Transactional
    public void approve(UUID versionId, String approver, Instant now, String note, String calibrationReference, String knownLimitations,
                        String previewJson, String fixturesJson) {
        jdbc.update("update configuration_review set approved_by = ?, approved_at = ?, approval_note = ?, calibration_reference = ?, known_limitations = ?, "
                        + "preview_summary = ?::jsonb, fixtures_result = ?::jsonb where version_id = ?",
                approver, Timestamp.from(now), note, calibrationReference, knownLimitations, previewJson, fixturesJson, versionId);
    }

    @Transactional
    public void reject(UUID versionId, String actor, Instant now, String comment) {
        jdbc.update("update configuration_review set rejected_by = ?, rejected_at = ?, rejection_comment = ? where version_id = ?",
                actor, Timestamp.from(now), comment, versionId);
    }

    /**
     * Everyone who worked on a version: who created it, edited it or submitted it. The approver must not be among them (decision D53).
     */
    public List<String> workedOnBy(UUID versionId) {
        return jdbc.queryForList("select distinct actor from scoring_configuration_audit where version_id = ? and action in ('CREATED', 'UPDATED', 'VALIDATED') "
                + "order by actor", String.class, versionId);
    }

    // ---------------- fixtures ----------------

    public List<FixtureRow> fixtures(UUID configurationId) {
        return jdbc.query("select " + FIXTURE_COLUMNS + " from configuration_fixture where configuration_id = ? order by lower(name), id", FIXTURE, configurationId);
    }

    public Optional<FixtureRow> fixture(UUID configurationId, UUID id) {
        return jdbc.query("select " + FIXTURE_COLUMNS + " from configuration_fixture where configuration_id = ? and id = ?", FIXTURE, configurationId, id)
                .stream().findFirst();
    }

    @Transactional
    public void insertFixture(UUID id, UUID configurationId, String tenantId, String name, String answersJson, String expectationsJson, String actor, Instant now) {
        jdbc.update("insert into configuration_fixture (id, configuration_id, tenant_id, name, answers, expectations, created_by, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)", id, configurationId, tenantId, name, answersJson, expectationsJson, actor,
                Timestamp.from(now), Timestamp.from(now));
    }

    @Transactional
    public void updateFixture(UUID configurationId, UUID id, String name, String answersJson, String expectationsJson, Instant now) {
        jdbc.update("update configuration_fixture set name = ?, answers = ?::jsonb, expectations = ?::jsonb, updated_at = ? where configuration_id = ? and id = ?",
                name, answersJson, expectationsJson, Timestamp.from(now), configurationId, id);
    }

    @Transactional
    public void deleteFixture(UUID configurationId, UUID id) {
        jdbc.update("delete from configuration_fixture where configuration_id = ? and id = ?", configurationId, id);
    }

    public int fixtureCount(UUID configurationId) {
        Integer count = jdbc.queryForObject("select count(*) from configuration_fixture where configuration_id = ?", Integer.class, configurationId);
        return count == null ? 0 : count;
    }

    // ---------------- assessments for a preview ----------------

    /**
     * The most recent completed assessments of the given organizations of one tenant, newest first. Their answers are read by the
     * preview, in memory, and never stored or returned.
     */
    public List<String> recentAssessmentIds(String tenantId, Collection<String> organizationIds, int limit) {
        if (organizationIds.isEmpty()) {
            return List.of();
        }
        String marks = String.join(",", organizationIds.stream().map(o -> "?").toList());
        java.util.ArrayList<Object> arguments = new java.util.ArrayList<>();
        arguments.add(tenantId);
        arguments.addAll(organizationIds);
        arguments.add(limit);
        return jdbc.queryForList("select id from assessment where tenant_id = ? and organization_id in (" + marks + ") and status = 'COMPLETED' "
                + "order by completed_at desc, id desc limit ?", String.class, arguments.toArray());
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
