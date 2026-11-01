package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.repository.persistence.entity.ScoringConfigurationVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped access to configuration versions. Platform-level access is separate and explicit. */
public interface ScoringConfigurationVersionRepository extends JpaRepository<ScoringConfigurationVersionEntity, UUID> {

    @Query("select v from ScoringConfigurationVersionEntity v where v.id = :id and v.tenantId = :tenantId")
    Optional<ScoringConfigurationVersionEntity> findByIdForTenant(@Param("id") UUID id, @Param("tenantId") String tenantId);

    @Query("select v from ScoringConfigurationVersionEntity v where v.id = :id and v.tenantId is null")
    Optional<ScoringConfigurationVersionEntity> findPlatformById(@Param("id") UUID id);

    @Query("select v from ScoringConfigurationVersionEntity v where v.configurationId = :configurationId order by v.version desc")
    List<ScoringConfigurationVersionEntity> findAllOf(@Param("configurationId") UUID configurationId);

    @Query("select coalesce(max(v.version), 0) from ScoringConfigurationVersionEntity v where v.configurationId = :configurationId")
    int maxVersion(@Param("configurationId") UUID configurationId);

    @Query("select v from ScoringConfigurationVersionEntity v where v.configurationId = :configurationId "
            + "and v.status = 'ACTIVE' and v.effectiveFrom <= :at order by v.version desc")
    List<ScoringConfigurationVersionEntity> findActiveAt(@Param("configurationId") UUID configurationId, @Param("at") Instant at);
}
