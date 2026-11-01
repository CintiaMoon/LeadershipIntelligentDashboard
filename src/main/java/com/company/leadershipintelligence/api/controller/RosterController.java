package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.request.RosterRequests.CreatePerson;
import com.company.leadershipintelligence.api.request.RosterRequests.CreateTeam;
import com.company.leadershipintelligence.api.request.RosterRequests.RenameTeam;
import com.company.leadershipintelligence.api.request.RosterRequests.UpdatePerson;
import com.company.leadershipintelligence.api.security.CurrentUser;
import com.company.leadershipintelligence.service.query.RunViews.PersonView;
import com.company.leadershipintelligence.service.query.RunViews.TeamView;
import com.company.leadershipintelligence.service.roster.RosterService;
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
import java.util.UUID;

/**
 * Teams and the roster (decision D48). Owners and administrators only; the tenant comes from the token and every organization is
 * checked against the token organizations. Names and e-mail addresses are personal data: reads are audited and nothing is logged.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Roster")
public class RosterController {

    private final RosterService roster;

    public RosterController(RosterService roster) {
        this.roster = roster;
    }

    @Operation(summary = "Teams of the caller organizations, optionally of one organization")
    @GetMapping("/teams")
    public List<TeamView> teams(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String organizationId) {
        CurrentUser user = CurrentUser.from(jwt);
        return roster.teams(user.tenantId(), user.scope(), organizationId);
    }

    @Operation(summary = "Create a team in an organization")
    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamView createTeam(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTeam request) {
        CurrentUser user = CurrentUser.from(jwt);
        return roster.createTeam(user.tenantId(), user.scope(), request.organizationId(), request.name());
    }

    @Operation(summary = "Rename a team")
    @PutMapping("/teams/{teamId}")
    public TeamView renameTeam(@AuthenticationPrincipal Jwt jwt, @PathVariable String teamId, @Valid @RequestBody RenameTeam request) {
        CurrentUser user = CurrentUser.from(jwt);
        return roster.renameTeam(user.tenantId(), user.scope(), teamId, request.name());
    }

    @Operation(summary = "People on the roster, optionally of one team (the read is audited)")
    @GetMapping("/people")
    public List<PersonView> people(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String teamId) {
        CurrentUser user = CurrentUser.from(jwt);
        return roster.people(user.tenantId(), user.scope(), user.actor(), teamId);
    }

    @Operation(summary = "Add a person to a team")
    @PostMapping("/people")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonView createPerson(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePerson request) {
        CurrentUser user = CurrentUser.from(jwt);
        return roster.createPerson(user.tenantId(), user.scope(), user.actor(), request.teamId(), request.name(), request.email());
    }

    @Operation(summary = "Change a person team, name, e-mail address or active flag")
    @PutMapping("/people/{personId}")
    public PersonView updatePerson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID personId, @Valid @RequestBody UpdatePerson request) {
        CurrentUser user = CurrentUser.from(jwt);
        return roster.updatePerson(user.tenantId(), user.scope(), user.actor(), personId, request.teamId(), request.name(), request.email(),
                request.active());
    }
}
