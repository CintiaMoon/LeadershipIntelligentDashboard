package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.repository.persistence.entity.TenantRow;
import com.company.leadershipintelligence.repository.persistence.repository.AccessAuditStore;
import com.company.leadershipintelligence.repository.persistence.repository.TenantStore;
import com.company.leadershipintelligence.service.query.TenantView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Creating and listing tenants (decision D46). This is the one operation that is not scoped to a tenant, so the caller
 * must be a platform administrator; the API layer checks that and passes the verified subject here as the actor. Every
 * creation and every list read is recorded in the access audit with who did it. A tenant id is permanent, so it is
 * validated strictly; the name is free display text.
 */
@Service
public class TenantAdministrationService {

    /** Lowercase letters, digits and hyphens, 2 to 63 characters, starting with a letter or a digit. */
    public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}");
    public static final int MAX_NAME = 200;

    private final TenantStore tenants;
    private final AccessAuditStore audit;
    private final Clock clock;

    public TenantAdministrationService(TenantStore tenants, AccessAuditStore audit, Clock clock) {
        this.tenants = tenants;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public TenantView create(String actor, String id, String name) {
        String cleanId = id == null ? "" : id.trim();
        String cleanName = name == null ? "" : name.trim();
        if (!ID.matcher(cleanId).matches()) {
            throw new ScoringException(ScoringErrorCode.INVALID_TENANT, "a tenant id uses 2 to 63 lowercase letters, digits or hyphens and starts with a letter or digit");
        }
        if (cleanName.isEmpty() || cleanName.length() > MAX_NAME) {
            throw new ScoringException(ScoringErrorCode.INVALID_TENANT, "a tenant name has 1 to " + MAX_NAME + " characters");
        }
        if (!tenants.insert(cleanId, cleanName)) {
            throw new ScoringException(ScoringErrorCode.TENANT_ALREADY_EXISTS, "a tenant with this id already exists");
        }
        // the audit row names the new tenant, so it also shows up in that tenant's own audit
        audit.record(cleanId, actor, "TENANT_CREATED", null, "{}", clock.instant());
        return view(tenants.find(cleanId));
    }

    @Transactional
    public List<TenantView> list(String actor) {
        List<TenantView> all = tenants.findAll().stream().map(TenantAdministrationService::view).toList();
        audit.record(null, actor, "TENANTS_LISTED", null, "{\"count\":" + all.size() + "}", clock.instant());
        return all;
    }

    private static TenantView view(TenantRow row) {
        return new TenantView(row.id(), row.name(), row.createdAt());
    }
}
