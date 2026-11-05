package com.company.leadershipintelligence.service.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Which security events to read (decision D74). Every field is optional. {@code tenantId} limits the read to one company: the
 * administrators of a company always have it set, and only the platform administrator may leave it out.
 */
public record AuditFilter(String tenantId, String eventType, String outcome, UUID userId, Instant from, Instant to) {
}
