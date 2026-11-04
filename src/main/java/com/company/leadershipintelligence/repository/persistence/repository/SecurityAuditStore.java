package com.company.leadershipintelligence.repository.persistence.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Append-only security events (decision D74). The detail never holds a password, a token or a cookie. */
@Repository
public class SecurityAuditStore {

    private final JdbcTemplate jdbc;

    public SecurityAuditStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(Instant at, String eventType, String outcome, UUID userId, String tenantId, String subjectHash,
                       String clientIp, String userAgent, String detailJson) {
        jdbc.update("insert into security_audit_event (occurred_at, event_type, outcome, user_id, tenant_id, subject_hash, client_ip, user_agent, detail) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                Timestamp.from(at), eventType, outcome, userId, tenantId, subjectHash, clientIp, userAgent, detailJson);
    }
}
