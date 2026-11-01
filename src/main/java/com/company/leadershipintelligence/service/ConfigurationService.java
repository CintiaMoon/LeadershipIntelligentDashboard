package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.domain.enums.ConfigurationScope;
import com.company.leadershipintelligence.domain.enums.ConfigurationStatus;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.repository.cache.TenantScopedCache;
import com.company.leadershipintelligence.repository.configuration.CanonicalJson;
import com.company.leadershipintelligence.repository.configuration.ConfigurationDocumentParser;
import com.company.leadershipintelligence.repository.configuration.ConfigurationMerger;
import com.company.leadershipintelligence.repository.persistence.entity.ScoringConfigurationAuditEntity;
import com.company.leadershipintelligence.repository.persistence.entity.ScoringConfigurationEntity;
import com.company.leadershipintelligence.repository.persistence.entity.ScoringConfigurationVersionEntity;
import com.company.leadershipintelligence.repository.persistence.repository.ScoringConfigurationAuditRepository;
import com.company.leadershipintelligence.repository.persistence.repository.ScoringConfigurationRepository;
import com.company.leadershipintelligence.repository.persistence.repository.ScoringConfigurationVersionRepository;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.CachedConfiguration;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.service.query.ValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Configuration lifecycle (specv3.md Section 23.5, decision D33): DRAFT, VALIDATING, ACTIVE, PAUSED, ARCHIVED.
 * Only DRAFT content can change; ACTIVE content is immutable here and by a database trigger. Every action is
 * tenant-scoped (a caller never sees another tenant's versions, and a foreign id looks like a missing one) and
 * recorded in the audit trail without any assessment content. Activating a version archives the previous active
 * one of the same configuration. Lifecycle changes evict the affected cache entries after the transaction commits.
 *
 * <p>Validation of a tenant, organization or template layer parses it merged over the current platform default
 * (a limitation: lower layers in between are not considered). Activation checks, preview and fixtures beyond
 * the document validation are not implemented.
 */
@Service
public class ConfigurationService {

    private final ScoringConfigurationRepository configurations;
    private final ScoringConfigurationVersionRepository versions;
    private final ScoringConfigurationAuditRepository audit;
    private final ConfigurationDocumentParser parser;
    private final ConfigurationMerger merger;
    private final TenantScopedCache<CachedConfiguration> cache;
    private final ObjectMapper json;
    private final Clock clock;

    public ConfigurationService(ScoringConfigurationRepository configurations, ScoringConfigurationVersionRepository versions,
                                ScoringConfigurationAuditRepository audit, ConfigurationDocumentParser parser,
                                ConfigurationMerger merger, TenantScopedCache<CachedConfiguration> cache, ObjectMapper json,
                                Clock clock) {
        this.configurations = configurations;
        this.versions = versions;
        this.audit = audit;
        this.parser = parser;
        this.merger = merger;
        this.cache = cache;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public ConfigurationVersionView createDraft(Caller caller, ConfigurationTarget target, String name, String payload) {
        authorize(caller, target);
        JsonNode document = readObject(payload);
        Instant now = clock.instant();
        String tenant = target.tenantId() == null ? null : target.tenantId().value();
        ScoringConfigurationEntity slot = (target.scope() == ConfigurationScope.PLATFORM_DEFAULT
                ? configurations.findPlatformDefault()
                : configurations.findForTenant(tenant, target.scope(), target.scopeId()))
                .orElseGet(() -> configurations.save(new ScoringConfigurationEntity(UUID.randomUUID(), tenant, target.scope(),
                        target.scopeId(), name, now)));
        configurations.lockById(slot.getId());
        ScoringConfigurationVersionEntity version = versions.save(new ScoringConfigurationVersionEntity(UUID.randomUUID(), slot.getId(),
                tenant, versions.maxVersion(slot.getId()) + 1, ConfigurationStatus.DRAFT, canonical(document),
                CanonicalJson.hash(document), caller.actor(), now));
        record(slot, version, "CREATED", caller, Map.of("version", version.getVersion()));
        return view(slot, version);
    }

    /**
     * The hash this document would have as a configuration version. It ignores formatting and key order, so a file can be
     * compared with the version that is active.
     */
    public String hashOf(String payload) {
        return CanonicalJson.hash(readObject(payload));
    }

    @Transactional
    public ConfigurationVersionView updateDraft(Caller caller, UUID versionId, String payload) {
        ScoringConfigurationVersionEntity version = load(caller, versionId);
        if (!version.getStatus().editable()) {
            throw illegal("version " + versionId + " is " + version.getStatus() + " and its content cannot change; create a new version");
        }
        JsonNode document = readObject(payload);
        version.replaceContent(canonical(document), CanonicalJson.hash(document));
        ScoringConfigurationEntity slot = slotOf(version);
        record(slot, version, "UPDATED", caller, Map.of());
        return view(slot, version);
    }

    @Transactional
    public ValidationResult submitForValidation(Caller caller, UUID versionId) {
        ScoringConfigurationVersionEntity version = load(caller, versionId);
        ScoringConfigurationEntity slot = slotOf(version);
        move(version, ConfigurationStatus.VALIDATING);
        Optional<String> problem = problemWith(version, slot);
        if (problem.isPresent()) {
            move(version, ConfigurationStatus.DRAFT);
            record(slot, version, "VALIDATION_FAILED", caller, Map.of("problem", problem.get()));
            return new ValidationResult(false, List.of(problem.get()));
        }
        record(slot, version, "VALIDATED", caller, Map.of());
        return new ValidationResult(true, List.of());
    }

    /** Failed activation keeps its audit record, so rollback is disabled for the expected business failures. */
    @Transactional(noRollbackFor = ScoringException.class)
    public ConfigurationVersionView activate(Caller caller, UUID versionId, Approval approval) {
        ScoringConfigurationVersionEntity version = load(caller, versionId);
        ScoringConfigurationEntity slot = configurations.lockById(version.getConfigurationId()).orElseThrow();
        if (!version.getStatus().canTransitionTo(ConfigurationStatus.ACTIVE)) {
            throw illegal("version " + versionId + " is " + version.getStatus() + "; only a VALIDATING version can be activated");
        }
        if (approval == null || approval.approvedBy() == null || approval.approvedBy().isBlank()) {
            return failActivation(caller, slot, version, "approval metadata (approvedBy) is required");
        }
        Optional<String> problem = problemWith(version, slot);
        if (problem.isPresent()) {
            return failActivation(caller, slot, version, problem.get());
        }
        Instant now = clock.instant();
        for (ScoringConfigurationVersionEntity other : versions.findAllOf(slot.getId())) {
            if (!other.getId().equals(version.getId()) && other.getStatus() == ConfigurationStatus.ACTIVE) {
                move(other, ConfigurationStatus.ARCHIVED);
                record(slot, other, "ARCHIVED", caller, Map.of("reason", "SUPERSEDED", "by", version.getId().toString()));
            }
        }
        versions.flush();
        move(version, ConfigurationStatus.ACTIVE);
        version.approve(approval.approvedBy(), now, approval.effectiveFrom() == null ? now : approval.effectiveFrom());
        versions.flush();
        record(slot, version, "ACTIVATED", caller, Map.of("approvedBy", approval.approvedBy()));
        invalidateAfterCommit(slot);
        return view(slot, version);
    }

    @Transactional
    public ConfigurationVersionView pause(Caller caller, UUID versionId) {
        return change(caller, versionId, ConfigurationStatus.PAUSED, "PAUSED");
    }

    @Transactional
    public ConfigurationVersionView resume(Caller caller, UUID versionId) {
        ScoringConfigurationVersionEntity version = load(caller, versionId);
        ScoringConfigurationEntity slot = configurations.lockById(version.getConfigurationId()).orElseThrow();
        boolean otherActive = versions.findAllOf(slot.getId()).stream()
                .anyMatch(v -> !v.getId().equals(version.getId()) && v.getStatus() == ConfigurationStatus.ACTIVE);
        if (otherActive) {
            throw illegal("another version of this configuration is ACTIVE; archive it before resuming this one");
        }
        return change(caller, versionId, ConfigurationStatus.ACTIVE, "RESUMED");
    }

    @Transactional
    public ConfigurationVersionView archive(Caller caller, UUID versionId) {
        return change(caller, versionId, ConfigurationStatus.ARCHIVED, "ARCHIVED");
    }

    @Transactional(readOnly = true)
    public ConfigurationVersionView get(Caller caller, UUID versionId) {
        ScoringConfigurationVersionEntity version = load(caller, versionId);
        return view(slotOf(version), version);
    }

    /** All versions of one configuration slot, newest first; empty when the slot does not exist for the caller. */
    @Transactional(readOnly = true)
    public List<ConfigurationVersionView> history(Caller caller, ConfigurationTarget target) {
        authorize(caller, target);
        String tenant = target.tenantId() == null ? null : target.tenantId().value();
        Optional<ScoringConfigurationEntity> slot = target.scope() == ConfigurationScope.PLATFORM_DEFAULT
                ? configurations.findPlatformDefault()
                : configurations.findForTenant(tenant, target.scope(), target.scopeId());
        return slot.map(s -> versions.findAllOf(s.getId()).stream().map(v -> view(s, v)).toList()).orElse(List.of());
    }

    // ---------------- internals ----------------

    private ConfigurationVersionView change(Caller caller, UUID versionId, ConfigurationStatus next, String action) {
        ScoringConfigurationVersionEntity version = load(caller, versionId);
        ScoringConfigurationEntity slot = slotOf(version);
        move(version, next);
        versions.flush();
        record(slot, version, action, caller, Map.of());
        invalidateAfterCommit(slot);
        return view(slot, version);
    }

    private ConfigurationVersionView failActivation(Caller caller, ScoringConfigurationEntity slot,
                                                    ScoringConfigurationVersionEntity version, String problem) {
        move(version, ConfigurationStatus.DRAFT);
        record(slot, version, "ACTIVATION_FAILED", caller, Map.of("problem", problem));
        throw new ScoringException(ScoringErrorCode.CONFIGURATION_ACTIVATION_FAILED, "activation failed: " + problem,
                Map.of("versionId", version.getId().toString()));
    }

    private Optional<String> problemWith(ScoringConfigurationVersionEntity version, ScoringConfigurationEntity slot) {
        try {
            JsonNode document = parser.read(version.getPayload());
            JsonNode effective = document;
            if (slot.getScopeType() != ConfigurationScope.PLATFORM_DEFAULT) {
                Optional<JsonNode> base = activePlatformDocument();
                if (base.isPresent()) {
                    effective = merger.merge(List.of(base.get(), document));
                }
            }
            parser.parse(effective, new ConfigurationVersion("validation-" + version.getId()));
            return Optional.empty();
        } catch (ScoringException e) {
            return Optional.of(e.code() + ": " + e.getMessage());
        }
    }

    private Optional<JsonNode> activePlatformDocument() {
        return configurations.findPlatformDefault()
                .flatMap(slot -> versions.findActiveAt(slot.getId(), clock.instant()).stream().findFirst())
                .map(v -> parser.read(v.getPayload()));
    }

    private void move(ScoringConfigurationVersionEntity version, ConfigurationStatus next) {
        if (!version.getStatus().canTransitionTo(next)) {
            throw illegal("version " + version.getId() + " cannot move from " + version.getStatus() + " to " + next);
        }
        version.moveTo(next);
    }

    private ScoringConfigurationVersionEntity load(Caller caller, UUID versionId) {
        Optional<ScoringConfigurationVersionEntity> found = caller.isPlatform()
                ? versions.findPlatformById(versionId)
                : versions.findByIdForTenant(versionId, caller.tenantId().value());
        return found.orElseThrow(() -> new ScoringException(ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND,
                "configuration version not found", Map.of("versionId", versionId.toString())));
    }

    private ScoringConfigurationEntity slotOf(ScoringConfigurationVersionEntity version) {
        return configurations.findById(version.getConfigurationId()).orElseThrow();
    }

    private static void authorize(Caller caller, ConfigurationTarget target) {
        boolean platformTarget = target.scope() == ConfigurationScope.PLATFORM_DEFAULT;
        boolean allowed = caller.isPlatform() ? platformTarget : !platformTarget && caller.tenantId().equals(target.tenantId());
        if (!allowed) {
            throw new ScoringException(ScoringErrorCode.TENANT_ACCESS_DENIED, "the caller cannot manage this configuration");
        }
    }

    private JsonNode readObject(String payload) {
        JsonNode document = parser.read(payload);
        if (!document.isObject()) {
            throw new ScoringException(ScoringErrorCode.CONFIGURATION_SNAPSHOT_INVALID, "a configuration must be a JSON object");
        }
        return document;
    }

    private static String canonical(JsonNode document) {
        return CanonicalJson.canonicalize(document);
    }

    private static ScoringException illegal(String message) {
        return new ScoringException(ScoringErrorCode.INVALID_CONFIGURATION_VERSION, message);
    }

    private void record(ScoringConfigurationEntity slot, ScoringConfigurationVersionEntity version, String action, Caller caller,
                        Map<String, ?> detail) {
        String text;
        try {
            text = json.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        audit.save(new ScoringConfigurationAuditEntity(slot.getTenantId(), slot.getId(), version.getId(), action, caller.actor(),
                clock.instant(), text));
    }

    private ConfigurationVersionView view(ScoringConfigurationEntity slot, ScoringConfigurationVersionEntity v) {
        return new ConfigurationVersionView(v.getId(), slot.getId(), slot.getScopeType(), slot.getScopeId(), v.getVersion(),
                v.getStatus(), v.getConfigHash(), v.getCreatedBy(), v.getCreatedAt(), v.getApprovedBy(), v.getApprovedAt(),
                v.getEffectiveFrom(), v.getPayload());
    }

    private void invalidateAfterCommit(ScoringConfigurationEntity slot) {
        Runnable evict = slot.getTenantId() == null ? cache::invalidateAll : () -> cache.invalidateTenant(slot.getTenantId());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }
}
