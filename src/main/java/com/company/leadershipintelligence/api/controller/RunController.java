package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.request.RunRequests.StartRun;
import com.company.leadershipintelligence.api.security.CurrentUser;
import com.company.leadershipintelligence.service.query.RunViews.RunDetail;
import com.company.leadershipintelligence.service.query.RunViews.RunSummary;
import com.company.leadershipintelligence.service.runs.AssessmentRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Assessment runs for administrators (decision D48). Starting a run pins a published questionnaire and snapshots its participants;
 * closing it scores the stored answers once. The participant list says who has finished and never contains an answer; reading it
 * is audited. Owners and administrators only.
 */
@RestController
@RequestMapping("/api/v1/runs")
@Tag(name = "Assessment runs")
public class RunController {

    private final AssessmentRunService runs;

    public RunController(AssessmentRunService runs) {
        this.runs = runs;
    }

    @Operation(summary = "Runs of the caller organizations, newest first")
    @GetMapping
    public List<RunSummary> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String organizationId,
                                 @RequestParam(required = false) String status) {
        CurrentUser user = CurrentUser.from(jwt);
        return runs.list(user.tenantId(), user.scope(), organizationId, status);
    }

    @Operation(summary = "A run with the progress of each team and who has not finished (the read is audited)")
    @GetMapping("/{runId}")
    public RunDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        CurrentUser user = CurrentUser.from(jwt);
        return runs.get(user.tenantId(), user.scope(), user.actor(), runId);
    }

    @Operation(summary = "Start a run: pin the latest published version of a questionnaire for some teams of an organization")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunDetail start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartRun request) {
        CurrentUser user = CurrentUser.from(jwt);
        return runs.start(user.tenantId(), user.scope(), user.actor(), request.name(), request.organizationId(), request.questionnaireId(),
                request.teamIds(), request.dueDate());
    }

    @Operation(summary = "Close a run: score the stored answers once with the pinned configuration")
    @PostMapping("/{runId}/close")
    public RunDetail close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        CurrentUser user = CurrentUser.from(jwt);
        return runs.close(user.tenantId(), user.scope(), user.actor(), runId);
    }
}
