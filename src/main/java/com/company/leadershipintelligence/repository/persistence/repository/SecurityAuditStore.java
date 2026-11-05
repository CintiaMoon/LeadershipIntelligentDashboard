package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.repository.persistence.entity.AuditQuery;
import com.company.leadershipintelligence.repository.persistence.entity.AuditRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Append-only security events (decision D74). The detail never holds a password, a token or a cookie. Rows are never changed; the
 * retention job is the only thing that removes them, and only those older than the retention period.
 */
@Repository
public class SecurityAuditStore {

    private static final String FROM = " from security_audit_event e left join app_user u on u.id = e.user_id ";

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

    /** Newest first, then by id, so the order is the same on every read. */
    public List<AuditRow> search(AuditQuery query, int limit, int offset) {
        List<Object> arguments = new ArrayList<>();
        String where = where(query, arguments);
        arguments.add(limit);
        arguments.add(offset);
        return jdbc.query("select e.id, e.occurred_at, e.event_type, e.outcome, e.user_id, u.email, e.tenant_id, e.client_ip, e.user_agent, e.detail::text"
                + FROM + where + " order by e.occurred_at desc, e.id desc limit ? offset ?",
                (rs, n) -> new AuditRow(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10)), arguments.toArray());
    }

    public long count(AuditQuery query) {
        List<Object> arguments = new ArrayList<>();
        String where = where(query, arguments);
        Long n = jdbc.queryForObject("select count(*)" + FROM + where, Long.class, arguments.toArray());
        return n == null ? 0 : n;
    }

    /**
     * Deletes the events older than {@code cutoff} and returns how many. The table refuses every other delete: the trigger lets this
     * one through because the transaction says it is the retention job (local setting, gone at commit).
     */
    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        jdbc.queryForObject("select set_config('lis.audit_purge', 'on', true)", String.class);
        return jdbc.update("delete from security_audit_event where occurred_at < ?", Timestamp.from(cutoff));
    }

    private static String where(AuditQuery q, List<Object> arguments) {
        List<String> conditions = new ArrayList<>();
        if (q.tenantId() != null) {
            conditions.add("e.tenant_id = ?");
            arguments.add(q.tenantId());
        }
        if (q.eventType() != null) {
            conditions.add("e.event_type = ?");
            arguments.add(q.eventType());
        }
        if (q.outcome() != null) {
            conditions.add("e.outcome = ?");
            arguments.add(q.outcome());
        }
        if (q.userId() != null) {
            conditions.add("e.user_id = ?");
            arguments.add(q.userId());
        }
        if (q.from() != null) {
            conditions.add("e.occurred_at >= ?");
            arguments.add(Timestamp.from(q.from()));
        }
        if (q.to() != null) {
            conditions.add("e.occurred_at < ?");
            arguments.add(Timestamp.from(q.to()));
        }
        return conditions.isEmpty() ? "" : "where " + String.join(" and ", conditions);
    }
}
