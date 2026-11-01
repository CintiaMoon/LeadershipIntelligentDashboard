package com.company.leadershipintelligence.support;

import com.company.leadershipintelligence.repository.cache.TenantScopedCache;
import com.company.leadershipintelligence.service.query.CachedConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests: one real PostgreSQL (Testcontainers) shared by the whole test run so the Spring
 * context can be cached, Flyway migrations applied, Hibernate validating the mapping. Configuration tables are
 * emptied and the cache cleared before each test; tests use their own tenant ids.
 */
@SpringBootTest
@Import(TestClockConfiguration.class)
public abstract class PostgresIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // tests create their own configurations; the bundled initial one is covered by InitialConfigurationLoaderIntegrationTest
        registry.add("lis.configuration.initial.enabled", () -> "false");
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TenantScopedCache<CachedConfiguration> effectiveCache;

    @Autowired
    protected TestClock clock;

    @BeforeEach
    void cleanConfiguration() {
        jdbc.execute("truncate scoring_access_audit, scoring_penalty_result, scoring_imbalance_result, scoring_pattern_evidence, scoring_pattern_evaluation, "
                + "scoring_contribution, scoring_dimension_result, scoring_run, assessment_answer, assessment_snapshot, assessment, "
                + "assessment_template_version, assessment_template, organization, scoring_configuration_audit, "
                + "scoring_configuration_version, scoring_configuration cascade");
        effectiveCache.invalidateAll();
        clock.set(java.time.Instant.parse("2026-11-01T10:00:00Z"));
    }

    protected void tenant(String id) {
        jdbc.update("insert into tenant (id, name) values (?, ?) on conflict do nothing", id, id);
    }
}
