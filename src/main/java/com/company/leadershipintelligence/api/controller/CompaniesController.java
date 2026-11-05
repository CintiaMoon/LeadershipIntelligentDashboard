package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.response.CompanyResponse;
import com.company.leadershipintelligence.api.security.UserPrincipal;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.service.identity.MembershipService;
import com.company.leadershipintelligence.service.identity.OrganizationReach;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The companies a signed-in user may open (decision D69): only those with an ACTIVE membership, so a company the user was invited
 * to but has not joined, or lost access to, is not listed. It takes a session and no company header.
 */
@RestController
@RequestMapping("/api/v1/companies")
@Tag(name = "Companies")
public class CompaniesController {

    private final MembershipService memberships;

    public CompaniesController(MembershipService memberships) {
        this.memberships = memberships;
    }

    @Operation(summary = "The companies the signed-in user may open (ACTIVE memberships only)")
    @GetMapping
    public List<CompanyResponse> companies(@AuthenticationPrincipal UserPrincipal user) {
        if (user == null) {
            // a bearer token belongs to one company and has no user account behind it
            throw new ScoringException(ScoringErrorCode.AUTHENTICATION_FAILED, "A signed-in session is required.");
        }
        return memberships.companies(user.userId()).stream()
                .map(m -> {
                    OrganizationReach reach = memberships.reach(m.tenantId(), m.organizationIds());
                    return new CompanyResponse(m.tenantId(), m.tenantName(), List.copyOf(m.roles()), reach.organizationIds().stream().sorted().toList(), reach.all());
                }).toList();
    }
}
