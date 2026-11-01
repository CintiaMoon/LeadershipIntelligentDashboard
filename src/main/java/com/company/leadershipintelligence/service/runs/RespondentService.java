package com.company.leadershipintelligence.service.runs;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.persistence.entity.QuestionnaireRows.QuestionnaireRow;
import com.company.leadershipintelligence.repository.persistence.entity.QuestionnaireRows.VersionRow;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.MyParticipationRow;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.PersonRow;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.RunRow;
import com.company.leadershipintelligence.repository.persistence.repository.QuestionnaireStore;
import com.company.leadershipintelligence.repository.persistence.repository.RosterStore;
import com.company.leadershipintelligence.repository.persistence.repository.RunStore;
import com.company.leadershipintelligence.service.query.RunViews.OptionView;
import com.company.leadershipintelligence.service.query.RunViews.RespondentQuestion;
import com.company.leadershipintelligence.service.query.RunViews.RespondentRun;
import com.company.leadershipintelligence.service.query.RunViews.RespondentRunDetail;
import com.company.leadershipintelligence.service.questionnaire.QuestionKind;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.QuestionSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The respondent flow (decision D48; specv3.md Section 4.14). The respondent is the roster entry whose e-mail equals the verified
 * e-mail of the token within the token's tenant; nothing in a request names a person. A respondent sees only their own runs and
 * never any scoring detail of a question. An unfinished answer set (a draft) is linked to the participant so it can be resumed;
 * on submit the answers are written with the team and the question only, in one transaction that also deletes the draft and marks the
 * participant finished. A required question must be answered before submit. Answers are checked when they are given, so a stored
 * answer can always be scored. Answers and addresses are never logged.
 */
@Service
public class RespondentService {

    private final RosterStore roster;
    private final RunStore runs;
    private final QuestionnaireStore questionnaires;
    private final ObjectMapper json;
    private final Clock clock;

    public RespondentService(RosterStore roster, RunStore runs, QuestionnaireStore questionnaires, ObjectMapper json, Clock clock) {
        this.roster = roster;
        this.runs = runs;
        this.questionnaires = questionnaires;
        this.json = json;
        this.clock = clock;
    }

    /** The runs the respondent takes part in, newest first; empty when the e-mail is not on the roster. */
    @Transactional(readOnly = true)
    public List<RespondentRun> list(TenantId tenant, String email) {
        Optional<PersonRow> person = roster.personByEmail(tenant.value(), email);
        if (person.isEmpty()) {
            return List.of();
        }
        List<RespondentRun> result = new ArrayList<>();
        for (MyParticipationRow mine : runs.participationsOf(tenant.value(), person.get().id())) {
            RunRow run = runs.run(tenant.value(), mine.runId()).orElseThrow();
            result.add(new RespondentRun(run.id(), run.name(), questionnaireName(tenant, run), run.dueDate(), run.status(), mine.status()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public RespondentRunDetail get(TenantId tenant, String email, UUID runId) {
        Mine mine = mine(tenant, email, runId, false);
        List<QuestionSpec> questions = questions(tenant, mine.run());
        Map<String, Object> answers = new LinkedHashMap<>();
        runs.draft(mine.participation().participantId()).forEach((question, answer) -> answers.put(question, read(answer)));
        return new RespondentRunDetail(mine.run().id(), mine.run().name(), questionnaireName(tenant, mine.run()), mine.run().dueDate(),
                mine.run().status(), mine.participation().status(), questions.stream().map(RespondentService::view).toList(), answers);
    }

    /**
     * Saves the given answers into the respondent's draft; an empty answer removes the answer from the draft. The questions not named
     * are left as they are.
     */
    @Transactional
    public RespondentRunDetail saveDraft(TenantId tenant, String email, UUID runId, Map<String, JsonNode> answers) {
        Mine mine = mine(tenant, email, runId, true);
        requireOpen(mine);
        Map<String, QuestionSpec> byId = byId(questions(tenant, mine.run()));
        // check everything before writing anything
        Map<String, JsonNode> checked = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : answers.entrySet()) {
            QuestionSpec question = byId.get(entry.getKey());
            if (question == null) {
                throw new ScoringException(ScoringErrorCode.RUN_INVALID, "the questionnaire has no question " + entry.getKey(),
                        Map.of("questionId", entry.getKey()));
            }
            checked.put(entry.getKey(), AnswerChecker.check(question, entry.getValue()));
        }
        UUID participant = mine.participation().participantId();
        checked.forEach((question, answer) -> {
            if (answer == null) {
                runs.deleteDraftAnswer(participant, question);
            } else {
                runs.saveDraftAnswer(participant, question, answer.toString(), clock.instant());
            }
        });
        if ("NOT_STARTED".equals(mine.participation().status())) {
            runs.setStatus(participant, "IN_PROGRESS");
        }
        return get(tenant, email, runId);
    }

    /**
     * Finishes the participation: the draft becomes answers of the team (no person, no time, no order), the draft is deleted and the
     * participant is marked finished, atomically. Every required question must have an answer.
     */
    @Transactional
    public RespondentRunDetail submit(TenantId tenant, String email, UUID runId) {
        Mine mine = mine(tenant, email, runId, true);
        requireOpen(mine);
        UUID participant = mine.participation().participantId();
        List<QuestionSpec> questions = questions(tenant, mine.run());
        Map<String, String> draft = runs.draft(participant);
        List<String> missing = new ArrayList<>();
        for (QuestionSpec q : questions) {
            if (q.required() && !draft.containsKey(q.id())) {
                missing.add(q.id());
            }
        }
        if (!missing.isEmpty()) {
            Map<String, String> details = new LinkedHashMap<>();
            for (int i = 0; i < missing.size(); i++) {
                details.put(String.format("missing%02d", i + 1), missing.get(i));
            }
            throw new ScoringException(ScoringErrorCode.RUN_INVALID, "required questions are not answered: " + String.join(", ", missing), details);
        }
        for (QuestionSpec q : questions) {
            String answer = draft.get(q.id());
            if (answer != null) {
                runs.insertResponse(tenant.value(), mine.run().id(), mine.participation().teamId(), q.id(), answer);
            }
        }
        runs.deleteDraft(participant);
        runs.setStatus(participant, "SUBMITTED");
        return get(tenant, email, runId);
    }

    // ---------------- internals ----------------

    private record Mine(RunRow run, MyParticipationRow participation) {
    }

    /** The respondent's own participation in the run, or "not found": another person's run looks like a missing one. */
    private Mine mine(TenantId tenant, String email, UUID runId, boolean lock) {
        PersonRow person = roster.personByEmail(tenant.value(), email).orElseThrow(RespondentService::notFound);
        Optional<RunRow> run = lock ? runs.shareRun(tenant.value(), runId) : runs.run(tenant.value(), runId);
        Optional<MyParticipationRow> participation = lock ? runs.lockParticipation(tenant.value(), person.id(), runId)
                : runs.participation(tenant.value(), person.id(), runId);
        if (run.isEmpty() || participation.isEmpty()) {
            throw notFound();
        }
        return new Mine(run.get(), participation.get());
    }

    private static void requireOpen(Mine mine) {
        if (!"OPEN".equals(mine.run().status())) {
            throw new ScoringException(ScoringErrorCode.RUN_STATE, "the run is closed", Map.of());
        }
        if ("SUBMITTED".equals(mine.participation().status())) {
            throw new ScoringException(ScoringErrorCode.RUN_STATE, "you have already submitted your answers", Map.of());
        }
    }

    private static ScoringException notFound() {
        return new ScoringException(ScoringErrorCode.RUN_NOT_FOUND, "run not found", Map.of());
    }

    private String questionnaireName(TenantId tenant, RunRow run) {
        return questionnaires.find(tenant.value(), run.questionnaireId()).map(QuestionnaireRow::name).orElse(run.questionnaireId());
    }

    private List<QuestionSpec> questions(TenantId tenant, RunRow run) {
        VersionRow version = questionnaires.version(tenant.value(), run.questionnaireId(), run.questionnaireVersion())
                .orElseThrow(() -> new IllegalStateException("the questionnaire version of a run is missing"));
        try {
            return json.readValue(version.definition(), QuestionnaireDocument.class).questions();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a stored questionnaire cannot be read", e);
        }
    }

    private static Map<String, QuestionSpec> byId(List<QuestionSpec> questions) {
        Map<String, QuestionSpec> byId = new LinkedHashMap<>();
        questions.forEach(q -> byId.put(q.id(), q));
        return byId;
    }

    private Object read(String answer) {
        try {
            return json.readValue(answer, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a stored draft cannot be read", e);
        }
    }

    /** Only what a respondent needs: no dimension, weight, direction, desirable answer or option score. */
    private static RespondentQuestion view(QuestionSpec q) {
        BigDecimal min = null;
        BigDecimal max = null;
        switch (q.kind()) {
            case RATING -> {
                min = BigDecimal.ONE;
                max = BigDecimal.valueOf(5);
            }
            case LIKERT -> {
                min = BigDecimal.ONE;
                max = BigDecimal.valueOf(q.points() == null ? 5 : q.points());
            }
            case NPS -> {
                min = BigDecimal.ZERO;
                max = BigDecimal.TEN;
            }
            case NUMERIC -> {
                min = q.min();
                max = q.max();
            }
            default -> {
            }
        }
        List<OptionView> options = q.kind() == QuestionKind.SINGLE_CHOICE || q.kind() == QuestionKind.MULTI_CHOICE
                ? q.options().stream().map(o -> new OptionView(o.key(), o.label())).toList() : List.of();
        return new RespondentQuestion(q.id(), q.kind().name(), q.title(), q.text(), q.help(), q.required(), options, q.labels(), min, max);
    }
}
