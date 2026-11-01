package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.repository.persistence.entity.ScoringConfigurationAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScoringConfigurationAuditRepository extends JpaRepository<ScoringConfigurationAuditEntity, Long> {

    List<ScoringConfigurationAuditEntity> findByConfigurationIdOrderByIdAsc(UUID configurationId);
}
