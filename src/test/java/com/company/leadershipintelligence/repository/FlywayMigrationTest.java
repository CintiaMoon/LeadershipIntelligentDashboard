package com.company.leadershipintelligence.repository;

import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Migrations applied to a brand-new empty database, independent of the application's own schema (V3 Section 38.2). */
class FlywayMigrationTest extends PostgresIntegrationTest {

    private static final String DATABASE = "flyway_clean";

    private List<String> query(Connection c, String sql) throws Exception {
        List<String> rows = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(rs.getString(1));
            }
        }
        return rows;
    }

    @Test
    void migrationsApplyInOrderToAnEmptyDatabaseAndAreIdempotent() throws Exception {
        jdbc.execute("drop database if exists " + DATABASE);
        jdbc.execute("create database " + DATABASE);
        String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + DATABASE);
        Flyway flyway = Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).locations("classpath:db/migration").load();

        var first = flyway.migrate();
        assertThat(first.success).isTrue();
        assertThat(first.migrationsExecuted).isEqualTo(8);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();

        try (Connection c = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(query(c, "select version from flyway_schema_history where success order by installed_rank"))
                    .containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
            assertThat(query(c, "select table_name from information_schema.tables where table_schema = 'public'"))
                    .contains("tenant", "assessment", "assessment_answer", "assessment_snapshot", "scoring_configuration",
                            "scoring_configuration_version", "scoring_run", "scoring_dimension_result", "scoring_contribution",
                            "scoring_pattern_evaluation", "scoring_pattern_evidence", "scoring_penalty_result",
                            "scoring_imbalance_result", "scoring_idempotency_key", "scoring_access_audit");
            assertThat(query(c, "select count(*) from pg_trigger where not tgisinternal")).first().asString().isNotEqualTo("0");
        }
    }

    @Test
    void responseScopesMigrationKeepsEarlierDataAsOrganizationLevel() throws Exception {
        String database = "flyway_upgrade";
        jdbc.execute("drop database if exists " + database);
        jdbc.execute("create database " + database);
        String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
        Flyway before = Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target("5").load();
        before.migrate();

        try (Connection c = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword()); Statement s = c.createStatement()) {
            s.execute("insert into tenant (id, name) values ('t', 't')");
            s.execute("insert into organization (id, tenant_id, name) values ('o', 't', 'o')");
            s.execute("insert into assessment_template (id, tenant_id, name) values ('tpl', 't', 'tpl')");
            s.execute("insert into assessment_template_version (template_id, version, tenant_id) values ('tpl', 1, 't')");
            s.execute("insert into assessment (id, tenant_id, organization_id, template_id, template_version, status, completed_at) "
                    + "values ('a', 't', 'o', 'tpl', 1, 'COMPLETED', now())");
            s.execute("insert into assessment_snapshot (id, tenant_id, assessment_id, revision, input_hash) values ('s', 't', 'a', 1, 'sha256:x')");
            s.execute("insert into assessment_answer (snapshot_id, tenant_id, question_id, answer) values ('s', 't', 'Q1', '4'), ('s', 't', 'Q2', '\"OFTEN\"')");
            s.execute("insert into scoring_run (id, tenant_id, organization_id, assessment_id, snapshot_id, source, configuration_selection, "
                    + "engine_version, algorithm_version, configuration_version, configuration_hash, input_hash, status, layers, error_code, "
                    + "started_at, completed_at) values ('00000000-0000-0000-0000-000000000001', 't', 'o', 'a', 's', 'CALCULATE', 'DEFAULT', "
                    + "'1', '1', 'v', 'h', 'i', 'FAILED', '[]', 'X', now(), now())");
        }

        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).locations("classpath:db/migration").load().migrate();

        try (Connection c = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword()); Statement s = c.createStatement()) {
            assertThat(query(c, "select scope_level || '/' || team_id || '/' || ordinal || '/' || coalesce(declared_respondents::text, '-') "
                    + "from assessment_answer order by question_id")).containsExactly("ORGANIZATION//0/-", "ORGANIZATION//0/-");
            assertThat(query(c, "select scope_type || '/' || coalesce(scope_id, '-') || '/' || coalesce(parent_run_id::text, '-') || '/' "
                    + "|| coalesce(response_summaries::text, '-') from scoring_run")).containsExactly("ORGANIZATION/-/-/-");
            // a team run must name its team and its parent; an organization run must name neither
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> s.execute("insert into scoring_run (id, tenant_id, organization_id, "
                    + "assessment_id, snapshot_id, source, configuration_selection, engine_version, algorithm_version, configuration_version, "
                    + "configuration_hash, input_hash, status, layers, error_code, started_at, completed_at, scope_type) values "
                    + "('00000000-0000-0000-0000-000000000002', 't', 'o', 'a', 's', 'CALCULATE', 'DEFAULT', '1', '1', 'v', 'h', 'i2', 'FAILED', "
                    + "'[]', 'X', now(), now(), 'TEAM')")).isInstanceOf(java.sql.SQLException.class);
            // history is still immutable
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> s.execute("update scoring_run set source = 'SCORE'"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("immutable");
        }
    }

    @Test
    void theApplicationSchemaIsTheMigratedSchema() {
        Integer applied = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isEqualTo(8);
    }
}
