package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.config.properties.SecurityAuditProperties;
import com.company.leadershipintelligence.repository.persistence.repository.SecurityAuditStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Deletes security events older than the retention period (decision D74; {@code lis.security-audit.retention}, 400 days by
 * default). It is the only code that deletes them, and what it did is itself recorded. A failure is logged and tried again at the
 * next run; it never affects a request.
 */
@Component
public class SecurityAuditRetention {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityAuditRetention.class);

    private final SecurityAuditStore store;
    private final SecurityAudit audit;
    private final SecurityAuditProperties properties;
    private final Clock clock;

    public SecurityAuditRetention(SecurityAuditStore store, SecurityAudit audit, SecurityAuditProperties properties, Clock clock) {
        this.store = store;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${lis.security-audit.purge-cron:0 30 3 * * *}")
    public void scheduled() {
        if (!properties.purgeEnabled()) {
            return;
        }
        try {
            purge();
        } catch (RuntimeException e) {
            LOG.warn("the security audit retention job failed type={}", e.getClass().getSimpleName());
        }
    }

    /** Deletes the events older than the retention period and returns how many. */
    public int purge() {
        Instant cutoff = clock.instant().minus(properties.retention());
        int removed = store.purgeOlderThan(cutoff);
        if (removed > 0) {
            audit.record("SECURITY_AUDIT_PURGED", SecurityAudit.SUCCESS, null, null, null, ClientInfo.unknown(), Map.of("removed", Integer.toString(removed)));
            LOG.info("security audit events past retention were deleted count={}", removed);
        }
        return removed;
    }
}
