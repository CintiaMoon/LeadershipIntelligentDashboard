package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.mapper.RunResponseMapper;
import com.company.leadershipintelligence.api.request.CalculateRequest;
import com.company.leadershipintelligence.api.request.RecalculateRequest;
import com.company.leadershipintelligence.api.response.ExplanationResponse;
import com.company.leadershipintelligence.api.response.RunAnswersResponse;
import com.company.leadershipintelligence.api.response.RunDimensionsResponse;
import com.company.leadershipintelligence.api.response.RunImbalancesResponse;
import com.company.leadershipintelligence.api.response.RunPatternsResponse;
import com.company.leadershipintelligence.api.response.RunPenaltiesResponse;
import com.company.leadershipintelligence.api.response.ScoringResultResponse;
import com.company.leadershipintelligence.api.security.CurrentUser;
import com.company.leadershipintelligence.api.security.Role;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.ResponseScope;
import com.company.leadershipintelligence.domain.model.ScopedResponse;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.valueobject.AssessmentId;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.service.ScoreAssessmentService;
import com.company.leadershipintelligence.service.command.CalculateCommand;
import com.company.leadershipintelligence.service.query.ScoringRunView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Scoring endpoints (specv3.md Section 28, decisions D10, D14, D19-D22). Controllers hold no scoring logic: they
 * resolve the caller from the token, check the organization, call the application service and shape the response.
 * Tenant and organization come only from the token; a request body can carry neither. Request bodies are never logged.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Scoring")
public class ScoringController {

    private static final Pattern KEY = Pattern.compile("[\\x21-\\x7E]{1,200}");
    private static final int MAX_TEXT_ANSWER = 10_000;
    private static final int MAX_SELECTION = 100;
    private static final int MAX_OPTION_LENGTH = 200;

    private final ScoreAssessmentService scoring;
    private final RunResponseMapper mapper;

    public ScoringController(ScoreAssessmentService scoring, RunResponseMapper mapper) {
        this.scoring = scoring;
        this.mapper = mapper;
    }

    @Operation(summary = "Submit an assessment and its answers, score it and store the run")
    @PostMapping("/calculate")
    public ScoringResultResponse calculate(@AuthenticationPrincipal Jwt jwt,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                           @Valid @RequestBody CalculateRequest request) {
        CurrentUser user = CurrentUser.from(jwt);
        user.scope().require(request.assessment().organizationId());
        List<ScoringInput> inputs = new ArrayList<>();
        List<ScopedResponse> responses = new ArrayList<>();
        Map<String, QuestionType> declared = new LinkedHashMap<>();
        if (request.inputs() != null) {
            for (CalculateRequest.InputPart part : request.inputs()) {
                checkAnswerSize(part.questionId(), part.answer());
                inputs.add(new ScoringInput(part.questionId(), part.answer()));
                declare(declared, part.questionId(), part.questionType());
            }
        }
        if (request.responses() != null) {
            for (CalculateRequest.ResponsePart part : request.responses()) {
                checkAnswerSize(part.questionId(), part.answer());
                responses.add(new ScopedResponse(part.questionId(), part.answer(), scope(part.scope()), blankToNull(part.teamId()),
                        part.respondents()));
                declare(declared, part.questionId(), part.questionType());
            }
        }
        CalculateRequest.AssessmentPart a = request.assessment();
        ScoringRunView run = scoring.calculate(new CalculateCommand(user.tenantId(), new OrganizationId(a.organizationId()), a.templateId(),
                templateVersion(a.templateVersion()), a.status(), a.completedAt(), inputs, key(idempotencyKey), declared, responses));
        return result(user, run);
    }

    @Operation(summary = "Score a stored assessment (reuses an identical earlier run)")
    @PostMapping("/assessments/{assessmentId}/score")
    public ScoringResultResponse score(@AuthenticationPrincipal Jwt jwt, @PathVariable String assessmentId,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        CurrentUser user = CurrentUser.from(jwt);
        ScoringRunView run = scoring.score(user.tenantId(), new AssessmentId(assessmentId), key(idempotencyKey), user.scope());
        return result(user, run);
    }

    @Operation(summary = "The latest completed result of an assessment")
    @GetMapping("/assessments/{assessmentId}/score")
    public ScoringResultResponse latest(@AuthenticationPrincipal Jwt jwt, @PathVariable String assessmentId) {
        CurrentUser user = CurrentUser.from(jwt);
        ScoringRunView run = scoring.latest(user.tenantId(), new AssessmentId(assessmentId), user.scope()).orElseThrow(() ->
                new ScoringException(ScoringErrorCode.SCORING_RUN_NOT_FOUND, "the assessment has no scoring run yet",
                        Map.of("assessmentId", assessmentId)));
        return result(user, run);
    }

    @Operation(summary = "Create a new run for an assessment, optionally with a chosen configuration version")
    @PostMapping("/assessments/{assessmentId}/score/recalculate")
    public ScoringResultResponse recalculate(@AuthenticationPrincipal Jwt jwt, @PathVariable String assessmentId,
                                             @Valid @RequestBody(required = false) RecalculateRequest request) {
        CurrentUser user = CurrentUser.from(jwt);
        UUID version = null;
        if (request != null && request.configurationVersion() != null) {
            if (!user.hasAnyRole(Role.OWNER, Role.ADMIN)) {
                throw new AccessDeniedException("only an owner or admin may choose a configuration version");
            }
            version = uuid(request.configurationVersion());
        }
        ScoringRunView run = scoring.recalculate(user.tenantId(), new AssessmentId(assessmentId), version, user.scope());
        return result(user, run);
    }

    @Operation(summary = "A stored scoring run")
    @GetMapping("/scoring-runs/{scoringRunId}")
    public ScoringResultResponse run(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID scoringRunId) {
        CurrentUser user = CurrentUser.from(jwt);
        return result(user, read(user, scoringRunId));
    }

    @Operation(summary = "The stored explanation trace of a run (never recalculated)")
    @GetMapping("/scoring-runs/{scoringRunId}/explanation")
    public ExplanationResponse explanation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID scoringRunId) {
        CurrentUser user = CurrentUser.from(jwt);
        ScoringRunView run = read(user, scoringRunId);
        ExplanationResponse response = mapper.explanation(run, redact(user));
        scoring.auditExplanationAccess(user.tenantId(), user.actor(), scoringRunId, redact(user));
        return response;
    }

    @Operation(summary = "Raw and adjusted dimension scores, coverage and contributions of a run")
    @GetMapping("/scoring-runs/{scoringRunId}/dimensions")
    public RunDimensionsResponse dimensions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID scoringRunId) {
        CurrentUser user = CurrentUser.from(jwt);
        return mapper.dimensions(read(user, scoringRunId), redact(user));
    }

    @Operation(summary = "Pattern evaluations of a run")
    @GetMapping("/scoring-runs/{scoringRunId}/patterns")
    public RunPatternsResponse patterns(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID scoringRunId) {
        CurrentUser user = CurrentUser.from(jwt);
        return mapper.patterns(read(user, scoringRunId), redact(user));
    }

    @Operation(summary = "Penalty candidates, applied and suppressed deductions of a run")
    @GetMapping("/scoring-runs/{scoringRunId}/penalties")
    public RunPenaltiesResponse penalties(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID scoringRunId) {
        CurrentUser user = CurrentUser.from(jwt);
        return mapper.penalties(read(user, scoringRunId), redact(user));
    }

    @Operation(summary = "The answers a run was scored with (owners, admins and analysts only; the read is audited)")
    @GetMapping("/scoring-runs/{scoringRunId}/answers")
    public RunAnswersResponse answers(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID scoringRunId) {
        CurrentUser user = CurrentUser.from(jwt);
        return new RunAnswersResponse(scoringRunId, scoring.answersOf(user.tenantId(), user.actor(), scoringRunId, user.scope()));
    }

    @Operation(summary = "Imbalance findings of a run")
    @GetMapping("/scoring-runs/{scoringRunId}/imbalances")
    public RunImbalancesResponse imbalances(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID scoringRunId) {
        CurrentUser user = CurrentUser.from(jwt);
        return mapper.imbalances(read(user, scoringRunId));
    }

    // ---------------- helpers ----------------

    private ScoringRunView read(CurrentUser user, UUID runId) {
        return scoring.get(user.tenantId(), runId, user.scope());
    }

    /** The run as a response, with the team results of an organization run scored from scoped answers (decision D45). */
    private ScoringResultResponse result(CurrentUser user, ScoringRunView run) {
        return mapper.result(run, user.tenantId().value(), redact(user), scoring.teamRuns(user.tenantId(), run));
    }

    private static void declare(Map<String, QuestionType> declared, String questionId, String type) {
        if (type != null) {
            declared.put(questionId, questionType(type));
        }
    }

    private static ResponseScope scope(String value) {
        try {
            return ResponseScope.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw invalid("scope must be one of MEMBER, TEAM, ORGANIZATION");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Only OWNER, ADMIN and ANALYST may see per-question evidence (decision D19). */
    private static boolean redact(CurrentUser user) {
        return !user.hasAnyRole(Role.OWNER, Role.ADMIN, Role.ANALYST);
    }

    private static String key(String value) {
        if (value == null) {
            return null;
        }
        if (!KEY.matcher(value).matches()) {
            throw invalid("Idempotency-Key must be 1 to 200 printable characters");
        }
        return value;
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw invalid("configurationVersion must be a version id");
        }
    }

    private static int templateVersion(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw invalid("assessment.templateVersion must be a whole number");
        }
    }

    private static QuestionType questionType(String value) {
        try {
            return QuestionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw invalid("questionType must be one of SCALE, MULTIPLE_CHOICE, MULTI_SELECT, BOOLEAN, NUMBER, TEXT");
        }
    }

    private static void checkAnswerSize(String questionId, Object answer) {
        if (answer instanceof String text && text.length() > MAX_TEXT_ANSWER) {
            throw invalid("the answer for question " + questionId + " is longer than " + MAX_TEXT_ANSWER + " characters");
        }
        if (answer instanceof java.util.Collection<?> selection) {
            if (selection.size() > MAX_SELECTION) {
                throw invalid("the answer for question " + questionId + " has more than " + MAX_SELECTION + " options");
            }
            for (Object element : selection) {
                if (element instanceof String option && option.length() > MAX_OPTION_LENGTH) {
                    throw invalid("an option of question " + questionId + " is longer than " + MAX_OPTION_LENGTH + " characters");
                }
            }
        }
    }

    private static ApiRequestException invalid(String message) {
        return new ApiRequestException(message);
    }
}
