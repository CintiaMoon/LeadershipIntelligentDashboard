package com.company.leadershipintelligence.config.beans;

import com.company.leadershipintelligence.config.properties.SecurityAuditProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Settings of the security audit and the schedule of its retention job (decision D74). */
@Configuration
@EnableConfigurationProperties(SecurityAuditProperties.class)
public class SecurityAuditBeans {

    /** Turns the scheduler on for the retention job; {@code lis.security-audit.purge-enabled=false} leaves it off. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "lis.security-audit.purge-enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduling {
    }
}
