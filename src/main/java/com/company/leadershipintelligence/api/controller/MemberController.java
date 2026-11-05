package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.request.InviteMemberRequest;
import com.company.leadershipintelligence.api.request.UpdateMemberRequest;
import com.company.leadershipintelligence.api.response.MemberResponse;
import com.company.leadershipintelligence.api.security.CurrentUser;
import com.company.leadershipintelligence.service.identity.MemberChange;
import com.company.leadershipintelligence.service.identity.MemberService;
import com.company.leadershipintelligence.service.identity.MemberView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.company.leadershipintelligence.api.security.AuthenticatedCaller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The members of the caller's company and its invitations (decision D72). Owners and administrators only, always as a signed-in
 * person (an API token cannot manage members). The company is the one the caller's membership names.
 */
@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Members")
public class MemberController {

    private final MemberService members;

    public MemberController(MemberService members) {
        this.members = members;
    }

    @Operation(summary = "The members and pending invitations of this company (not the removed ones)")
    @GetMapping
    public List<MemberResponse> list(@AuthenticationPrincipal AuthenticatedCaller caller) {
        Callers.person(caller);
        return members.list(CurrentUser.from(caller).tenantId().value()).stream().map(MemberController::response).toList();
    }

    @Operation(summary = "Invite a person by email with roles and, optionally, organizations; they are sent a single-use link")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse invite(@AuthenticationPrincipal AuthenticatedCaller caller, @Valid @RequestBody InviteMemberRequest request, HttpServletRequest http) {
        UUID actor = Callers.person(caller);
        return response(members.invite(actor, CurrentUser.from(caller).tenantId().value(), request.email(), request.roles(), request.organizationIds(), Callers.client(http)));
    }

    @Operation(summary = "Change the roles, the organizations or the status (ACTIVE, SUSPENDED) of a member; applies at their next request")
    @PatchMapping("/{id}")
    public MemberResponse update(@AuthenticationPrincipal AuthenticatedCaller caller, @PathVariable UUID id, @Valid @RequestBody UpdateMemberRequest request, HttpServletRequest http) {
        UUID actor = Callers.person(caller);
        return response(members.update(actor, CurrentUser.from(caller).tenantId().value(), id,
                new MemberChange(request.roles(), request.organizationIds(), request.status()), Callers.client(http)));
    }

    @Operation(summary = "Remove a member or withdraw an invitation")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthenticatedCaller caller, @PathVariable UUID id, HttpServletRequest http) {
        UUID actor = Callers.person(caller);
        members.remove(actor, CurrentUser.from(caller).tenantId().value(), id, Callers.client(http));
    }

    @Operation(summary = "Send a pending invitation again; the earlier link stops working")
    @PostMapping("/{id}/resend-invitation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resend(@AuthenticationPrincipal AuthenticatedCaller caller, @PathVariable UUID id, HttpServletRequest http) {
        UUID actor = Callers.person(caller);
        members.resendInvitation(actor, CurrentUser.from(caller).tenantId().value(), id, Callers.client(http));
    }

    private static MemberResponse response(MemberView view) {
        return new MemberResponse(view.id(), view.email(), view.displayName(), view.status(), List.copyOf(view.roles()), List.copyOf(view.organizationIds()),
                view.createdAt(), view.updatedAt());
    }
}
