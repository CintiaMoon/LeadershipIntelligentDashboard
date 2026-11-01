package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.config.properties.EngineProperties;
import com.company.leadershipintelligence.domain.enums.ResponseScope;
import com.company.leadershipintelligence.domain.enums.RunStatus;
import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.ScopedResponse;
import com.company.leadershipintelligence.domain.model.ScoringContext;
import com.company.leadershipintelligence.domain.model.ScoringResult;
import com.company.leadershipintelligence.domain.service.InputHasher;
import com.company.leadershipintelligence.domain.service.ScoringOrchestrator;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.persistence.entity.AnswerRow;
import com.company.leadershipintelligence.repository.persistence.entity.AssessmentRow;
import com.company.leadershipintelligence.repository.persistence.entity.NewFailedRun;
import com.company.leadershipintelligence.repository.persistence.entity.NewRun;
import com.company.leadershipintelligence.repository.persistence.entity.SnapshotRow;
import com.company.leadershipintelligence.repository.persistence.entity.StoredRunRow;
import com.company.leadershipintelligence.repository.persistence.mapper.AnswerJson;
import com.company.leadershipintelligence.repository.persistence.mapper.TraceJson;
import com.company.leadershipintelligence.repository.persistence.repository.AccessAuditStore;
import com.company.leadershipintelligence.repository.persistence.repository.AssessmentStore;
import com.company.leadershipintelligence.repository.persistence.repository.ScoringRunStore;
import com.company.leadershipintelligence.service.command.CalculateCommand;
import com.company.leadershipintelligence.service.command.OrganizationScope;
import com.company.leadershipintelligence.service.query.AnswerView;
import com.company.leadershipintelligence.service.query.EffectiveConfiguration;
import com.company.leadershipintelligence.service.query.ScoringRunView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Application use cases around the scoring engine (specv3.md Sections 6 steps 1-5 and 24-25, 7, 26, 27):
 * <ul>
 *   <li>{@link #calculate}: new assessment from a request body; nothing is stored unless scoring succeeds;</li>
 *   <li>{@link #score}: score a stored assessment, reusing an existing run with the same identity (tenant, snapshot,
 *       input hash, configuration hash, engine and algorithm version);</li>
 *   <li>{@link #recalculate}: always a new run, optionally with an explicitly selected configuration version.</li>
 * </ul>
 * Answers may carry a scope (decision D45). Scoring then produces one ORGANIZATION run from all answers and one TEAM run
 * for each team that has answers, all written in one transaction; the organization run is the result of the request
 * and the team runs point to it. A request with one answer per question behaves exactly as before.
 * Every step is tenant-scoped. This class is deliberately not transactional: persistence goes through
 * {@link RunRecorder} so a duplicate (a concurrent identical request) can be recognised and answered with the
 * existing run. A failed engine run on a stored assessment is recorded as FAILED and never returned as a result.
 */
@Service
public class ScoreAssessmentService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AssessmentStore assessments;
    private final ScoringRunStore runs;
    private final RunRecorder recorder;
    private final EffectiveConfigurationService effective;
    private final ScoringOrchestrator engine;
    private final EngineProperties properties;
    private final Clock clock;
    private final ScoringMetrics metrics;
    private final ScoringLog log;
    private final AccessAuditStore accessAudit;

    public ScoreAssessmentService(AssessmentStore assessments, ScoringRunStore runs, RunRecorder recorder,
                                  EffectiveConfigurationService effective, ScoringOrchestrator engine, EngineProperties properties,
                                  Clock clock, ScoringMetrics metrics, ScoringLog log, AccessAuditStore accessAudit) {
        this.metrics = metrics;
        this.log = log;
        this.accessAudit = accessAudit;
        this.assessments = assessments;
        this.runs = runs;
        this.recorder = recorder;
        this.effective = effective;
        this.engine = engine;
        this.properties = properties;
        this.clock = clock;
    }

    // ---------------- calculate ----------------

    public ScoringRunView calculate(CalculateCommand command) {
        return measured(command.tenantId().value(), "CALCULATE", () -> doCalculate(command));
    }

    private ScoringRunView doCalculate(CalculateCommand command) {
        String tenant = command.tenantId().value();
        String requestHash = requestHash(command);
        Optional<ScoringRunView> repeated = repeatedRequest(tenant, command.idempotencyKey(), requestHash);
        if (repeated.isPresent()) {
            return repeated.get();
        }
        if (!assessments.tenantExists(tenant)) {
            throw new ScoringException(ScoringErrorCode.TENANT_ACCESS_DENIED, "unknown tenant");
        }
        requireCompleted(command.status(), command.completedAt());

        Instant started = clock.instant();
        AssessmentId assessmentId = new AssessmentId("asm-" + UUID.randomUUID());
        AssessmentSnapshotId snapshotId = new AssessmentSnapshotId("snp-" + UUID.randomUUID());
        EffectiveConfiguration configuration = metrics.observe("lis.configuration.resolve",
                () -> command.explicitConfigurationVersionId() == null
                        ? effective.resolve(command.tenantId(), command.organizationId(), command.templateId())
                        : effective.resolveExplicit(command.tenantId(), command.explicitConfigurationVersionId()));
        command.declaredTypes().forEach((questionId, type) -> configuration.snapshot().question(questionId).ifPresent(definition -> {
            if (definition.type() != type) {
                throw new ScoringException(ScoringErrorCode.INVALID_ANSWER_TYPE,
                        "the declared type of question " + questionId + " differs from its configured type", Map.of("questionId", questionId));
            }
        }));
        List<ScopedResponse> responses = command.scopedResponses();
        ScoringContext context = context(command.tenantId(), command.organizationId(), assessmentId, snapshotId, configuration);
        Computed computed = compute(context, responses, configuration);

        Built built = build(computed, "CALCULATE", command.explicitConfigurationVersionId() == null ? "DEFAULT" : "EXPLICIT", configuration,
                command.idempotencyKey(), requestHash, started);
        AssessmentRow assessment = new AssessmentRow(assessmentId.value(), tenant, command.organizationId().value(), command.templateId(),
                command.templateVersion(), command.status(), command.completedAt());
        SnapshotRow snapshot = new SnapshotRow(snapshotId.value(), tenant, assessmentId.value(), 1, computed.inputHash());
        try {
            metrics.observe("lis.persistence.store", () -> {
                recorder.recordCalculation(tenant, command.organizationId().value(), command.templateId(), command.templateVersion(),
                        assessment, snapshot, answerRows(responses), built.organization(), built.teams());
                return built.organization();
            });
        } catch (DuplicateKeyException e) {
            return afterDuplicate(tenant, command.idempotencyKey(), requestHash, Optional::empty, e);
        }
        return view(runs.findById(tenant, built.organization().runId()).orElseThrow());
    }

    // ---------------- score ----------------

    public ScoringRunView score(TenantId tenant, AssessmentId assessmentId, String idempotencyKey) {
        return score(tenant, assessmentId, idempotencyKey, OrganizationScope.unrestricted());
    }

    public ScoringRunView score(TenantId tenant, AssessmentId assessmentId, String idempotencyKey, OrganizationScope scope) {
        return measured(tenant.value(), "SCORE", () -> doScore(tenant, assessmentId, idempotencyKey, scope));
    }

    private ScoringRunView doScore(TenantId tenant, AssessmentId assessmentId, String idempotencyKey, OrganizationScope scope) {
        String requestHash = InputHasher.sha256("score|" + assessmentId.value());
        Optional<ScoringRunView> repeated = repeatedRequest(tenant.value(), idempotencyKey, requestHash);
        if (repeated.isPresent()) {
            scope.require(repeated.get().organizationId());
            return repeated.get();
        }
        Loaded loaded = load(tenant, assessmentId);
        scope.require(loaded.assessment().organizationId());
        EffectiveConfiguration configuration = effective.resolve(tenant, loaded.organization(), loaded.assessment().templateId());
        String inputHash = organizationHash(loaded.snapshotId(), loaded.responses());
        Supplier<Optional<StoredRunRow>> identity = () -> runs.findByIdentity(tenant.value(), loaded.snapshotId().value(), inputHash,
                configuration.configurationHash(), properties.version(), properties.algorithmVersion());
        Optional<StoredRunRow> existing = identity.get();
        if (existing.isPresent()) {
            bindReusedRun(tenant.value(), idempotencyKey, requestHash, existing.get());
            return view(existing.get());
        }
        return executeAndStore(tenant, loaded, configuration, "SCORE", "DEFAULT", idempotencyKey, requestHash, identity);
    }

    // ---------------- recalculate ----------------

    /** @param explicitVersionId a configuration version to use instead of the effective one, or null */
    public ScoringRunView recalculate(TenantId tenant, AssessmentId assessmentId, UUID explicitVersionId) {
        return recalculate(tenant, assessmentId, explicitVersionId, OrganizationScope.unrestricted());
    }

    public ScoringRunView recalculate(TenantId tenant, AssessmentId assessmentId, UUID explicitVersionId, OrganizationScope scope) {
        return measured(tenant.value(), "RECALCULATE", () -> doRecalculate(tenant, assessmentId, explicitVersionId, scope));
    }

    private ScoringRunView doRecalculate(TenantId tenant, AssessmentId assessmentId, UUID explicitVersionId, OrganizationScope scope) {
        Loaded loaded = load(tenant, assessmentId);
        scope.require(loaded.assessment().organizationId());
        EffectiveConfiguration configuration = explicitVersionId == null
                ? effective.resolve(tenant, loaded.organization(), loaded.assessment().templateId())
                : effective.resolveExplicit(tenant, explicitVersionId);
        return executeAndStore(tenant, loaded, configuration, "RECALCULATE",
                explicitVersionId == null ? "DEFAULT" : "EXPLICIT", null, null, Optional::empty);
    }

    // ---------------- reads ----------------

    /** The latest completed run of an assessment; failed runs are never returned. */
    public Optional<ScoringRunView> latest(TenantId tenant, AssessmentId assessmentId) {
        return latest(tenant, assessmentId, OrganizationScope.unrestricted());
    }

    public Optional<ScoringRunView> latest(TenantId tenant, AssessmentId assessmentId, OrganizationScope scope) {
        AssessmentRow assessment = assessments.findAssessment(tenant.value(), assessmentId.value())
                .orElseThrow(() -> assessmentNotFound(assessmentId));
        scope.require(assessment.organizationId());
        return runs.findLatestCompleted(tenant.value(), assessmentId.value()).map(this::view);
    }

    public ScoringRunView get(TenantId tenant, UUID runId) {
        return get(tenant, runId, OrganizationScope.unrestricted());
    }

    public ScoringRunView get(TenantId tenant, UUID runId, OrganizationScope scope) {
        ScoringRunView run = runs.findById(tenant.value(), runId).map(this::view).orElseThrow(() -> new ScoringException(
                ScoringErrorCode.SCORING_RUN_NOT_FOUND, "scoring run not found", Map.of("scoringRunId", runId.toString())));
        scope.require(run.organizationId());
        return run;
    }

    /**
     * The team runs produced together with an organization run, in team order (decision D45). An assessment scored from
     * one answer per question has none, and no query is made for it.
     */
    public List<ScoringRunView> teamRuns(TenantId tenant, ScoringRunView organizationRun) {
        if (organizationRun.isTeamRun() || organizationRun.responseSummariesJson() == null) {
            return List.of();
        }
        return runs.findTeamRuns(tenant.value(), organizationRun.id()).stream().map(this::view).toList();
    }

    // ---------------- internals ----------------

    /** What scoring one assessment produced: the organization result and a result for each team that could be scored. */
    private record Computed(ScoringResult organization, String inputHash, boolean scoped, List<TeamResult> teams, List<String> unscoredTeams) {
    }

    private record TeamResult(String teamId, ScoringResult result, String inputHash) {
    }

    private record Built(NewRun organization, List<NewRun> teams) {
    }

    /**
     * Scores the organization unit and, for scoped answers, one unit per team. An organization failure fails the request.
     * A team that cannot be scored only because a question is required and has too few answers has no run and is
     * reported as unscored; any other failure is also a failure of the organization unit and is raised.
     */
    private Computed compute(ScoringContext context, List<ScopedResponse> responses, EffectiveConfiguration configuration) {
        boolean scoped = !isEarlierForm(responses);
        ScoringResult organization = executeUnit(context, responses, scoped, null, configuration);
        String organizationHash = organizationHash(context.assessmentSnapshotId(), responses);
        List<TeamResult> teams = new ArrayList<>();
        List<String> unscored = new ArrayList<>();
        if (scoped) {
            for (String team : teamIds(responses)) {
                try {
                    teams.add(new TeamResult(team, executeUnit(context, responses, true, team, configuration),
                            InputHasher.scopedInputHash(context.assessmentSnapshotId(), responses, team)));
                } catch (ScoringException e) {
                    if (e.code() != ScoringErrorCode.MISSING_REQUIRED_INPUT) {
                        throw e;
                    }
                    unscored.add(team);
                }
            }
        }
        return new Computed(organization, organizationHash, scoped, teams, unscored);
    }

    /** Runs the engine for one unit; any unexpected error is reported as a scoring failure without internal details. */
    private ScoringResult executeUnit(ScoringContext context, List<ScopedResponse> responses, boolean scoped, String teamId,
                                      EffectiveConfiguration configuration) {
        try {
            return metrics.observe("lis.engine.execute", () -> scoped
                    ? engine.scoreResponses(context, responses, teamId, configuration.snapshot())
                    : engine.score(context, responses.stream().map(ScopedResponse::asInput).toList(), configuration.snapshot()));
        } catch (ScoringException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ScoringException(ScoringErrorCode.SCORING_EXECUTION_FAILED, "scoring failed unexpectedly");
        }
    }

    private Built build(Computed computed, String source, String selection, EffectiveConfiguration configuration, String idempotencyKey,
                        String requestHash, Instant started) {
        String organizationSummaries = computed.scoped() ? summariesJson(computed.organization(), computed.unscoredTeams()) : null;
        NewRun organization = newRun(computed.organization(), source, selection, computed.inputHash(), configuration, idempotencyKey,
                requestHash, started, "ORGANIZATION", null, null, organizationSummaries);
        List<NewRun> teams = computed.teams().stream().map(t -> newRun(t.result(), source, selection, t.inputHash(), configuration, null,
                null, started, "TEAM", t.teamId(), organization.runId(), summariesJson(t.result(), List.of()))).toList();
        return new Built(organization, teams);
    }

    private ScoringRunView executeAndStore(TenantId tenant, Loaded loaded, EffectiveConfiguration configuration, String source,
                                           String selection, String idempotencyKey, String requestHash,
                                           Supplier<Optional<StoredRunRow>> identity) {
        Instant started = clock.instant();
        ScoringContext context = context(tenant, loaded.organization(), new AssessmentId(loaded.assessment().id()), loaded.snapshotId(),
                configuration);
        Computed computed;
        try {
            computed = compute(context, loaded.responses(), configuration);
        } catch (ScoringException failure) {
            recorder.recordFailure(new NewFailedRun(UUID.randomUUID(), tenant.value(), loaded.organization().value(),
                    loaded.assessment().id(), loaded.snapshotId().value(), source, selection, properties.version(),
                    properties.algorithmVersion(), configuration.effectiveVersion(), configuration.configurationHash(),
                    organizationHash(loaded.snapshotId(), loaded.responses()),
                    TraceJson.writeValue(configuration.layers()), failure.code().name(), failure.getMessage(), started, clock.instant()));
            throw failure;
        }
        Built built = build(computed, source, selection, configuration, idempotencyKey, requestHash, started);
        try {
            metrics.observe("lis.persistence.store", () -> {
                recorder.recordRuns(built.organization(), built.teams());
                return built.organization();
            });
        } catch (DuplicateKeyException e) {
            return afterDuplicate(tenant.value(), idempotencyKey, requestHash, identity, e);
        }
        return view(runs.findById(tenant.value(), built.organization().runId()).orElseThrow());
    }

    private ScoringContext context(TenantId tenant, OrganizationId organization, AssessmentId assessmentId, AssessmentSnapshotId snapshotId,
                                   EffectiveConfiguration configuration) {
        return new ScoringContext(tenant, organization, assessmentId, snapshotId, configuration.snapshot().version(),
                properties.version(), properties.algorithmVersion());
    }

    private NewRun newRun(ScoringResult result, String source, String selection, String inputHash, EffectiveConfiguration configuration,
                          String idempotencyKey, String requestHash, Instant started, String scopeType, String scopeId,
                          UUID parentRunId, String responseSummariesJson) {
        return new NewRun(UUID.randomUUID(), result, result.context().tenantId().value(), result.context().organizationId().value(),
                source, selection, inputHash, configuration.configurationHash(), TraceJson.writeValue(configuration.layers()),
                TraceJson.write(result.trace()), idempotencyKey, idempotencyKey == null ? null : requestHash, started, clock.instant(),
                scopeType, scopeId, parentRunId, responseSummariesJson);
    }

    /** How each question's answers were combined, and the teams that could not be scored. Never an answer. */
    private static String summariesJson(ScoringResult result, List<String> unscoredTeams) {
        Map<String, Object> summaries = new LinkedHashMap<>();
        summaries.put("questions", result.facts().responseSummaries());
        if (!unscoredTeams.isEmpty()) {
            summaries.put("unscoredTeams", unscoredTeams);
        }
        return TraceJson.writeValue(summaries);
    }

    /** Counts, times and logs one scoring request. The result and any error pass through unchanged. */
    private ScoringRunView measured(String tenant, String source, Supplier<ScoringRunView> work) {
        long started = System.nanoTime();
        try {
            ScoringRunView run = metrics.request(source, work, ScoringRunView::scoreStatus);
            log.completed(tenant, source, run, (System.nanoTime() - started) / 1_000_000);
            return run;
        } catch (ScoringException e) {
            log.failed(tenant, source, e.code().name(), (System.nanoTime() - started) / 1_000_000);
            throw e;
        } catch (RuntimeException e) {
            log.failed(tenant, source, "UNEXPECTED", (System.nanoTime() - started) / 1_000_000);
            throw e;
        }
    }

    /**
     * The answers a run was scored with, for the roles that may see evidence (decision D42). The run is looked up with the
     * caller's tenant and organizations first, so another tenant's run is simply not found. A team run shows only its
     * team's answers. Member answers come as one group per question (count and sorted values), and a group smaller than
     * the minimum shows only its count (decision D45). The read is audited with who and how many, never what; answers are
     * never logged.
     */
    public List<AnswerView> answersOf(TenantId tenant, String actor, UUID runId, OrganizationScope scope) {
        ScoringRunView run = get(tenant, runId, scope);
        List<AnswerRow> rows = assessments.answers(tenant.value(), run.snapshotId()).stream()
                .filter(a -> !run.isTeamRun() || (!"ORGANIZATION".equals(a.scopeLevel()) && run.scopeId().equals(a.teamId())))
                .toList();
        List<AnswerView> answers = groupAnswers(rows, belowMinimum(run));
        accessAudit.record(tenant.value(), actor, "ANSWERS_READ", runId, "{\"count\":" + answers.size() + "}", clock.instant());
        return answers;
    }

    private static List<AnswerView> groupAnswers(List<AnswerRow> rows, java.util.Set<String> belowMinimum) {
        List<AnswerView> views = new ArrayList<>();
        Map<String, List<Object>> members = new LinkedHashMap<>();
        for (AnswerRow row : rows) {
            Object answer = AnswerJson.read(row.answerJson());
            if ("MEMBER".equals(row.scopeLevel())) {
                // a blank answer was not counted as a response, so it is not part of the group either
                if (answer != null && !(answer instanceof String text && text.isBlank())
                        && !(answer instanceof java.util.Collection<?> selection && selection.isEmpty())) {
                    members.computeIfAbsent(row.questionId(), k -> new ArrayList<>()).add(answer);
                }
            } else {
                views.add(new AnswerView(row.questionId(), answer, row.scopeLevel(), row.teamId().isEmpty() ? null : row.teamId(),
                        row.declaredRespondents(), null, null, null));
            }
        }
        members.forEach((question, values) -> {
            boolean suppressed = belowMinimum.contains(question);
            List<Object> sorted = suppressed ? List.of() : values.stream().sorted(Comparator.comparing(ScoreAssessmentService::sortKey)).toList();
            views.add(new AnswerView(question, null, "MEMBER", null, null, values.size(), sorted, suppressed));
        });
        views.sort(Comparator.comparing(AnswerView::questionId).thenComparing(AnswerView::scope)
                .thenComparing(v -> v.teamId() == null ? "" : v.teamId()));
        return views;
    }

    /** A deterministic order for member values that carries no submission order. */
    private static String sortKey(Object value) {
        if (value instanceof BigDecimal d) {
            return "n" + String.format("%040.10f", d.add(new BigDecimal("1000000000")));
        }
        return (value instanceof Boolean ? "b" : "s") + value;
    }

    private static java.util.Set<String> belowMinimum(ScoringRunView run) {
        java.util.Set<String> questions = new TreeSet<>();
        if (run.responseSummariesJson() == null) {
            return questions;
        }
        try {
            JsonNode summaries = JSON.readTree(run.responseSummariesJson()).path("questions");
            summaries.forEach(s -> {
                if (s.path("belowMinimum").asBoolean(false)) {
                    questions.add(s.path("questionId").asText());
                }
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("stored response summaries are not valid JSON", e);
        }
        return questions;
    }

    /** Records that a caller opened the explanation of a run (specv3.md Section 33.4): who and when, never the content. */
    public void auditExplanationAccess(TenantId tenant, String actor, UUID runId, boolean redacted) {
        accessAudit.record(tenant.value(), actor, "EXPLANATION_READ", runId, "{\"redacted\":" + redacted + "}", clock.instant());
    }

    private Optional<ScoringRunView> repeatedRequest(String tenant, String idempotencyKey, String requestHash) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return runs.findIdempotencyBinding(tenant, idempotencyKey).map(binding -> {
            if (!requestHash.equals(binding.requestHash())) {
                throw conflict();
            }
            return view(binding.run());
        });
    }

    /** A key used on a request that reused an existing run is bound to that run, so later misuse is detected. */
    private void bindReusedRun(String tenant, String idempotencyKey, String requestHash, StoredRunRow run) {
        if (idempotencyKey == null) {
            return;
        }
        try {
            recorder.bindKey(tenant, idempotencyKey, requestHash, run.id());
        } catch (DuplicateKeyException e) {
            repeatedRequest(tenant, idempotencyKey, requestHash);
        }
    }

    /** A concurrent identical request won the race: answer with its run. Anything else is a real persistence failure. */
    private ScoringRunView afterDuplicate(String tenant, String idempotencyKey, String requestHash,
                                          Supplier<Optional<StoredRunRow>> identity, DuplicateKeyException cause) {
        Optional<ScoringRunView> byKey = repeatedRequest(tenant, idempotencyKey, requestHash);
        if (byKey.isPresent()) {
            return byKey.get();
        }
        return identity.get().map(this::view).orElseThrow(() -> new ScoringException(ScoringErrorCode.PERSISTENCE_FAILURE,
                "the scoring run could not be stored"));
    }

    private Loaded load(TenantId tenant, AssessmentId assessmentId) {
        AssessmentRow assessment = assessments.findAssessment(tenant.value(), assessmentId.value())
                .orElseThrow(() -> assessmentNotFound(assessmentId));
        requireCompleted(assessment.status(), assessment.completedAt());
        SnapshotRow snapshot = assessments.latestSnapshot(tenant.value(), assessmentId.value()).orElseThrow(() ->
                new ScoringException(ScoringErrorCode.ASSESSMENT_SNAPSHOT_NOT_FOUND, "assessment has no snapshot",
                        Map.of("assessmentId", assessmentId.value())));
        List<ScopedResponse> responses = assessments.answers(tenant.value(), snapshot.id()).stream().map(a ->
                new ScopedResponse(a.questionId(), AnswerJson.read(a.answerJson()), ResponseScope.valueOf(a.scopeLevel()),
                        a.teamId().isEmpty() ? null : a.teamId(), a.declaredRespondents())).toList();
        return new Loaded(assessment, new AssessmentSnapshotId(snapshot.id()), new OrganizationId(assessment.organizationId()), responses);
    }

    private record Loaded(AssessmentRow assessment, AssessmentSnapshotId snapshotId, OrganizationId organization,
                          List<ScopedResponse> responses) {
    }

    /** True when every answer is an organization answer: the form that existed before response scopes. */
    private static boolean isEarlierForm(List<ScopedResponse> responses) {
        return responses.stream().allMatch(r -> r.scope() == ResponseScope.ORGANIZATION);
    }

    private static String organizationHash(AssessmentSnapshotId snapshot, List<ScopedResponse> responses) {
        return isEarlierForm(responses)
                ? InputHasher.inputHash(snapshot, responses.stream().map(ScopedResponse::asInput).toList())
                : InputHasher.scopedInputHash(snapshot, responses, null);
    }

    /** The teams that have a member or team answer, in id order. */
    private static List<String> teamIds(List<ScopedResponse> responses) {
        TreeSet<String> teams = new TreeSet<>();
        responses.stream().filter(r -> r.scope() != ResponseScope.ORGANIZATION && r.teamId() != null)
                .forEach(r -> teams.add(r.teamId()));
        return List.copyOf(teams);
    }

    /** Answer rows to store. Member answers only get an ordinal to tell them apart; they have no identity. */
    private static List<AnswerRow> answerRows(List<ScopedResponse> responses) {
        Map<String, Integer> next = new HashMap<>();
        List<AnswerRow> rows = new ArrayList<>();
        for (ScopedResponse r : responses) {
            String team = r.teamId() == null ? "" : r.teamId();
            int ordinal = r.scope() == ResponseScope.MEMBER
                    ? next.merge(r.questionId() + "|" + team, 1, Integer::sum) - 1
                    : 0;
            rows.add(new AnswerRow(r.questionId(), AnswerJson.write(r.questionId(), r.answer()), r.scope().name(), team, ordinal,
                    r.declaredRespondents()));
        }
        return rows;
    }

    private static void requireCompleted(String status, Instant completedAt) {
        if (!"COMPLETED".equals(status) || completedAt == null) {
            throw new ScoringException(ScoringErrorCode.ASSESSMENT_NOT_COMPLETED, "the assessment is not completed");
        }
    }

    private static ScoringException assessmentNotFound(AssessmentId id) {
        return new ScoringException(ScoringErrorCode.ASSESSMENT_NOT_FOUND, "assessment not found", Map.of("assessmentId", id.value()));
    }

    private static ScoringException conflict() {
        return new ScoringException(ScoringErrorCode.IDEMPOTENCY_CONFLICT,
                "the idempotency key was already used with a different request");
    }

    private static String requestHash(CalculateCommand c) {
        List<ScopedResponse> responses = c.scopedResponses();
        String answers = isEarlierForm(responses) ? InputHasher.answersHash(c.inputs()) : InputHasher.scopedAnswersHash(responses);
        return InputHasher.sha256("calculate|" + c.organizationId().value() + "|" + c.templateId() + "|" + c.templateVersion() + "|"
                + c.status() + "|" + c.completedAt() + "|" + answers
                + (c.explicitConfigurationVersionId() == null ? "" : "|" + c.explicitConfigurationVersionId()));
    }

    private ScoringRunView view(StoredRunRow r) {
        return new ScoringRunView(r.id(), r.assessmentId(), r.snapshotId(), r.organizationId(), r.source(), r.configurationSelection(),
                r.engineVersion(), r.algorithmVersion(), r.configurationVersion(), r.configurationHash(), r.inputHash(),
                r.roundingPolicyVersion(), RunStatus.valueOf(r.status()), r.scoreStatus() == null ? null : ScoreStatus.valueOf(r.scoreStatus()),
                r.finalScore(), r.weightedAdjustedScore(), r.overallPenalty(), r.interpretation(), r.layersJson(), r.traceJson(),
                r.errorCode(), r.startedAt(), r.completedAt(), r.scopeType(), r.scopeId(), r.parentRunId(), r.responseSummariesJson());
    }
}
