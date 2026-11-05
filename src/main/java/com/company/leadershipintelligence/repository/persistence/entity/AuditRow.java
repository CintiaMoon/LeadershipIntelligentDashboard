package com.company.leadershipintelligence.repository.persistence.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored security event with the email of the account it names. The detail is the stored JSON text: short labels only, never a
 * password, a token or a cookie.
 */
public record AuditRow(long id, Instant occurredAt, String eventType, String outcome, UUID userId, String userEmail, String tenantId,
                       String clientIp, String userAgent, String detailJson) {
}
