package com.company.leadershipintelligence.repository;

import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private UUID configuration(String tenant) {
        jdbc.update("insert into tenant (id, name) values (?, ?) on conflict do nothing", tenant, tenant);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into scoring_configuration (id, tenant_id, scope_type, scope_id, name) values (?, ?, 'TENANT', null, 'c')",
                id, tenant);
        return id;
    }

    private UUID version(UUID configuration, String tenant, int number, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into scoring_configuration_version (id, configuration_id, tenant_id, version, status, payload, config_hash, created_by) "
                + "values (?, ?, ?, ?, ?, '{\"a\":1}'::jsonb, 'h', 'tester')", id, configuration, tenant, number, status);
        return id;
    }

    @Test
    void migrationsApplyAndHibernateValidatesTheMapping() {
        Integer tables = jdbc.queryForObject("select count(*) from information_schema.tables where table_name in "
                + "('tenant','organization','assessment','assessment_snapshot','assessment_answer','scoring_configuration',"
                + "'scoring_configuration_version','scoring_configuration_audit')", Integer.class);
        assertThat(tables).isEqualTo(8);
    }

    @Test
    void draftContentMayChangeButActiveContentIsImmutable() {
        UUID configuration = configuration("t-immutable");
        UUID draft = version(configuration, "t-immutable", 1, "DRAFT");
        jdbc.update("update scoring_configuration_version set payload = '{\"a\":2}'::jsonb where id = ?", draft);
        assertThat(jdbc.queryForObject("select payload->>'a' from scoring_configuration_version where id = ?", String.class, draft)).isEqualTo("2");

        jdbc.update("update scoring_configuration_version set status = 'ACTIVE' where id = ?", draft);
        assertThatThrownBy(() -> jdbc.update("update scoring_configuration_version set payload = '{\"a\":3}'::jsonb where id = ?", draft))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("update scoring_configuration_version set config_hash = 'other' where id = ?", draft))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        // status changes (pause, archive) remain allowed
        jdbc.update("update scoring_configuration_version set status = 'PAUSED' where id = ?", draft);
        assertThat(jdbc.queryForObject("select status from scoring_configuration_version where id = ?", String.class, draft)).isEqualTo("PAUSED");
    }

    @Test
    void versionNumbersAreUniquePerConfigurationAndStatusesAreConstrained() {
        UUID configuration = configuration("t-unique");
        version(configuration, "t-unique", 1, "DRAFT");
        assertThatThrownBy(() -> version(configuration, "t-unique", 1, "DRAFT")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> version(configuration, "t-unique", 2, "BOGUS")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyPlatformDefaultsMayHaveNoTenantAndScopesAreUniquePerTenant() {
        assertThatThrownBy(() -> jdbc.update("insert into scoring_configuration (id, tenant_id, scope_type, name) values (?, null, 'TENANT', 'x')",
                UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        configuration("t-scope");
        assertThatThrownBy(() -> jdbc.update("insert into scoring_configuration (id, tenant_id, scope_type, scope_id, name) values (?, 't-scope', 'TENANT', null, 'dup')",
                UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(POSTGRES.isRunning()).isTrue();
    }
}
