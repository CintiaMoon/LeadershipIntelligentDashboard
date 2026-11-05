package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.api.response.SecurityAuditResponse;
import com.company.leadershipintelligence.api.security.CurrentUser;
import com.company.leadershipintelligence.api.security.PlatformRole;
import com.company.leadershipintelligence.service.identity.AuditFilter;
import com.company.leadershipintelligence.service.identity.SecurityAuditViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.company.leadershipintelligence.api.security.AuthenticatedCaller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Reading the security audit (decision D74). The owners and administrators of a company read the events of that company; the
 * platform administrator reads every event. Every read is recorded.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Security audit")
public class SecurityAuditController {

    private final SecurityAuditViewService audit;

    public SecurityAuditController(SecurityAuditViewService audit) {
        this.audit = audit;
    }

    @Operation(summary = "The security events of this company, newest first: membership changes, API token events, denied access")
    @GetMapping("/security-audit")
    public SecurityAuditResponse company(@AuthenticationPrincipal AuthenticatedCaller caller,
                                         @RequestParam(required = false) String eventType, @RequestParam(required = false) String outcome,
                                         @RequestParam(required = false) UUID userId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                         @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, HttpServletRequest http) {
        // the company is the caller's own and cannot be named; a token may read it too, and is recorded under its own subject
        String tenant = CurrentUser.from(caller).tenantId().value();
        UUID reader = reader(caller);
        return SecurityAuditResponse.of(audit.search(reader, new AuditFilter(tenant, eventType, outcome, userId, from, to), page, size, Callers.client(http)));
    }

    @Operation(summary = "Every security event of the platform, newest first, optionally of one company (platform administrators only)")
    @GetMapping("/platform/security-audit")
    public SecurityAuditResponse platform(@AuthenticationPrincipal AuthenticatedCaller caller, @RequestParam(required = false) String tenantId,
                                          @RequestParam(required = false) String eventType, @RequestParam(required = false) String outcome,
                                          @RequestParam(required = false) UUID userId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                          @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, HttpServletRequest http) {
        PlatformRole.require(caller);
        return SecurityAuditResponse.of(audit.search(reader(caller), new AuditFilter(tenantId, eventType, outcome, userId, from, to), page, size, Callers.client(http)));
    }

    /** The account that reads, or null for a token (the read is then recorded without a user). */
    private static UUID reader(AuthenticatedCaller caller) {
        try {
            return UUID.fromString(caller.subject());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
