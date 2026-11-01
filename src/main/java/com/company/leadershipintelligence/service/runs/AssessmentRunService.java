package com.company.leadershipintelligence.service.runs;

import com.company.leadershipintelligence.domain.enums.ConfigurationScope;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.ScopedResponse;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.persistence.entity.QuestionnaireRows.QuestionnaireRow;
import com.company.leadershipintelligence.repository.persistence.entity.QuestionnaireRows.VersionRow;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.ParticipantRow;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.PersonRow;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.ResponseRow;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.RunRow;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.TeamRow;
import com.company.leadershipintelligence.repository.persistence.repository.AccessAuditStore;
import com.company.leadershipintelligence.repository.persistence.repository.QuestionnaireStore;
import com.company.leadershipintelligence.repository.persistence.repository.RosterStore;
import com.company.leadershipintelligence.repository.persistence.repository.RunStore;
import com.company.leadershipintelligence.service.EffectiveConfigurationService;
import com.company.leadershipintelligence.service.ScoreAssessmentService;
import com.company.leadershipintelligence.service.command.CalculateCommand;
import com.company.leadershipintelligence.service.command.OrganizationScope;
import com.company.leadershipintelligence.service.query.LayerRef;
import com.company.leadershipintelligence.service.query.RunViews.ParticipantView;
import com.company.leadershipintelligence.service.query.RunViews.AssessmentRunDetail;
import com.company.leadershipintelligence.service.query.RunViews.AssessmentRunSummary;
import com.company.leadershipintelligence.service.query.RunViews.TeamProgress;
import com.company.leadershipintelligence.service.query.ScoringRunView;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assessment runs for administrators (decision D48; specv3.md Section 4.14). Starting a run pins the latest published version of a
 * questionnaire and its configuration version for an organization and some of its teams, and snapshots one participant per active
 * person of those teams. Closing a run scores the stored answers once, as member answers with the pinned configuration, so
 * republishing the questionnaire cannot change an open run. Nothing here reads an answer together with a person: the participant
 * list says who has finished and the responses carry only a team. Reading the participant list and starting and closing a run are
 * audited. Names, addresses and answers are never logged.
 */
@Service
public class AssessmentRunService {

    /** What the completion list can reveal in a small team; the interface shows it beside the list (specv3.md Section 4.14). */
    public static final String COMPLETION_NOTICE = "This list shows who has not finished. In a very small team that can let you work out "
            + "who is missing, which the minimum number of responses per team does not prevent. Answers are never linked to a person.";

    private final RunStore runs;
    private final RosterStore roster;
    private final QuestionnaireStore questionnaires;
    private final ScoreAssessmentService scoring;
    private final EffectiveConfigurationService effective;
    private final AccessAuditStore audit;
    private final ObjectMapper json;
    private final Clock clock;

    public AssessmentRunService(RunStore runs, RosterStore roster, QuestionnaireStore questionnaires, ScoreAssessmentService scoring,
                                EffectiveConfigurationService effective, AccessAuditStore audit, ObjectMapper json, Clock clock) {
        this.runs = runs;
        this.roster = roster;
        this.questionnaires = questionnaires;
        this.scoring = scoring;
        this.effective = effective;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AssessmentRunSummary> list(TenantId tenant, OrganizationScope scope, String organizationId, String status) {
        if (organizationId != null) {
            scope.require(organizationId);
        }
        if (status != null && !status.equals("OPEN") && !status.equals("CLOSED")) {
            throw new ScoringException(ScoringErrorCode.RUN_INVALID, "status must be OPEN or CLOSED");
        }
        Map<String, String> names = new HashMap<>();
        return runs.runs(tenant.value(), organizationId == null ? scope.organizationIds() : List.of(organizationId), status).stream()
                .map(r -> summary(tenant, r, names)).toList();
    }

    @Transactional
    public AssessmentRunDetail get(TenantId tenant, OrganizationScope scope, String actor, UUID id) {
        RunRow run = require(runs.run(tenant.value(), id), scope);
        audit.record(tenant.value(), actor, "RUN_PARTICIPANTS_READ", null, "{\"assessmentRunId\":\"" + id + "\"}", clock.instant());
        return detail(tenant, run);
    }

    @Transactional
    public AssessmentRunDetail start(TenantId tenant, OrganizationScope scope, String actor, String name, String organizationId, String questionnaireId,
                           List<String> teamIds, LocalDate dueDate) {
        scope.require(organizationId);
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty() || cleanName.length() > 200) {
            throw invalid("a run needs a name of 1 to 200 characters");
        }
        Set<String> teams = new LinkedHashSet<>(teamIds == null ? List.of() : teamIds);
        if (teams.isEmpty() || teams.size() > 100) {
            throw invalid("a run needs between 1 and 100 teams");
        }
        if (dueDate != null && dueDate.isBefore(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))) {
            throw invalid("the due date is in the past");
        }
        QuestionnaireRow questionnaire = questionnaires.find(tenant.value(), questionnaireId == null ? "" : questionnaireId)
                .orElseThrow(() -> invalid("the questionnaire does not exist"));
        if (questionnaire.archivedAt() != null) {
            throw invalid("the questionnaire is archived");
        }
        VersionRow version = questionnaires.latestPublished(tenant.value(), questionnaire.id())
                .orElseThrow(() -> invalid("the questionnaire has no published version"));
        for (String teamId : teams) {
            TeamRow team = roster.team(tenant.value(), teamId).orElseThrow(() -> invalid("a team does not exist"));
            if (!team.organizationId().equals(organizationId)) {
                throw invalid("a team belongs to another organization");
            }
        }
        List<PersonRow> people = roster.activePeopleOf(tenant.value(), teams);
        if (people.isEmpty()) {
            throw invalid("the teams have no active people to ask");
        }
        if (!roster.ensureOrganization(tenant.value(), organizationId)) {
            throw new ScoringException(ScoringErrorCode.ORGANIZATION_ACCESS_DENIED, "the caller cannot access this organization");
        }
        UUID id = UUID.randomUUID();
        runs.insertRun(tenant.value(), id, organizationId, cleanName, questionnaire.id(), version.version(), version.configurationVersionId(), dueDate,
                actor, clock.instant());
        teams.forEach(t -> runs.insertRunTeam(id, t));
        people.forEach(p -> runs.insertParticipant(tenant.value(), UUID.randomUUID(), id, p.id(), p.teamId()));
        audit.record(tenant.value(), actor, "RUN_STARTED", null, "{\"assessmentRunId\":\"" + id + "\",\"participants\":" + people.size() + "}", clock.instant());
        return detail(tenant, runs.run(tenant.value(), id).orElseThrow());
    }

    /**
     * Scores the stored answers once, as member answers with the configuration version the run pinned. Free text is never scored.
     * When the questionnaire's layer is still the pinned one the ordinary resolution is used, otherwise the pinned version is
     * selected explicitly. A failure rolls everything back and leaves the run open.
     */
    @Transactional
    public AssessmentRunDetail close(TenantId tenant, OrganizationScope scope, String actor, UUID id) {
        RunRow run = require(runs.lockRun(tenant.value(), id), scope);
        if (!"OPEN".equals(run.status())) {
            throw new ScoringException(ScoringErrorCode.RUN_STATE, "the run is already closed", Map.of());
        }
        QuestionnaireDocument document = document(tenant, run);
        Set<String> scored = new HashSet<>();
        document.questions().stream().filter(AnswerChecker::scored).forEach(q -> scored.add(q.id()));
        List<ScopedResponse> responses = new ArrayList<>();
        for (ResponseRow row : runs.responses(tenant.value(), id)) {
            if (scored.contains(row.questionId())) {
                responses.add(ScopedResponse.member(row.questionId(), parse(row.answerJson()), row.teamId()));
            }
        }
        if (responses.isEmpty()) {
            throw new ScoringException(ScoringErrorCode.RUN_STATE, "nobody has submitted an answer yet, so there is nothing to score", Map.of());
        }
        UUID explicit = pinnedLayerDiffers(tenant, run) ? run.configurationVersionId() : null;
        Instant now = clock.instant();
        ScoringRunView result = scoring.calculate(new CalculateCommand(tenant, new OrganizationId(run.organizationId()), run.questionnaireId(),
                run.questionnaireVersion(), "COMPLETED", now, List.of(), "run-close-" + id, Map.of(), responses, explicit));
        runs.close(tenant.value(), id, actor, now, result.assessmentId(), result.id());
        runs.deleteDraftsOfRun(id);
        audit.record(tenant.value(), actor, "RUN_CLOSED", result.id(), "{\"assessmentRunId\":\"" + id + "\"}", now);
        return detail(tenant, runs.run(tenant.value(), id).orElseThrow());
    }

    // ---------------- internals ----------------

    private boolean pinnedLayerDiffers(TenantId tenant, RunRow run) {
        for (LayerRef layer : effective.resolve(tenant, new OrganizationId(run.organizationId()), run.questionnaireId()).layers()) {
            if (layer.scope() == ConfigurationScope.TEMPLATE && run.questionnaireId().equals(layer.scopeId())) {
                return !layer.versionId().equals(run.configurationVersionId());
            }
        }
        return true;
    }

    private AssessmentRunDetail detail(TenantId tenant, RunRow run) {
        Map<String, String> names = new HashMap<>();
        AssessmentRunSummary summary = summary(tenant, run, names);
        Map<String, Map<String, Integer>> counts = runs.counts(tenant.value(), run.id());
        List<TeamProgress> teams = new ArrayList<>();
        for (String teamId : runs.teamIdsOf(run.id())) {
            Map<String, Integer> c = counts.getOrDefault(teamId, Map.of());
            int notStarted = c.getOrDefault("NOT_STARTED", 0);
            int inProgress = c.getOrDefault("IN_PROGRESS", 0);
            int submitted = c.getOrDefault("SUBMITTED", 0);
            teams.add(new TeamProgress(teamId, roster.team(tenant.value(), teamId).map(TeamRow::name).orElse(teamId),
                    notStarted + inProgress + submitted, notStarted, inProgress, submitted));
        }
        List<ParticipantView> participants = runs.participants(tenant.value(), run.id()).stream()
                .map((ParticipantRow p) -> new ParticipantView(p.teamId(), p.personName(), p.email(), p.status())).toList();
        return new AssessmentRunDetail(summary, teams, participants, COMPLETION_NOTICE);
    }

    private AssessmentRunSummary summary(TenantId tenant, RunRow run, Map<String, String> names) {
        String name = names.computeIfAbsent(run.questionnaireId(),
                q -> questionnaires.find(tenant.value(), q).map(QuestionnaireRow::name).orElse(q));
        int total = 0;
        int submitted = 0;
        for (Map<String, Integer> c : runs.counts(tenant.value(), run.id()).values()) {
            for (Map.Entry<String, Integer> e : c.entrySet()) {
                total += e.getValue();
                if ("SUBMITTED".equals(e.getKey())) {
                    submitted += e.getValue();
                }
            }
        }
        return new AssessmentRunSummary(run.id(), run.name(), run.organizationId(), run.questionnaireId(), name, run.questionnaireVersion(), run.status(),
                run.dueDate(), run.createdAt(), run.closedAt(), total, submitted, run.assessmentId(), run.scoringRunId());
    }

    private QuestionnaireDocument document(TenantId tenant, RunRow run) {
        VersionRow version = questionnaires.version(tenant.value(), run.questionnaireId(), run.questionnaireVersion())
                .orElseThrow(() -> new IllegalStateException("the questionnaire version of a run is missing"));
        try {
            return json.readValue(version.definition(), QuestionnaireDocument.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a stored questionnaire cannot be read", e);
        }
    }

    private Object parse(String answerJson) {
        try {
            return json.readValue(answerJson, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a stored answer cannot be read", e);
        }
    }

    private static RunRow require(java.util.Optional<RunRow> run, OrganizationScope scope) {
        RunRow found = run.orElseThrow(() -> new ScoringException(ScoringErrorCode.RUN_NOT_FOUND, "run not found", Map.of()));
        // a run of an organization the caller cannot reach looks like a missing one
        if (!scope.everything() && !scope.organizationIds().contains(found.organizationId())) {
            throw new ScoringException(ScoringErrorCode.RUN_NOT_FOUND, "run not found", Map.of());
        }
        return found;
    }

    private static ScoringException invalid(String message) {
        return new ScoringException(ScoringErrorCode.RUN_INVALID, message, Map.of());
    }
}
