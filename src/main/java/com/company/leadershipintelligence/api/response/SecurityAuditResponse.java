package com.company.leadershipintelligence.api.response;

import com.company.leadershipintelligence.service.identity.SecurityAuditViewService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One page of security events (decision D74), newest first. The detail is short labels; it never holds a password, a token or a cookie. */
public record SecurityAuditResponse(List<Event> items, int page, int size, long total) {

    public record Event(long id, Instant occurredAt, String eventType, String outcome, UUID userId, String userEmail, String tenantId,
                        String clientIp, String userAgent, Map<String, String> detail) {
    }

    public static SecurityAuditResponse of(SecurityAuditViewService.Page page) {
        return new SecurityAuditResponse(page.items().stream().map(e -> new Event(e.id(), e.occurredAt(), e.eventType(), e.outcome(), e.userId(),
                e.userEmail(), e.tenantId(), e.clientIp(), e.userAgent(), e.detail())).toList(), page.page(), page.size(), page.total());
    }
}
