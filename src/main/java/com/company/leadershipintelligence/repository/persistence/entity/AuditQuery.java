package com.company.leadershipintelligence.repository.persistence.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Which security events to read (decision D74). Every field is optional; {@code tenantId} limits the read to the events of one
 * company (the company administrators always set it), none reads the events of every company and of none (the platform).
 */
public record AuditQuery(String tenantId, String eventType, String outcome, UUID userId, Instant from, Instant to) {
}
