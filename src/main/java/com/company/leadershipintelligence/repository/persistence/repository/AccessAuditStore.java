package com.company.leadershipintelligence.repository.persistence.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Tenant-scoped, append-only record of access events. The detail never contains assessment content. */
@Repository
public class AccessAuditStore {

    private final JdbcTemplate jdbc;

    public AccessAuditStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(String tenantId, String actor, String action, UUID runId, String detailJson, Instant at) {
        jdbc.update("insert into scoring_access_audit (tenant_id, actor, action, run_id, detail, occurred_at) values (?, ?, ?, ?, ?::jsonb, ?)",
                tenantId, actor, action, runId, detailJson, Timestamp.from(at));
    }
}
