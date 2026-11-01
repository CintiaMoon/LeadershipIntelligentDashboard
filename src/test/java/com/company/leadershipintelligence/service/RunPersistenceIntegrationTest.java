package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.domain.enums.RunStatus;
import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.company.leadershipintelligence.repository.persistence.entity.AnswerRow;
import com.company.leadershipintelligence.repository.persistence.entity.AssessmentRow;
import com.company.leadershipintelligence.repository.persistence.entity.NewRun;
import com.company.leadershipintelligence.repository.persistence.entity.SnapshotRow;
import com.company.leadershipintelligence.repository.persistence.repository.AssessmentStore;
import com.company.leadershipintelligence.repository.persistence.repository.ScoringRunStore;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.CalculateCommand;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.service.query.ScoringRunView;
import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class RunPersistenceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    ScoreAssessmentService scoring;

    @Autowired
    ConfigurationService configurations;

    @Autowired
    AssessmentStore assessmentStore;

    @MockitoSpyBean
    ScoringRunStore runStore;

    private final Caller platform = Caller.platform("ops");
    private final Approval approval = new Approval("approver", null);
    private final TenantId a = new TenantId("run-a");
    private final TenantId b = new TenantId("run-b");

    @BeforeEach
    void platformDefault() {
        tenant("run-a");
        tenant("run-b");
        activatePlatform(ConfigurationSamples.sampleText());
    }

    private ConfigurationVersionView activatePlatform(String payload) {
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "platform", payload);
        assertThat(configurations.submitForValidation(platform, draft.id()).valid()).isTrue();
        return configurations.activate(platform, draft.id(), approval);
    }

    private static List<ScoringInput> inputs() {
        return List.of(new ScoringInput("Q01", 2), new ScoringInput("Q02", "OFTEN"), new ScoringInput("Q03", true),
                new ScoringInput("Q04", "only one key person"), new ScoringInput("Q05", 80));
    }

    private CalculateCommand command(TenantId tenant, List<ScoringInput> inputs, String key) {
        return new CalculateCommand(tenant, new OrganizationId("org-1"), "tpl-1", 1, "COMPLETED", clock.instant(), inputs, key);
    }

    private ScoringRunView calculate(TenantId tenant) {
        return scoring.calculate(command(tenant, inputs(), null));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static void assertCode(Runnable call, ScoringErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ScoringException.class, e -> assertThat(e.code()).isEqualTo(expected));
    }

    // ---- storing a calculation ----

    @Test
    void calculateStoresTheAssessmentTheRunAndEveryFinding() {
        ScoringRunView run = calculate(a);
        assertThat(run.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.scoreStatus()).isEqualTo(ScoreStatus.VALID);
        assertThat(run.finalScore()).isEqualByComparingTo("67.00");
        assertThat(run.interpretation()).isEqualTo("HEALTHY");
        assertThat(run.source()).isEqualTo("CALCULATE");
        assertThat(run.configurationVersion()).isEqualTo("platform_default:v1");
        assertThat(run.engineVersion()).isEqualTo("3.0.0");
        assertThat(run.algorithmVersion()).isEqualTo("1.0.0");
        assertThat(run.roundingPolicyVersion()).isEqualTo("1");
        assertThat(run.inputHash()).matches("sha256:[0-9a-f]{64}");
        assertThat(run.configurationHash()).matches("sha256:[0-9a-f]{64}");
        assertThat(run.assessmentId()).startsWith("asm-");
        assertThat(run.snapshotId()).startsWith("snp-");
        assertThat(run.layersJson()).contains("PLATFORM_DEFAULT");

        assertThat(count("select count(*) from assessment where tenant_id = 'run-a'")).isEqualTo(1);
        assertThat(count("select count(*) from assessment_snapshot where tenant_id = 'run-a'")).isEqualTo(1);
        assertThat(count("select count(*) from assessment_answer where tenant_id = 'run-a'")).isEqualTo(5);
        assertThat(count("select count(*) from scoring_run where tenant_id = 'run-a'")).isEqualTo(1);
        assertThat(count("select count(*) from scoring_dimension_result where run_id = ?", run.id())).isEqualTo(4);
        assertThat(count("select count(*) from scoring_contribution where run_id = ?", run.id())).isEqualTo(4);
        assertThat(count("select count(*) from scoring_pattern_evaluation where run_id = ?", run.id())).isEqualTo(1);
        assertThat(count("select count(*) from scoring_pattern_evidence where run_id = ?", run.id())).isEqualTo(2);
        assertThat(count("select count(*) from scoring_imbalance_result where run_id = ?", run.id())).isEqualTo(1);
        assertThat(count("select count(*) from scoring_penalty_result where run_id = ?", run.id())).isEqualTo(1);
        // every child row carries the tenant
        for (String table : List.of("scoring_dimension_result", "scoring_contribution", "scoring_pattern_evaluation",
                "scoring_pattern_evidence", "scoring_imbalance_result", "scoring_penalty_result")) {
            assertThat(count("select count(*) from " + table + " where tenant_id <> 'run-a'")).as(table).isZero();
        }
    }

    @Test
    void storedFindingsMatchTheCalculation() {
        ScoringRunView run = calculate(a);
        assertThat(jdbc.queryForObject("select raw_score from scoring_dimension_result where run_id = ? and dimension = 'TEAM_HEALTH'", BigDecimal.class, run.id()))
                .isEqualByComparingTo("25.00");
        assertThat(jdbc.queryForObject("select dimension_penalty from scoring_dimension_result where run_id = ? and dimension = 'TEAM_HEALTH'", BigDecimal.class, run.id()))
                .isEqualByComparingTo("12.00");
        assertThat(jdbc.queryForObject("select adjusted_score from scoring_dimension_result where run_id = ? and dimension = 'TEAM_HEALTH'", BigDecimal.class, run.id()))
                .isEqualByComparingTo("13.00");
        assertThat(jdbc.queryForObject("select label from scoring_dimension_result where run_id = ? and dimension = 'TEAM_HEALTH'", String.class, run.id()))
                .isEqualTo("CRITICAL");
        assertThat(jdbc.queryForObject("select status from scoring_pattern_evaluation where run_id = ?", String.class, run.id())).isEqualTo("MATCHED");
        assertThat(jdbc.queryForObject("select confidence from scoring_pattern_evaluation where run_id = ?", BigDecimal.class, run.id())).isEqualByComparingTo("1.00");
        assertThat(jdbc.queryForObject("select status || '/' || effective_deduction || '/' || suppressed_amount from scoring_penalty_result where run_id = ?",
                String.class, run.id())).isEqualTo("APPLIED/12.00/0.00");
        assertThat(jdbc.queryForObject("select status from scoring_imbalance_result where run_id = ?", String.class, run.id())).isEqualTo("NOT_DETECTED");
        assertThat(jdbc.queryForObject("select gap from scoring_imbalance_result where run_id = ?", BigDecimal.class, run.id())).isEqualByComparingTo("-25");
    }

    @Test
    void theStoredTraceIsReturnedExactlyAsStored() throws Exception {
        ScoringRunView run = calculate(a);
        String fromDatabase = jdbc.queryForObject("select trace::text from scoring_run where id = ?", String.class, run.id());
        assertThat(run.traceJson()).isEqualTo(fromDatabase);
        assertThat(scoring.get(a, run.id()).traceJson()).isEqualTo(fromDatabase);
        com.fasterxml.jackson.databind.JsonNode trace = new com.fasterxml.jackson.databind.ObjectMapper().readTree(fromDatabase);
        assertThat(trace.get("steps")).hasSize(8);
        assertThat(trace.get("steps").get(7).get("stage").asText()).isEqualTo("OVERALL");
        assertThat(fromDatabase).doesNotContain("key person");
    }

    @Test
    void anInsufficientDataRunIsCompletedWithoutANumber() {
        List<ScoringInput> missingTechnical = List.of(new ScoringInput("Q01", 2), new ScoringInput("Q02", "OFTEN"),
                new ScoringInput("Q04", "text"), new ScoringInput("Q05", 80));
        ScoringRunView run = scoring.calculate(command(a, missingTechnical, null));
        assertThat(run.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.scoreStatus()).isEqualTo(ScoreStatus.INSUFFICIENT_DATA);
        assertThat(run.finalScore()).isNull();
        assertThat(run.interpretation()).isNull();
        assertThat(jdbc.queryForObject("select status from scoring_dimension_result where run_id = ? and dimension = 'TECHNICAL_RISK'", String.class, run.id()))
                .isEqualTo("INSUFFICIENT_DATA");
        assertThat(jdbc.queryForObject("select raw_score from scoring_dimension_result where run_id = ? and dimension = 'TECHNICAL_RISK'", BigDecimal.class, run.id()))
                .isNull();
    }

    @Test
    void identicalInputsOnTwoAssessmentsGiveIdenticalTraces() {
        ScoringRunView first = calculate(a);
        ScoringRunView second = calculate(a);
        assertThat(second.assessmentId()).isNotEqualTo(first.assessmentId());
        assertThat(second.traceJson()).isEqualTo(first.traceJson());
        assertThat(second.finalScore()).isEqualByComparingTo(first.finalScore());
        assertThat(second.configurationHash()).isEqualTo(first.configurationHash());
        assertThat(second.inputHash()).isNotEqualTo(first.inputHash());
    }

    // ---- /score identity, /recalculate, history ----

    @Test
    void scoreReusesTheRunWithTheSameIdentity() {
        ScoringRunView first = calculate(a);
        ScoringRunView again = scoring.score(a, new AssessmentId(first.assessmentId()), null);
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(count("select count(*) from scoring_run where assessment_id = ?", first.assessmentId())).isEqualTo(1);
    }

    @Test
    void recalculateAlwaysCreatesANewRunAndKeepsTheOldOne() {
        ScoringRunView first = calculate(a);
        clock.advance(Duration.ofMinutes(5));
        ScoringRunView second = scoring.recalculate(a, new AssessmentId(first.assessmentId()), null);
        ScoringRunView third = scoring.recalculate(a, new AssessmentId(first.assessmentId()), null);
        assertThat(List.of(first.id(), second.id(), third.id())).doesNotHaveDuplicates();
        assertThat(second.source()).isEqualTo("RECALCULATE");
        assertThat(second.configurationSelection()).isEqualTo("DEFAULT");
        assertThat(second.finalScore()).isEqualByComparingTo(first.finalScore());
        assertThat(count("select count(*) from scoring_run where assessment_id = ?", first.assessmentId())).isEqualTo(3);
        assertThat(scoring.latest(a, new AssessmentId(first.assessmentId())).orElseThrow().id()).isEqualTo(third.id());
        // history is retrievable and unchanged
        assertThat(scoring.get(a, first.id()).traceJson()).isEqualTo(first.traceJson());
    }

    @Test
    void aConfigurationChangeGivesANewRunAndLeavesTheOldOneReproducible() {
        ScoringRunView first = calculate(a);
        String before = jdbc.queryForObject("select configuration_hash || '|' || configuration_version || '|' || trace::text from scoring_run where id = ?",
                String.class, first.id());
        Caller admin = Caller.tenant(a, "admin");
        ConfigurationVersionView draft = configurations.createDraft(admin, ConfigurationTarget.tenant(a), "override", "{\"overall\":{\"tolerance\":0.0005}}");
        configurations.submitForValidation(admin, draft.id());
        configurations.activate(admin, draft.id(), approval);
        clock.advance(Duration.ofMinutes(1));

        ScoringRunView rescored = scoring.score(a, new AssessmentId(first.assessmentId()), null);
        assertThat(rescored.id()).isNotEqualTo(first.id());
        assertThat(rescored.configurationHash()).isNotEqualTo(first.configurationHash());
        assertThat(rescored.configurationVersion()).isEqualTo("platform_default:v1|tenant:v1");
        assertThat(rescored.layersJson()).contains("TENANT");
        assertThat(jdbc.queryForObject("select configuration_hash || '|' || configuration_version || '|' || trace::text from scoring_run where id = ?",
                String.class, first.id())).isEqualTo(before);
    }

    @Test
    void recalculateCanUseAnExplicitlySelectedConfigurationVersion() {
        ConfigurationVersionView v1 = jdbcPlatformVersion();
        ScoringRunView first = calculate(a);
        activatePlatform(ConfigurationSamples.sampleText().replace("\"tolerance\": 0.0001", "\"tolerance\": 0.0002"));
        clock.advance(Duration.ofMinutes(1));

        ScoringRunView explicit = scoring.recalculate(a, new AssessmentId(first.assessmentId()), v1.id());
        assertThat(explicit.configurationSelection()).isEqualTo("EXPLICIT");
        assertThat(explicit.configurationVersion()).isEqualTo("explicit:platform_default:v1");
        assertThat(explicit.configurationHash()).isEqualTo(first.configurationHash());
        ScoringRunView current = scoring.recalculate(a, new AssessmentId(first.assessmentId()), null);
        assertThat(current.configurationVersion()).isEqualTo("platform_default:v2");
        assertThat(current.configurationHash()).isNotEqualTo(first.configurationHash());
    }

    private ConfigurationVersionView jdbcPlatformVersion() {
        return configurations.history(platform, ConfigurationTarget.platform()).get(0);
    }

    @Test
    void explicitConfigurationMustExistBeActiveOrArchivedAndBelongToTheTenant() {
        ScoringRunView first = calculate(a);
        AssessmentId id = new AssessmentId(first.assessmentId());
        assertCode(() -> scoring.recalculate(a, id, UUID.randomUUID()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "p", ConfigurationSamples.sampleText());
        assertCode(() -> scoring.recalculate(a, id, draft.id()), ScoringErrorCode.CONFIGURATION_NOT_ACTIVE);
        Caller adminB = Caller.tenant(b, "admin-b");
        ConfigurationVersionView bVersion = configurations.createDraft(adminB, ConfigurationTarget.tenant(b), "b", "{\"overall\":{\"tolerance\":0.0003}}");
        configurations.submitForValidation(adminB, bVersion.id());
        configurations.activate(adminB, bVersion.id(), approval);
        assertCode(() -> scoring.recalculate(a, id, bVersion.id()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
    }

    // ---- idempotency ----

    @Test
    void theSameKeyAndRequestReturnsTheSameRunWithoutDuplicates() {
        ScoringRunView first = scoring.calculate(command(a, inputs(), "key-1"));
        ScoringRunView second = scoring.calculate(command(a, inputs(), "key-1"));
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(count("select count(*) from assessment where tenant_id = 'run-a'")).isEqualTo(1);
        assertThat(count("select count(*) from scoring_run where tenant_id = 'run-a'")).isEqualTo(1);
    }

    @Test
    void reusingAKeyForADifferentRequestIsAConflict() {
        scoring.calculate(command(a, inputs(), "key-2"));
        List<ScoringInput> other = new ArrayList<>(inputs());
        other.set(0, new ScoringInput("Q01", 5));
        assertCode(() -> scoring.calculate(command(a, other, "key-2")), ScoringErrorCode.IDEMPOTENCY_CONFLICT);
        assertThat(count("select count(*) from assessment where tenant_id = 'run-a'")).isEqualTo(1);
    }

    @Test
    void keysAreIndependentPerTenant() {
        ScoringRunView forA = scoring.calculate(command(a, inputs(), "shared-key"));
        ScoringRunView forB = scoring.calculate(command(b, inputs(), "shared-key"));
        assertThat(forB.id()).isNotEqualTo(forA.id());
    }

    @Test
    void scoreHonoursAnIdempotencyKeyToo() {
        ScoringRunView first = calculate(a);
        ScoringRunView one = scoring.score(a, new AssessmentId(first.assessmentId()), "score-key");
        ScoringRunView two = scoring.score(a, new AssessmentId(first.assessmentId()), "score-key");
        assertThat(two.id()).isEqualTo(one.id());
        ScoringRunView second = calculate(a);
        assertCode(() -> scoring.score(a, new AssessmentId(second.assessmentId()), "score-key"), ScoringErrorCode.IDEMPOTENCY_CONFLICT);
    }

    // ---- failures leave nothing behind ----

    @Test
    void invalidInputsStoreNothing() {
        assertCode(() -> scoring.calculate(command(a, List.of(new ScoringInput("NOPE", 1)), null)), ScoringErrorCode.QUESTION_NOT_CONFIGURED);
        assertCode(() -> scoring.calculate(command(a, List.of(new ScoringInput("Q01", 99)), null)), ScoringErrorCode.INVALID_SCALE_VALUE);
        assertThat(count("select count(*) from assessment where tenant_id = 'run-a'")).isZero();
        assertThat(count("select count(*) from assessment_snapshot where tenant_id = 'run-a'")).isZero();
        assertThat(count("select count(*) from scoring_run where tenant_id = 'run-a'")).isZero();
        assertThat(count("select count(*) from organization where tenant_id = 'run-a'")).isZero();
    }

    @Test
    void anIncompleteAssessmentIsRejectedAndNothingIsStored() {
        CalculateCommand inProgress = new CalculateCommand(a, new OrganizationId("org-1"), "tpl-1", 1, "IN_PROGRESS", null, inputs(), null);
        assertCode(() -> scoring.calculate(inProgress), ScoringErrorCode.ASSESSMENT_NOT_COMPLETED);
        CalculateCommand noDate = new CalculateCommand(a, new OrganizationId("org-1"), "tpl-1", 1, "COMPLETED", null, inputs(), null);
        assertCode(() -> scoring.calculate(noDate), ScoringErrorCode.ASSESSMENT_NOT_COMPLETED);
        assertThat(count("select count(*) from assessment where tenant_id = 'run-a'")).isZero();
    }

    @Test
    void aDatabaseFailureAfterTheRunWasWrittenRollsBackEverything() {
        Mockito.doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("simulated failure after the run and its findings were written");
        }).when(runStore).insert(any(NewRun.class));
        assertThatThrownBy(() -> calculate(a)).hasMessageContaining("simulated failure");
        for (String table : List.of("assessment", "assessment_snapshot", "assessment_answer", "scoring_run", "scoring_dimension_result",
                "scoring_contribution", "scoring_pattern_evaluation", "scoring_pattern_evidence", "scoring_imbalance_result",
                "scoring_penalty_result", "organization", "assessment_template")) {
            assertThat(count("select count(*) from " + table + " where tenant_id = 'run-a'")).as(table).isZero();
        }
    }

    @Test
    void anEngineFailureOnAStoredAssessmentIsRecordedAsFailedAndNeverReturnedAsAResult() {
        ScoringRunView first = calculate(a);
        // a new configuration version no longer knows question Q05, which the stored assessment answered
        activatePlatform(ConfigurationSamples.sampleText().replace("Q05", "Q06"));
        clock.advance(Duration.ofMinutes(1));

        assertCode(() -> scoring.score(a, new AssessmentId(first.assessmentId()), null), ScoringErrorCode.QUESTION_NOT_CONFIGURED);

        UUID failedId = jdbc.queryForObject("select id from scoring_run where status = 'FAILED' and assessment_id = ?", UUID.class, first.assessmentId());
        ScoringRunView failed = scoring.get(a, failedId);
        assertThat(failed.status()).isEqualTo(RunStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("QUESTION_NOT_CONFIGURED");
        assertThat(failed.scoreStatus()).isNull();
        assertThat(failed.finalScore()).isNull();
        assertThat(failed.traceJson()).isNull();
        assertThat(count("select count(*) from scoring_dimension_result where run_id = ?", failedId)).isZero();
        // the last valid result is still the first run
        assertThat(scoring.latest(a, new AssessmentId(first.assessmentId())).orElseThrow().id()).isEqualTo(first.id());
        // a failed run does not block a later retry
        assertCode(() -> scoring.score(a, new AssessmentId(first.assessmentId()), null), ScoringErrorCode.QUESTION_NOT_CONFIGURED);
        assertThat(count("select count(*) from scoring_run where status = 'FAILED' and assessment_id = ?", first.assessmentId())).isEqualTo(2);
    }

    // ---- concurrency ----

    @Test
    void concurrentIdenticalScoringRequestsProduceExactlyOneRun() throws Exception {
        String assessmentId = "asm-concurrent";
        String snapshotId = "snp-concurrent";
        assessmentStore.register("run-a", "org-1", "tpl-1", 1);
        assessmentStore.insertAssessment(new AssessmentRow(assessmentId, "run-a", "org-1", "tpl-1", 1, "COMPLETED", clock.instant()));
        assessmentStore.insertSnapshot(new SnapshotRow(snapshotId, "run-a", assessmentId, 1, "unused"), List.of(
                new AnswerRow("Q01", "2"), new AnswerRow("Q02", "\"OFTEN\""), new AnswerRow("Q03", "true"),
                new AnswerRow("Q04", "\"only one key person\""), new AnswerRow("Q05", "80")));

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ScoringRunView>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<ScoringRunView> task = () -> {
                ready.countDown();
                go.await();
                return scoring.score(a, new AssessmentId(assessmentId), null);
            };
            results.add(pool.submit(task));
        }
        ready.await();
        go.countDown();
        List<UUID> ids = new ArrayList<>();
        for (Future<ScoringRunView> result : results) {
            ids.add(result.get().id());
        }
        pool.shutdown();
        assertThat(ids).containsOnly(ids.get(0));
        assertThat(count("select count(*) from scoring_run where assessment_id = ? and status = 'COMPLETED'", assessmentId)).isEqualTo(1);
        assertThat(count("select count(*) from scoring_dimension_result where tenant_id = 'run-a'")).isEqualTo(4);
    }

    // ---- tenant isolation (specv3.md Section 25.6) ----

    @Test
    void anotherTenantCannotScoreRecalculateOrReadAnAssessmentOrRun() {
        ScoringRunView run = calculate(a);
        AssessmentId id = new AssessmentId(run.assessmentId());
        assertCode(() -> scoring.score(b, id, null), ScoringErrorCode.ASSESSMENT_NOT_FOUND);
        assertCode(() -> scoring.recalculate(b, id, null), ScoringErrorCode.ASSESSMENT_NOT_FOUND);
        assertCode(() -> scoring.latest(b, id), ScoringErrorCode.ASSESSMENT_NOT_FOUND);
        assertCode(() -> scoring.get(b, run.id()), ScoringErrorCode.SCORING_RUN_NOT_FOUND);
        assertThat(count("select count(*) from scoring_run where tenant_id = 'run-b'")).isZero();
        // the owner is unaffected and each tenant has its own history
        ScoringRunView forB = calculate(b);
        assertThat(scoring.get(a, run.id()).id()).isEqualTo(run.id());
        assertThat(scoring.get(b, forB.id()).assessmentId()).isNotEqualTo(run.assessmentId());
    }

    @Test
    void anUnknownTenantIsRejected() {
        assertCode(() -> scoring.calculate(command(new TenantId("ghost"), inputs(), null)), ScoringErrorCode.TENANT_ACCESS_DENIED);
    }

    // ---- immutability ----

    @Test
    void storedRunsAndFindingsCannotBeChangedOrDeleted() {
        ScoringRunView run = calculate(a);
        assertThatThrownBy(() -> jdbc.update("update scoring_run set final_score = 1 where id = ?", run.id()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("delete from scoring_run where id = ?", run.id()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("update scoring_penalty_result set effective_deduction = 0 where run_id = ?", run.id()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("delete from scoring_contribution where run_id = ?", run.id()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        assertThat(scoring.get(a, run.id()).finalScore()).isEqualByComparingTo("67.00");
    }

    @Test
    void theDatabaseRejectsAPenaltyThatDoesNotReconcile() {
        ScoringRunView run = calculate(a);
        assertThatThrownBy(() -> jdbc.update("insert into scoring_penalty_result (run_id, tenant_id, penalty_id, version, code, target, strategy, category, "
                + "priority, rule_order, stacking_policy, trigger_pattern_id, trigger_pattern_version, status, reason, candidate_deduction, "
                + "effective_deduction, suppressed_amount, evidence_refs) values (?, 'run-a', 'X', 1, 'X', 'TEAM_HEALTH', 'FIXED', 'C', 0, 0, "
                + "'STACK', 'P', 1, 'APPLIED', 'APPLIED_IN_FULL', 10, 4, 3, '[]'::jsonb)", run.id()))
                .isInstanceOf(DataAccessException.class);
    }
}
