package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.repository.persistence.entity.AuditQuery;
import com.company.leadershipintelligence.repository.persistence.entity.AuditRow;
import com.company.leadershipintelligence.repository.persistence.repository.SecurityAuditStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Reading the security audit (decision D74). The administrators of a company read the events of that company only; the platform
 * administrator reads every event. A read is itself an event ({@code SECURITY_AUDIT_READ}), so who looked is on record. The details
 * are short labels written by the application; nothing here can show a password, a token or a cookie because none is ever stored.
 */
@Service
public class SecurityAuditViewService {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;
    private static final Set<String> OUTCOMES = Set.of(SecurityAudit.SUCCESS, SecurityAudit.FAILURE);

    public record Entry(long id, Instant occurredAt, String eventType, String outcome, UUID userId, String userEmail, String tenantId,
                        String clientIp, String userAgent, Map<String, String> detail) {
    }

    public record Page(List<Entry> items, int page, int size, long total) {
    }

    private final SecurityAuditStore store;
    private final SecurityAudit audit;
    private final ObjectMapper json;

    public SecurityAuditViewService(SecurityAuditStore store, SecurityAudit audit, ObjectMapper json) {
        this.store = store;
        this.audit = audit;
        this.json = json;
    }

    /**
     * One page of events, newest first. {@code filter.tenantId()} limits it to one company; the caller decides whether that may be
     * left out (the platform).
     */
    public Page search(UUID reader, AuditFilter filter, Integer page, Integer size, ClientInfo client) {
        AuditQuery query = new AuditQuery(filter.tenantId(), filter.eventType(), filter.outcome(), filter.userId(), filter.from(), filter.to());
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0 || s < 1 || s > MAX_SIZE) {
            throw new ScoringException(ScoringErrorCode.INVALID_MEMBER, "The page must be 0 or more and the size 1 to " + MAX_SIZE + ".");
        }
        if (query.outcome() != null && !OUTCOMES.contains(query.outcome())) {
            throw new ScoringException(ScoringErrorCode.INVALID_MEMBER, "The outcome must be SUCCESS or FAILURE.");
        }
        if (query.from() != null && query.to() != null && !query.from().isBefore(query.to())) {
            throw new ScoringException(ScoringErrorCode.INVALID_MEMBER, "The start must be before the end.");
        }
        List<Entry> items = store.search(query, s, Math.multiplyExact(p, s)).stream().map(this::entry).toList();
        long total = store.count(query);
        audit.record("SECURITY_AUDIT_READ", SecurityAudit.SUCCESS, reader, query.tenantId(), null, client, Map.of("results", Integer.toString(items.size())));
        return new Page(items, p, s, total);
    }

    private Entry entry(AuditRow row) {
        return new Entry(row.id(), row.occurredAt(), row.eventType(), row.outcome(), row.userId(), row.userEmail(), row.tenantId(), row.clientIp(),
                row.userAgent(), detail(row.detailJson()));
    }

    private Map<String, String> detail(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            return new TreeMap<>(json.readValue(text, new TypeReference<Map<String, String>>() {
            }));
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
