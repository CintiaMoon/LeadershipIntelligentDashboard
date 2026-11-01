package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.domain.enums.ConfigurationScope;
import com.company.leadershipintelligence.repository.persistence.entity.ScoringConfigurationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped access to configuration slots. Platform-level access is separate and explicit. */
public interface ScoringConfigurationRepository extends JpaRepository<ScoringConfigurationEntity, UUID> {

    @Query("select c from ScoringConfigurationEntity c where c.tenantId = :tenantId and c.scopeType = :scope "
            + "and ((:scopeId is null and c.scopeId is null) or c.scopeId = :scopeId)")
    Optional<ScoringConfigurationEntity> findForTenant(@Param("tenantId") String tenantId,
                                                       @Param("scope") ConfigurationScope scope,
                                                       @Param("scopeId") String scopeId);

    @Query("select c from ScoringConfigurationEntity c where c.tenantId is null and c.scopeType = 'PLATFORM_DEFAULT'")
    Optional<ScoringConfigurationEntity> findPlatformDefault();

    /** Serializes lifecycle changes of one configuration slot. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ScoringConfigurationEntity c where c.id = :id")
    Optional<ScoringConfigurationEntity> lockById(@Param("id") UUID id);

    @Query("select c from ScoringConfigurationEntity c where c.id = :id and c.tenantId = :tenantId")
    Optional<ScoringConfigurationEntity> findByIdForTenant(@Param("id") UUID id, @Param("tenantId") String tenantId);
}
