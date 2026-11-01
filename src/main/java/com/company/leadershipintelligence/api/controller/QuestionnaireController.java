package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.request.CreateQuestionnaireRequest;
import com.company.leadershipintelligence.api.security.CurrentUser;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.query.QuestionnaireViews.Detail;
import com.company.leadershipintelligence.service.query.QuestionnaireViews.Summary;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The questionnaire library and builder (decisions D47, D50). Owners and administrators only; the tenant comes from the token and
 * never from the path or body. The builder document contains no scoring configuration of the platform, only what an administrator
 * authors, and publishing turns it into a validated template layer. Questionnaire content is never written to the logs.
 */
@RestController
@RequestMapping("/api/v1/questionnaires")
@Tag(name = "Questionnaires")
public class QuestionnaireController {

    private final QuestionnaireService questionnaires;

    public QuestionnaireController(QuestionnaireService questionnaires) {
        this.questionnaires = questionnaires;
    }

    @Operation(summary = "The tenant's questionnaires, newest first")
    @GetMapping
    public List<Summary> list(@AuthenticationPrincipal Jwt jwt) {
        return questionnaires.list(caller(jwt));
    }

    @Operation(summary = "A questionnaire: its open draft, else its latest published version, or the version asked for")
    @GetMapping("/{id}")
    public Detail get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestParam(required = false) Integer version) {
        return questionnaires.get(caller(jwt), id, version);
    }

    @Operation(summary = "Create an empty questionnaire with a first draft")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Detail create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateQuestionnaireRequest request) {
        return questionnaires.create(caller(jwt), request.name());
    }

    @Operation(summary = "Create a questionnaire whose first draft is a copy of the platform default; the notes list what the builder could not carry over")
    @PostMapping("/from-initial")
    @ResponseStatus(HttpStatus.CREATED)
    public Detail fromInitial(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateQuestionnaireRequest request) {
        return questionnaires.fromInitial(caller(jwt), request.name());
    }

    @Operation(summary = "Save the open draft (opens the next draft when the latest version is published)")
    @PutMapping("/{id}")
    public Detail save(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestBody QuestionnaireDocument document) {
        return questionnaires.saveDraft(caller(jwt), id, document);
    }

    @Operation(summary = "Publish the open draft as an active template layer; 422 lists everything that blocks it")
    @PostMapping("/{id}/publish")
    public Detail publish(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return questionnaires.publish(caller(jwt), id);
    }

    @Operation(summary = "Hide the questionnaire from new use (past assessments stay explainable)")
    @PostMapping("/{id}/archive")
    public Detail archive(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return questionnaires.archive(caller(jwt), id);
    }

    @Operation(summary = "A new questionnaire with a copy of this one as its first draft")
    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public Detail duplicate(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return questionnaires.duplicate(caller(jwt), id);
    }

    private static Caller caller(Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        return Caller.tenant(user.tenantId(), user.actor());
    }
}
