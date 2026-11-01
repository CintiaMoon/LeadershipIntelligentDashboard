package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.domain.enums.RunStatus;
import com.company.leadershipintelligence.domain.model.AdjustedDimension;
import com.company.leadershipintelligence.domain.model.DimensionContribution;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.ImbalanceResult;
import com.company.leadershipintelligence.domain.model.InterpretationResult;
import com.company.leadershipintelligence.domain.model.OverallScore;
import com.company.leadershipintelligence.domain.model.PatternEvaluation;
import com.company.leadershipintelligence.domain.model.PatternEvidence;
import com.company.leadershipintelligence.domain.model.PenaltyResult;
import com.company.leadershipintelligence.domain.model.ScoringFacts;
import com.company.leadershipintelligence.repository.persistence.entity.IdempotencyBinding;
import com.company.leadershipintelligence.repository.persistence.entity.NewFailedRun;
import com.company.leadershipintelligence.repository.persistence.entity.NewRun;
import com.company.leadershipintelligence.repository.persistence.entity.StoredRunRow;
import com.company.leadershipintelligence.repository.persistence.mapper.TraceJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped storage of immutable scoring runs and their findings (specv3.md Sections 26.3-26.7, 27). A run is
 * written with all its children in one transaction, so a failure leaves nothing behind. Rows are never updated;
 * the database rejects it. Every statement carries tenant_id.
 */
@Repository
public class ScoringRunStore {

    private static final String RUN_COLUMNS = "id, tenant_id, organization_id, assessment_id, snapshot_id, source, configuration_selection, "
            + "engine_version, algorithm_version, configuration_version, configuration_hash, input_hash, rounding_policy_version, "
            + "confidence_algorithm_version, status, score_status, final_score, weighted_adjusted_score, "
            + "overall_penalty, interpretation, layers::text, trace::text, error_code, error_message, started_at, completed_at, "
            + "scope_type, scope_id, parent_run_id, response_summaries::text";

    private static final RowMapper<StoredRunRow> RUN_MAPPER = (rs, n) -> new StoredRunRow(
            rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
            rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13),
            rs.getString(14), rs.getString(15), rs.getString(16), rs.getBigDecimal(17), rs.getBigDecimal(18), rs.getBigDecimal(19),
            rs.getString(20), rs.getString(21), rs.getString(22), rs.getString(23), rs.getString(24),
            rs.getTimestamp(25).toInstant(), rs.getTimestamp(26).toInstant(), rs.getString(27), rs.getString(28),
            rs.getObject(29, UUID.class), rs.getString(30));

    private final JdbcTemplate jdbc;

    public ScoringRunStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void insert(NewRun run) {
        ScoringFacts facts = run.result().facts();
        OverallScore overall = facts.overall();
        InterpretationResult lis = facts.lisInterpretation();
        jdbc.update("insert into scoring_run (id, tenant_id, organization_id, assessment_id, snapshot_id, source, configuration_selection, "
                        + "engine_version, algorithm_version, configuration_version, configuration_hash, input_hash, rounding_policy_version, "
                        + "confidence_algorithm_version, status, score_status, final_score, "
                        + "weighted_adjusted_score, overall_penalty, interpretation, layers, trace, started_at, completed_at, "
                        + "scope_type, scope_id, parent_run_id, response_summaries) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?::jsonb)",
                run.runId(), run.tenantId(), run.organizationId(), run.result().context().assessmentId().value(),
                run.result().context().assessmentSnapshotId().value(), run.source(), run.configurationSelection(),
                facts.versions().engineVersion(), facts.versions().algorithmVersion(), facts.versions().configurationVersion().value(),
                run.configurationHash(), run.inputHash(), facts.versions().roundingPolicyVersion(),
                facts.versions().confidenceAlgorithmVersion(), RunStatus.COMPLETED.name(),
                overall.status().name(), overall.finalScore() == null ? null : overall.finalScore().value(),
                overall.weightedAdjustedScore() == null ? null : overall.weightedAdjustedScore().value(),
                overall.overallPenaltyApplied().value(), lis == null ? null : lis.label(), run.layersJson(), run.traceJson(),
                Timestamp.from(run.startedAt()), Timestamp.from(run.completedAt()),
                run.scopeType(), run.scopeId(), run.parentRunId(), run.responseSummariesJson());
        insertChildren(run, facts);
        if (run.idempotencyKey() != null) {
            bindIdempotencyKey(run.tenantId(), run.idempotencyKey(), run.requestHash(), run.runId());
        }
    }

    /** Binds a key to its first request and run. A second binding of the same key is a duplicate-key error. */
    @Transactional
    public void bindIdempotencyKey(String tenantId, String key, String requestHash, UUID runId) {
        jdbc.update("insert into scoring_idempotency_key (tenant_id, idempotency_key, request_hash, run_id) values (?, ?, ?, ?)",
                tenantId, key, requestHash, runId);
    }

    public Optional<IdempotencyBinding> findIdempotencyBinding(String tenantId, String key) {
        return jdbc.query("select request_hash, run_id from scoring_idempotency_key where tenant_id = ? and idempotency_key = ?",
                (rs, n) -> new Object[]{rs.getString(1), rs.getObject(2, UUID.class)}, tenantId, key).stream().findFirst()
                .flatMap(row -> findById(tenantId, (UUID) row[1]).map(run -> new IdempotencyBinding((String) row[0], run)));
    }

    @Transactional
    public void insertFailed(NewFailedRun f) {
        jdbc.update("insert into scoring_run (id, tenant_id, organization_id, assessment_id, snapshot_id, source, configuration_selection, "
                        + "engine_version, algorithm_version, configuration_version, configuration_hash, input_hash, status, layers, "
                        + "error_code, error_message, started_at, completed_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'FAILED', ?::jsonb, ?, ?, ?, ?)",
                f.runId(), f.tenantId(), f.organizationId(), f.assessmentId(), f.snapshotId(), f.source(), f.configurationSelection(),
                f.engineVersion(), f.algorithmVersion(), f.configurationVersion(), f.configurationHash(), f.inputHash(), f.layersJson(),
                f.errorCode(), f.errorMessage() == null ? null : f.errorMessage().substring(0, Math.min(500, f.errorMessage().length())),
                Timestamp.from(f.startedAt()), Timestamp.from(f.completedAt()));
    }

    public Optional<StoredRunRow> findById(String tenantId, UUID runId) {
        return jdbc.query("select " + RUN_COLUMNS + " from scoring_run where tenant_id = ? and id = ?", RUN_MAPPER, tenantId, runId)
                .stream().findFirst();
    }

    /** The latest completed organization run; failed runs and team runs are never returned as the assessment's result. */
    public Optional<StoredRunRow> findLatestCompleted(String tenantId, String assessmentId) {
        return jdbc.query("select " + RUN_COLUMNS + " from scoring_run where tenant_id = ? and assessment_id = ? and status = 'COMPLETED' "
                        + "and scope_type = 'ORGANIZATION' order by seq desc limit 1", RUN_MAPPER, tenantId, assessmentId).stream().findFirst();
    }

    /** specv3.md Section 27.4 identity (recalculations excluded) of an organization run. */
    public Optional<StoredRunRow> findByIdentity(String tenantId, String snapshotId, String inputHash, String configurationHash,
                                                 String engineVersion, String algorithmVersion) {
        return jdbc.query("select " + RUN_COLUMNS + " from scoring_run where tenant_id = ? and snapshot_id = ? and input_hash = ? "
                        + "and configuration_hash = ? and engine_version = ? and algorithm_version = ? and status = 'COMPLETED' "
                        + "and source <> 'RECALCULATE' and scope_type = 'ORGANIZATION'", RUN_MAPPER, tenantId, snapshotId, inputHash,
                configurationHash, engineVersion, algorithmVersion).stream().findFirst();
    }

    /** The team runs produced together with an organization run, in team order (decision D45). */
    public List<StoredRunRow> findTeamRuns(String tenantId, UUID parentRunId) {
        return jdbc.query("select " + RUN_COLUMNS + " from scoring_run where tenant_id = ? and parent_run_id = ? and status = 'COMPLETED' "
                + "order by scope_id", RUN_MAPPER, tenantId, parentRunId);
    }

    // ---------------- children ----------------

    private void insertChildren(NewRun run, ScoringFacts facts) {
        UUID id = run.runId();
        String tenant = run.tenantId();

        List<Object[]> dimensions = new ArrayList<>();
        List<Object[]> contributions = new ArrayList<>();
        for (DimensionScore score : facts.dimensionScores()) {
            AdjustedDimension adjusted = facts.overall().dimensions().stream()
                    .filter(a -> a.dimension() == score.dimension()).findFirst().orElseThrow();
            InterpretationResult label = facts.dimensionInterpretations().get(score.dimension());
            var coverage = score.coverage();
            dimensions.add(new Object[]{id, tenant, score.dimension().name(), score.status().name(),
                    score.rawScore() == null ? null : score.rawScore().value(), adjusted.dimensionPenalty().value(),
                    adjusted.adjustedScore() == null ? null : adjusted.adjustedScore().value(), adjusted.weight().value(),
                    adjusted.weightedContribution(), label == null ? null : label.label(), coverage.coverage(),
                    coverage.requiredCoverage(), coverage.status().name(), coverage.applicableQuestionCount(),
                    coverage.answeredQuestionCount(), coverage.validContributionCount(), coverage.expectedWeight(),
                    coverage.observedWeight()});
            for (DimensionContribution c : score.contributions()) {
                contributions.add(new Object[]{id, tenant, c.dimension().name(), c.questionId(), c.status().name(),
                        c.value() == null ? null : c.value().value(), c.weight().value(), c.normalizedWeight(), c.contribution()});
            }
        }
        jdbc.batchUpdate("insert into scoring_dimension_result (run_id, tenant_id, dimension, status, raw_score, dimension_penalty, "
                + "adjusted_score, weight, weighted_contribution, label, coverage, required_coverage, coverage_status, "
                + "applicable_questions, answered_questions, valid_contributions, expected_weight, observed_weight) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", dimensions);
        jdbc.batchUpdate("insert into scoring_contribution (run_id, tenant_id, dimension, question_id, status, value, weight, "
                + "normalized_weight, contribution) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", contributions);

        List<Object[]> patterns = new ArrayList<>();
        List<Object[]> evidence = new ArrayList<>();
        for (PatternEvaluation p : facts.patterns()) {
            patterns.add(new Object[]{id, tenant, p.patternId(), p.version(), p.code(), p.category().name(), p.severity().name(),
                    p.status().name(), p.reason().name(), p.confidence() == null ? null : p.confidence().value(),
                    p.confidenceBand() == null ? null : p.confidenceBand().name(), p.penaltyEligible(),
                    TraceJson.writeValue(p.missingOperands()), TraceJson.writeValue(p.actionKeys()),
                    p.conditionResult() == null ? null : TraceJson.writeValue(p.conditionResult()),
                    p.confidenceBreakdown() == null ? null : TraceJson.writeValue(p.confidenceBreakdown())});
            int ordinal = 0;
            for (PatternEvidence e : p.evidence()) {
                evidence.add(new Object[]{id, tenant, p.patternId(), p.version(), ordinal++, e.operandKey(), e.kind().name(),
                        e.numericValue(), e.flag(), TraceJson.writeValue(e.sourceQuestionIds()), e.correlationGroup(), e.reliability()});
            }
        }
        jdbc.batchUpdate("insert into scoring_pattern_evaluation (run_id, tenant_id, pattern_id, version, code, category, severity, status, "
                + "reason, confidence, confidence_band, penalty_eligible, missing_operands, action_keys, condition_result, "
                + "confidence_breakdown) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb)", patterns);
        jdbc.batchUpdate("insert into scoring_pattern_evidence (run_id, tenant_id, pattern_id, version, ordinal, operand_key, kind, "
                + "numeric_value, flag, source_questions, correlation_group, reliability) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)", evidence);

        List<Object[]> imbalances = new ArrayList<>();
        for (ImbalanceResult r : facts.imbalances()) {
            imbalances.add(new Object[]{id, tenant, r.ruleId(), r.version(), r.status().name(), r.reason().name(), r.left().name(),
                    r.right().name(), r.leftValue() == null ? null : r.leftValue().value(),
                    r.rightValue() == null ? null : r.rightValue().value(), r.gap(), r.direction().name(), r.gapOperator().name(),
                    r.threshold(), r.severity().name(), r.basis().name(), r.explanationKey(), r.recommendationKey(),
                    TraceJson.writeValue(r.evidenceRefs())});
        }
        jdbc.batchUpdate("insert into scoring_imbalance_result (run_id, tenant_id, rule_id, version, status, reason, left_dimension, "
                + "right_dimension, left_value, right_value, gap, direction, gap_operator, threshold, severity, basis, explanation_key, "
                + "recommendation_key, evidence_refs) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)", imbalances);

        List<Object[]> penalties = new ArrayList<>();
        for (PenaltyResult r : facts.penalties().results()) {
            var d = r.definition();
            penalties.add(new Object[]{id, tenant, d.penaltyId(), d.version(), d.code(), d.target().name(), d.strategy().type().name(),
                    d.category(), d.exclusiveGroup(), d.priority(), d.ruleOrder(), r.policy().name(), d.triggerPatternId(),
                    d.triggerPatternVersion(), r.status().name(), r.reason().name(), r.candidateDeduction().value(),
                    r.effectiveDeduction().value(), r.suppressedAmount().value(), r.winningPenaltyId(), r.scoreBase(),
                    r.scoreBaseValue(), TraceJson.writeValue(r.evidenceRefs()), d.explanationKey()});
        }
        jdbc.batchUpdate("insert into scoring_penalty_result (run_id, tenant_id, penalty_id, version, code, target, strategy, category, "
                + "exclusive_group, priority, rule_order, stacking_policy, trigger_pattern_id, trigger_pattern_version, status, reason, "
                + "candidate_deduction, effective_deduction, suppressed_amount, winning_penalty_id, score_base, score_base_value, "
                + "evidence_refs, explanation_key) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                penalties);
    }

    /** Keeps BigDecimal parameters out of unused-import warnings for readers. */
    @SuppressWarnings("unused")
    private static BigDecimal none() {
        return null;
    }
}
