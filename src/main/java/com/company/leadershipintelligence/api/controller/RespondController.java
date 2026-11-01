package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.request.RunRequests.DraftAnswer;
import com.company.leadershipintelligence.api.request.RunRequests.SaveDraft;
import com.company.leadershipintelligence.api.security.CurrentUser;
import com.company.leadershipintelligence.api.security.RespondentRole;
import com.company.leadershipintelligence.service.query.RunViews.RespondentRun;
import com.company.leadershipintelligence.service.query.RunViews.RespondentRunDetail;
import com.company.leadershipintelligence.service.runs.RespondentService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The respondent flow (decision D48). Only the respondent role reaches it. The respondent is the roster entry that matches the
 * verified e-mail of the token in the token tenant; no request names a person, so a respondent can reach only their own runs.
 * Answers are never written to the logs.
 */
@RestController
@RequestMapping("/api/v1/respond")
@Tag(name = "Respondent")
public class RespondController {

    private final RespondentService respondent;

    public RespondController(RespondentService respondent) {
        this.respondent = respondent;
    }

    @Operation(summary = "The runs I am asked to answer")
    @GetMapping("/runs")
    public List<RespondentRun> runs(@AuthenticationPrincipal Jwt jwt) {
        RespondentRole.require(jwt);
        return respondent.list(CurrentUser.from(jwt).tenantId(), RespondentRole.email(jwt));
    }

    @Operation(summary = "A run I am asked to answer: its questions (without any scoring detail) and my draft")
    @GetMapping("/runs/{runId}")
    public RespondentRunDetail run(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        RespondentRole.require(jwt);
        return respondent.get(CurrentUser.from(jwt).tenantId(), RespondentRole.email(jwt), runId);
    }

    @Operation(summary = "Save answers into my draft; a null or empty answer removes it")
    @PutMapping("/runs/{runId}/draft")
    public RespondentRunDetail saveDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId, @Valid @RequestBody SaveDraft request) {
        RespondentRole.require(jwt);
        Map<String, JsonNode> answers = new LinkedHashMap<>();
        for (DraftAnswer answer : request.answers()) {
            if (answers.containsKey(answer.questionId())) {
                throw new ApiRequestException("question " + answer.questionId() + " is answered twice in one request");
            }
            answers.put(answer.questionId(), answer.answer());
        }
        return respondent.saveDraft(CurrentUser.from(jwt).tenantId(), RespondentRole.email(jwt), runId, answers);
    }

    @Operation(summary = "Submit my answers: they are stored with my team only and my draft is deleted")
    @PostMapping("/runs/{runId}/submit")
    public RespondentRunDetail submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        RespondentRole.require(jwt);
        return respondent.submit(CurrentUser.from(jwt).tenantId(), RespondentRole.email(jwt), runId);
    }
}
