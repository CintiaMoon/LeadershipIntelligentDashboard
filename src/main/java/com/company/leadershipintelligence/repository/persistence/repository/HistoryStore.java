package com.company.leadershipintelligence.repository.persistence.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only history queries (decisions D26-D27). Every query takes the tenant, and organization filters come from
 * the caller's organizations; the previous run of a series is found with a window function over the VALID runs of
 * one organization and template, so no query is made per row. Only completed runs with a score take part in
 * history: an INSUFFICIENT_DATA run has no score to trend.
 */
@Repository
public class HistoryStore {

    public record RunSummaryRow(UUID id, long seq, String assessmentId, String organizationId, String templateId, int templateVersion,
                                String source, String scoreStatus, BigDecimal score, String interpretation, String configurationVersion,
                                String configurationHash, String algorithmVersion, String engineVersion, Instant calculatedAt,
                                UUID previousRunId, BigDecimal previousScore, String previousConfigurationHash,
                                String previousAlgorithmVersion, Integer previousTemplateVersion) {
    }

    public record AssessmentRow(String id, String organizationId, String templateId, int templateVersion, String status, Instant completedAt,
                                Instant createdAt, UUID latestRunId, BigDecimal latestScore, String latestScoreStatus,
                                String latestInterpretation, Instant latestCalculatedAt) {
    }

    public record DimensionScoreRow(String dimension, BigDecimal adjustedScore) {
    }

    public record DimensionTrendRow(UUID runId, String dimension, BigDecimal rawScore, BigDecimal adjustedScore) {
    }

    public record PatternHitRow(UUID runId, String patternId, String severity, String category) {
    }

    public record PenaltyTotalsRow(UUID runId, String status, int count, BigDecimal effectiveDeduction, BigDecimal suppressedAmount) {
    }

    /** A matched pattern or detected imbalance that refers to an action key. */
    public record ActionSourceRow(String sourceType, String sourceId, String severity, String actionKey) {
    }

    private static final String SUMMARY_SELECT = "select * from ("
            + "select r.id, r.seq, r.assessment_id, r.organization_id, a.template_id, a.template_version, r.source, r.score_status, "
            + "r.final_score, r.interpretation, r.configuration_version, r.configuration_hash, r.algorithm_version, r.engine_version, "
            + "r.completed_at, lag(r.id) over w, lag(r.final_score) over w, lag(r.configuration_hash) over w, "
            + "lag(r.algorithm_version) over w, lag(a.template_version) over w "
            + "from scoring_run r join assessment a on a.id = r.assessment_id and a.tenant_id = r.tenant_id "
            + "where r.tenant_id = ? and r.status = 'COMPLETED' and r.score_status = 'VALID' and r.scope_type = 'ORGANIZATION' "
            + "window w as (partition by r.organization_id, a.template_id order by r.seq)) s where true";

    private static final RowMapper<RunSummaryRow> SUMMARY_MAPPER = (rs, n) -> new RunSummaryRow(
            rs.getObject(1, UUID.class), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6),
            rs.getString(7), rs.getString(8), rs.getBigDecimal(9), rs.getString(10), rs.getString(11), rs.getString(12),
            rs.getString(13), rs.getString(14), rs.getTimestamp(15).toInstant(), rs.getObject(16, UUID.class),
            rs.getBigDecimal(17), rs.getString(18), rs.getString(19), (Integer) rs.getObject(20));

    private final JdbcTemplate jdbc;

    public HistoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Newest first. {@code organizations} null means every organization of the tenant. */
    public List<RunSummaryRow> summaries(String tenantId, Collection<String> organizations, String organizationId, String assessmentId,
                                         Instant from, Instant to, Long beforeSeq, UUID runId, int limit) {
        return summaries(tenantId, organizations, organizationId, assessmentId, null, from, to, beforeSeq, runId, limit);
    }

    public List<RunSummaryRow> summaries(String tenantId, Collection<String> organizations, String organizationId, String assessmentId,
                                         String templateId, Instant from, Instant to, Long beforeSeq, UUID runId, int limit) {
        StringBuilder sql = new StringBuilder(SUMMARY_SELECT);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (organizations != null) {
            if (organizations.isEmpty()) {
                return List.of();
            }
            sql.append(" and s.organization_id = any(?)");
            args.add(organizations.toArray(new String[0]));
        }
        if (organizationId != null) {
            sql.append(" and s.organization_id = ?");
            args.add(organizationId);
        }
        if (assessmentId != null) {
            sql.append(" and s.assessment_id = ?");
            args.add(assessmentId);
        }
        if (templateId != null) {
            sql.append(" and s.template_id = ?");
            args.add(templateId);
        }
        if (from != null) {
            sql.append(" and s.completed_at >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" and s.completed_at < ?");
            args.add(Timestamp.from(to));
        }
        if (beforeSeq != null) {
            sql.append(" and s.seq < ?");
            args.add(beforeSeq);
        }
        if (runId != null) {
            sql.append(" and s.id = ?");
            args.add(runId);
        }
        sql.append(" order by s.seq desc limit ?");
        args.add(limit);
        return jdbc.query(sql.toString(), SUMMARY_MAPPER, args.toArray());
    }

    public List<AssessmentRow> assessments(String tenantId, Collection<String> organizations, String organizationId, String status,
                                           Instant beforeCreatedAt, String beforeId, int limit) {
        StringBuilder sql = new StringBuilder("select a.id, a.organization_id, a.template_id, a.template_version, a.status, a.completed_at, "
                + "a.created_at, lr.id, lr.final_score, lr.score_status, lr.interpretation, lr.completed_at from assessment a "
                + "left join lateral (select r.id, r.final_score, r.score_status, r.interpretation, r.completed_at from scoring_run r "
                + "where r.tenant_id = a.tenant_id and r.assessment_id = a.id and r.status = 'COMPLETED' and r.scope_type = 'ORGANIZATION' "
                + "order by r.seq desc limit 1) lr on true "
                + "where a.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (organizations != null) {
            if (organizations.isEmpty()) {
                return List.of();
            }
            sql.append(" and a.organization_id = any(?)");
            args.add(organizations.toArray(new String[0]));
        }
        if (organizationId != null) {
            sql.append(" and a.organization_id = ?");
            args.add(organizationId);
        }
        if (status != null) {
            sql.append(" and a.status = ?");
            args.add(status);
        }
        if (beforeCreatedAt != null) {
            sql.append(" and (a.created_at, a.id) < (?, ?)");
            args.add(Timestamp.from(beforeCreatedAt));
            args.add(beforeId);
        }
        sql.append(" order by a.created_at desc, a.id desc limit ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, n) -> new AssessmentRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                rs.getString(5), rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant(), rs.getTimestamp(7).toInstant(),
                rs.getObject(8, UUID.class), rs.getBigDecimal(9), rs.getString(10), rs.getString(11),
                rs.getTimestamp(12) == null ? null : rs.getTimestamp(12).toInstant()), args.toArray());
    }

    public Optional<AssessmentRow> assessment(String tenantId, String assessmentId) {
        return jdbc.query("select a.id, a.organization_id, a.template_id, a.template_version, a.status, a.completed_at, a.created_at, "
                + "lr.id, lr.final_score, lr.score_status, lr.interpretation, lr.completed_at from assessment a "
                + "left join lateral (select r.id, r.final_score, r.score_status, r.interpretation, r.completed_at from scoring_run r "
                + "where r.tenant_id = a.tenant_id and r.assessment_id = a.id and r.status = 'COMPLETED' and r.scope_type = 'ORGANIZATION' "
                + "order by r.seq desc limit 1) lr on true "
                + "where a.tenant_id = ? and a.id = ?", (rs, n) -> new AssessmentRow(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getInt(4), rs.getString(5), rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant(),
                rs.getTimestamp(7).toInstant(), rs.getObject(8, UUID.class), rs.getBigDecimal(9), rs.getString(10), rs.getString(11),
                rs.getTimestamp(12) == null ? null : rs.getTimestamp(12).toInstant()), tenantId, assessmentId).stream().findFirst();
    }

    public Map<String, Integer> assessmentCounts(String tenantId, String organizationId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        jdbc.query("select status, count(*) from assessment where tenant_id = ? and organization_id = ? group by status order by status",
                rs -> {
                    counts.put(rs.getString(1), rs.getInt(2));
                }, tenantId, organizationId);
        return counts;
    }

    public List<DimensionScoreRow> dimensionScores(String tenantId, UUID runId) {
        return jdbc.query("select dimension, adjusted_score from scoring_dimension_result where tenant_id = ? and run_id = ? order by dimension",
                (rs, n) -> new DimensionScoreRow(rs.getString(1), rs.getBigDecimal(2)), tenantId, runId);
    }

    public List<ActionSourceRow> actionSources(String tenantId, UUID runId) {
        return jdbc.query("select 'PATTERN', e.pattern_id, e.severity, k from scoring_pattern_evaluation e, "
                + "jsonb_array_elements_text(e.action_keys) k where e.tenant_id = ? and e.run_id = ? and e.status = 'MATCHED' "
                + "union all select 'IMBALANCE', i.rule_id, i.severity, i.recommendation_key from scoring_imbalance_result i "
                + "where i.tenant_id = ? and i.run_id = ? and i.status = 'DETECTED' and i.recommendation_key is not null "
                + "order by 1, 2, 4", (rs, n) -> new ActionSourceRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                tenantId, runId, tenantId, runId);
    }

    public List<DimensionTrendRow> trendDimensions(String tenantId, Collection<UUID> runIds) {
        if (runIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("select run_id, dimension, raw_score, adjusted_score from scoring_dimension_result where tenant_id = ? "
                        + "and run_id = any(?::uuid[]) order by run_id, dimension",
                (rs, n) -> new DimensionTrendRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4)),
                tenantId, ids(runIds));
    }

    public List<PatternHitRow> trendPatterns(String tenantId, Collection<UUID> runIds) {
        if (runIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("select run_id, pattern_id, severity, category from scoring_pattern_evaluation where tenant_id = ? "
                        + "and run_id = any(?::uuid[]) and status = 'MATCHED' order by run_id, pattern_id",
                (rs, n) -> new PatternHitRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)),
                tenantId, ids(runIds));
    }

    public List<PenaltyTotalsRow> trendPenalties(String tenantId, Collection<UUID> runIds) {
        if (runIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("select run_id, status, count(*), coalesce(sum(effective_deduction), 0), coalesce(sum(suppressed_amount), 0) "
                        + "from scoring_penalty_result where tenant_id = ? and run_id = any(?::uuid[]) group by run_id, status order by run_id, status",
                (rs, n) -> new PenaltyTotalsRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), rs.getBigDecimal(4), rs.getBigDecimal(5)),
                tenantId, ids(runIds));
    }

    private static String[] ids(Collection<UUID> runIds) {
        return runIds.stream().map(UUID::toString).toArray(String[]::new);
    }

    public Set<String> matchedPatterns(String tenantId, UUID runId) {
        return new LinkedHashSet<>(jdbc.queryForList("select pattern_id from scoring_pattern_evaluation where tenant_id = ? and run_id = ? "
                + "and status = 'MATCHED' order by pattern_id", String.class, tenantId, runId));
    }
}
