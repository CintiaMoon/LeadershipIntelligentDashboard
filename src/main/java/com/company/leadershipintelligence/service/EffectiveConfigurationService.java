package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.domain.enums.ConfigurationScope;
import com.company.leadershipintelligence.domain.enums.ConfigurationStatus;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.cache.TenantScopedCache;
import com.company.leadershipintelligence.repository.configuration.CanonicalJson;
import com.company.leadershipintelligence.repository.configuration.ConfigurationDocumentParser;
import com.company.leadershipintelligence.repository.configuration.ConfigurationMerger;
import com.company.leadershipintelligence.repository.persistence.entity.ScoringConfigurationEntity;
import com.company.leadershipintelligence.repository.persistence.entity.ScoringConfigurationVersionEntity;
import com.company.leadershipintelligence.repository.persistence.repository.ScoringConfigurationRepository;
import com.company.leadershipintelligence.repository.persistence.repository.ScoringConfigurationVersionRepository;
import com.company.leadershipintelligence.service.query.CachedConfiguration;
import com.company.leadershipintelligence.service.query.EffectiveConfiguration;
import com.company.leadershipintelligence.service.query.LayerRef;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the effective configuration (specv3.md Sections 23.2, 23.3, 24): the ACTIVE, currently effective
 * version of each layer is merged from the platform default up to the tenant override, the result is validated by
 * building the immutable snapshot, and it is cached per tenant. The snapshot is loaded once per scoring run and
 * is never changed afterwards. A version scheduled to become effective later makes the cache entry stale at that
 * instant, so caching never alters which configuration is used. A bounded number of queries is made (one per layer
 * plus one per layer's versions).
 */
@Service
public class EffectiveConfigurationService {

    private final ScoringConfigurationRepository configurations;
    private final ScoringConfigurationVersionRepository versions;
    private final ConfigurationDocumentParser parser;
    private final ConfigurationMerger merger;
    private final TenantScopedCache<CachedConfiguration> cache;
    private final Clock clock;

    public EffectiveConfigurationService(ScoringConfigurationRepository configurations, ScoringConfigurationVersionRepository versions,
                                         ConfigurationDocumentParser parser, ConfigurationMerger merger,
                                         TenantScopedCache<CachedConfiguration> cache, Clock clock) {
        this.configurations = configurations;
        this.versions = versions;
        this.parser = parser;
        this.merger = merger;
        this.cache = cache;
        this.clock = clock;
    }

    /** @param organization may be null; @param templateId may be null */
    @Transactional(readOnly = true)
    public EffectiveConfiguration resolve(TenantId tenant, OrganizationId organization, String templateId) {
        Instant now = clock.instant();
        String organizationKey = organization == null ? null : organization.value();
        CachedConfiguration cached = cache.getIfPresent(tenant.value(), organizationKey, templateId);
        if (cached != null && (cached.validUntil() == null || now.isBefore(cached.validUntil()))) {
            return cached.configuration();
        }
        List<ScoringConfigurationEntity> slots = new ArrayList<>();
        configurations.findPlatformDefault().ifPresent(slots::add);
        if (templateId != null) {
            configurations.findForTenant(tenant.value(), ConfigurationScope.TEMPLATE, templateId).ifPresent(slots::add);
        }
        if (organizationKey != null) {
            configurations.findForTenant(tenant.value(), ConfigurationScope.ORGANIZATION, organizationKey).ifPresent(slots::add);
        }
        configurations.findForTenant(tenant.value(), ConfigurationScope.TENANT, null).ifPresent(slots::add);

        List<JsonNode> documents = new ArrayList<>();
        List<LayerRef> layers = new ArrayList<>();
        Instant validUntil = null;
        for (ScoringConfigurationEntity slot : slots) {
            List<ScoringConfigurationVersionEntity> all = versions.findAllOf(slot.getId());
            Optional<ScoringConfigurationVersionEntity> effective = all.stream()
                    .filter(v -> v.getStatus() == ConfigurationStatus.ACTIVE && !v.getEffectiveFrom().isAfter(now)).findFirst();
            for (ScoringConfigurationVersionEntity v : all) {
                if (v.getStatus() == ConfigurationStatus.ACTIVE && v.getEffectiveFrom().isAfter(now)
                        && (validUntil == null || v.getEffectiveFrom().isBefore(validUntil))) {
                    validUntil = v.getEffectiveFrom();
                }
            }
            if (effective.isPresent()) {
                ScoringConfigurationVersionEntity v = effective.get();
                documents.add(parser.read(v.getPayload()));
                layers.add(new LayerRef(slot.getScopeType(), slot.getScopeId(), v.getId(), v.getVersion(), v.getConfigHash()));
            }
        }
        if (documents.isEmpty()) {
            throw new ScoringException(ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND,
                    "no active scoring configuration applies to this tenant", Map.of());
        }
        JsonNode merged = merger.merge(documents);
        String hash = CanonicalJson.hash(merged);
        String effectiveVersion = effectiveVersion(layers);
        ScoringConfigurationSnapshot snapshot = parser.parse(merged, new ConfigurationVersion(effectiveVersion));
        EffectiveConfiguration result = new EffectiveConfiguration(snapshot, hash, effectiveVersion, layers);
        cache.put(tenant.value(), organizationKey, templateId, new CachedConfiguration(result, validUntil));
        return result;
    }

    /**
     * The configuration of one specific ACTIVE or ARCHIVED version (decision D21), merged over the current active
     * platform default unless it is the platform default itself. Never cached. The tenant may select its own
     * versions or platform versions.
     */
    @Transactional(readOnly = true)
    public EffectiveConfiguration resolveExplicit(TenantId tenant, java.util.UUID versionId) {
        ScoringConfigurationVersionEntity chosen = versions.findByIdForTenant(versionId, tenant.value())
                .or(() -> versions.findPlatformById(versionId))
                .orElseThrow(() -> new ScoringException(ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND,
                        "configuration version not found", Map.of("versionId", versionId.toString())));
        if (chosen.getStatus() != ConfigurationStatus.ACTIVE && chosen.getStatus() != ConfigurationStatus.ARCHIVED) {
            throw new ScoringException(ScoringErrorCode.CONFIGURATION_NOT_ACTIVE,
                    "only an ACTIVE or ARCHIVED configuration version can be selected", Map.of("versionId", versionId.toString()));
        }
        ScoringConfigurationEntity slot = configurations.findById(chosen.getConfigurationId()).orElseThrow();
        List<JsonNode> documents = new ArrayList<>();
        List<LayerRef> layers = new ArrayList<>();
        if (slot.getScopeType() != ConfigurationScope.PLATFORM_DEFAULT) {
            configurations.findPlatformDefault().flatMap(p -> versions.findActiveAt(p.getId(), clock.instant()).stream().findFirst())
                    .ifPresent(base -> {
                        documents.add(parser.read(base.getPayload()));
                        layers.add(new LayerRef(ConfigurationScope.PLATFORM_DEFAULT, null, base.getId(), base.getVersion(), base.getConfigHash()));
                    });
        }
        documents.add(parser.read(chosen.getPayload()));
        layers.add(new LayerRef(slot.getScopeType(), slot.getScopeId(), chosen.getId(), chosen.getVersion(), chosen.getConfigHash()));
        JsonNode merged = merger.merge(documents);
        String effectiveVersion = "explicit:" + effectiveVersion(layers);
        return new EffectiveConfiguration(parser.parse(merged, new ConfigurationVersion(effectiveVersion)),
                CanonicalJson.hash(merged), effectiveVersion, layers);
    }

    /**
     * The configuration a stored run was scored with, rebuilt from the exact layer versions the run recorded. Active
     * versions are immutable, so the result must hash to the run's configuration hash; a mismatch is reported, never
     * ignored. Never cached.
     */
    @Transactional(readOnly = true)
    public ScoringConfigurationSnapshot resolveForRun(TenantId tenant, String layersJson, String expectedHash) {
        List<JsonNode> documents = new ArrayList<>();
        for (JsonNode layer : parser.read(layersJson)) {
            java.util.UUID id = java.util.UUID.fromString(layer.path("versionId").asText());
            ScoringConfigurationVersionEntity version = versions.findByIdForTenant(id, tenant.value())
                    .or(() -> versions.findPlatformById(id))
                    .orElseThrow(() -> new ScoringException(ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND,
                            "a configuration version of the run is no longer available", Map.of()));
            documents.add(parser.read(version.getPayload()));
        }
        JsonNode merged = merger.merge(documents);
        if (!CanonicalJson.hash(merged).equals(expectedHash)) {
            throw new ScoringException(ScoringErrorCode.CONFIGURATION_SNAPSHOT_INVALID,
                    "the stored configuration versions no longer reproduce the run's configuration hash", Map.of());
        }
        return parser.parse(merged, new ConfigurationVersion("run"));
    }

    private static String effectiveVersion(List<LayerRef> layers) {
        StringBuilder text = new StringBuilder();
        for (LayerRef layer : layers) {
            if (text.length() > 0) {
                text.append('|');
            }
            text.append(layer.scope().name().toLowerCase()).append(':');
            if (layer.scopeId() != null) {
                text.append(layer.scopeId()).append(':');
            }
            text.append('v').append(layer.version());
        }
        return text.toString();
    }
}
