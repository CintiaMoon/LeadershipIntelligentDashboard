package com.company.leadershipintelligence.config.beans;

import com.company.leadershipintelligence.config.properties.EngineProperties;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.service.ScoringOrchestrator;
import com.company.leadershipintelligence.domain.service.StageTimer;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({EngineProperties.class, com.company.leadershipintelligence.config.properties.InitialConfigurationProperties.class})
public class EngineBeans {

    /** The scoring engine is stateless and shared. Confidence bands are the illustrative V3 defaults. */
    /** Structured scoring log; the tenant id is written only when lis.logging.include-tenant-id is true. */
    @Bean
    com.company.leadershipintelligence.service.ScoringLog scoringLog(
            @org.springframework.beans.factory.annotation.Value("${lis.logging.include-tenant-id:false}") boolean includeTenantId) {
        return new com.company.leadershipintelligence.service.ScoringLog(includeTenantId);
    }

    @Bean
    StageTimer stageTimer(ObservationRegistry registry) {
        return new ObservedStageTimer(registry);
    }

    @Bean
    ScoringOrchestrator scoringOrchestrator(StageTimer timer) {
        return ScoringOrchestrator.standard(ConfidenceBandConfig.illustrative(), timer);
    }
}
