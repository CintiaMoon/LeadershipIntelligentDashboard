package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.service.identity.SecurityAuditRetention;
import com.company.leadershipintelligence.support.AccountFlowTest;
import com.company.leadershipintelligence.support.SessionClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.company.leadershipintelligence.support.SessionClient.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reading the security audit and its retention (decision D74): the administrators of a company read the events of that company and no
 * other, the platform administrator reads every event, every read is recorded, and events past the retention period are deleted by the
 * retention job and by nothing else.
 */
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class SecurityAuditApiIntegrationTest extends AccountFlowTest {

    @Autowired
    SecurityAuditRetention retention;

    private SessionClient owner(String email, String company) throws Exception {
        SessionClient b = signedIn(email);
        member(company, email, "ACTIVE", "OWNER");
        return b.inCompany(company);
    }

    private SessionClient platformAdmin() throws Exception {
        signedIn("root@example.com").post("/api/v1/auth/logout", null);
        jdbc.update("update app_user set platform_admin = true where email = 'root@example.com'");
        SessionClient b = browser();
        assertThat(status(b.post("/api/v1/auth/login", Map.of("email", "root@example.com", "password", PASSWORD)))).isEqualTo(200);
        return b;
    }

    private void event(String type, String outcome, String tenant, UUID user, String daysAgo) {
        jdbc.update("insert into security_audit_event (occurred_at, event_type, outcome, user_id, tenant_id, client_ip, user_agent, detail) "
                + "values (now() - (?::text || ' days')::interval, ?, ?, ?, ?, '10.1.1.1', 'test-agent', '{\"k\":\"v\"}'::jsonb)", daysAgo, type, outcome, user, tenant);
    }

    /** An event stamped by the application clock (which the tests fix), as the retention job measures age. */
    private void aged(String type, long days) {
        jdbc.update("insert into security_audit_event (occurred_at, event_type, outcome, detail) values (?, ?, 'SUCCESS', '{}'::jsonb)",
                java.sql.Timestamp.from(clock.instant().minus(java.time.Duration.ofDays(days))), type);
    }

    // ---------------------------------------------------------------- reading

    @Test
    void theAdministratorsOfACompanyReadTheEventsOfThatCompanyOnly() throws Exception {
        SessionClient acme = owner("owner@example.com", "acme");
        UUID ownerId = userId("owner@example.com");
        event("MEMBERSHIP_INVITED", "SUCCESS", "acme", ownerId, "1");
        event("MEMBERSHIP_REMOVED", "SUCCESS", "acme", ownerId, "2");
        event("MEMBERSHIP_INVITED", "SUCCESS", "globex", null, "1");
        event("LOGIN", "FAILURE", null, null, "1");

        MvcResult r = acme.get("/api/v1/security-audit");
        assertThat(status(r)).isEqualTo(200);
        JsonNode page = acme.body(r);
        String text = page.toString();
        assertThat(text).contains("MEMBERSHIP_INVITED").contains("MEMBERSHIP_REMOVED").doesNotContain("globex").doesNotContain("\"LOGIN\"");
        for (JsonNode item : page.get("items")) {
            assertThat(item.get("tenantId").asText()).isEqualTo("acme");
        }
        JsonNode first = page.get("items").get(0);
        assertThat(first.get("userEmail").asText()).isEqualTo("owner@example.com");
        assertThat(first.get("clientIp").asText()).isEqualTo("10.1.1.1");
        assertThat(first.get("detail").get("k").asText()).isEqualTo("v");
        // newest first
        assertThat(page.get("items").get(0).get("occurredAt").asText()).isGreaterThan(page.get("items").get(1).get("occurredAt").asText());
    }

    @Test
    void theEventsCanBeFilteredAndPaged() throws Exception {
        SessionClient acme = owner("owner@example.com", "acme");
        UUID ownerId = userId("owner@example.com");
        for (int i = 1; i <= 5; i++) {
            event("MEMBERSHIP_INVITED", "SUCCESS", "acme", ownerId, String.valueOf(i));
        }
        event("API_TOKEN_REJECTED", "FAILURE", "acme", null, "1");

        JsonNode failures = acme.body(acme.get("/api/v1/security-audit?outcome=FAILURE"));
        assertThat(failures.get("items")).hasSize(1);
        assertThat(failures.get("items").get(0).get("eventType").asText()).isEqualTo("API_TOKEN_REJECTED");
        assertThat(acme.body(acme.get("/api/v1/security-audit?eventType=MEMBERSHIP_INVITED")).get("total").asLong()).isEqualTo(5L);
        assertThat(acme.body(acme.get("/api/v1/security-audit?userId=" + ownerId + "&eventType=MEMBERSHIP_INVITED")).get("total").asLong()).isEqualTo(5L);
        JsonNode firstPage = acme.body(acme.get("/api/v1/security-audit?eventType=MEMBERSHIP_INVITED&size=2&page=0"));
        JsonNode lastPage = acme.body(acme.get("/api/v1/security-audit?eventType=MEMBERSHIP_INVITED&size=2&page=2"));
        assertThat(firstPage.get("items")).hasSize(2);
        assertThat(firstPage.get("total").asLong()).isEqualTo(5L);
        assertThat(lastPage.get("items")).hasSize(1);
        // a period: from 3.5 days ago on gives the events of days 1 to 3
        String from = java.time.Instant.now().minus(java.time.Duration.ofHours(84)).toString();
        assertThat(acme.body(acme.get("/api/v1/security-audit?eventType=MEMBERSHIP_INVITED&from=" + from)).get("total").asLong()).isEqualTo(3L);
    }

    @Test
    void badParametersAreRefused() throws Exception {
        SessionClient acme = owner("owner@example.com", "acme");
        for (String query : List.of("size=0", "size=201", "page=-1", "outcome=MAYBE", "from=2026-02-01T00:00:00Z&to=2026-01-01T00:00:00Z")) {
            MvcResult r = acme.get("/api/v1/security-audit?" + query);
            assertThat(status(r)).as(query).isEqualTo(422);
            assertThat(code(acme.body(r))).isEqualTo("INVALID_MEMBER");
        }
        assertThat(status(acme.get("/api/v1/security-audit?from=yesterday"))).isEqualTo(400);
        assertThat(status(acme.get("/api/v1/security-audit?userId=not-a-uuid"))).isEqualTo(400);
    }

    @Test
    void aCompanyCannotBeNamedAndOtherRolesAreRefused() throws Exception {
        SessionClient acme = owner("owner@example.com", "acme");
        event("MEMBERSHIP_INVITED", "SUCCESS", "globex", null, "1");
        // a tenant query parameter does nothing: the company is the caller's own
        assertThat(acme.body(acme.get("/api/v1/security-audit?tenantId=globex")).toString()).doesNotContain("globex");
        for (String role : List.of("ENGINEERING_LEADER", "ANALYST", "VIEWER", "RESPONDENT")) {
            String email = role.toLowerCase().replace('_', '-') + "@example.com";
            SessionClient b = signedIn(email);
            member("acme", email, "ACTIVE", role);
            assertThat(status(b.inCompany("acme").get("/api/v1/security-audit"))).as(role).isEqualTo(403);
        }
        assertThat(status(browser().get("/api/v1/security-audit"))).isEqualTo(401);
    }

    @Test
    void everyReadIsRecordedWithWhoReadAndHowMuchWasReturned() throws Exception {
        SessionClient acme = owner("owner@example.com", "acme");
        event("MEMBERSHIP_INVITED", "SUCCESS", "acme", null, "1");
        acme.get("/api/v1/security-audit");
        List<Map<String, Object>> reads = jdbc.queryForList("select user_id, tenant_id, detail::text as detail from security_audit_event where event_type = 'SECURITY_AUDIT_READ'");
        assertThat(reads).hasSize(1);
        assertThat(reads.get(0).get("tenant_id")).isEqualTo("acme");
        assertThat(reads.get(0).get("user_id")).isEqualTo(userId("owner@example.com"));
        assertThat(reads.get(0).get("detail").toString()).contains("\"results\"");
        // and the next read shows the first one
        assertThat(acme.get("/api/v1/security-audit?eventType=SECURITY_AUDIT_READ").getResponse().getContentAsString()).contains("SECURITY_AUDIT_READ");
    }

    // ---------------------------------------------------------------- the platform

    @Test
    void thePlatformAdministratorReadsEveryEventAndMayFilterByCompany() throws Exception {
        SessionClient admin = platformAdmin();
        event("MEMBERSHIP_INVITED", "SUCCESS", "acme", null, "1");
        event("MEMBERSHIP_INVITED", "SUCCESS", "globex", null, "1");
        event("LOGIN", "FAILURE", null, null, "1");
        String all = admin.get("/api/v1/platform/security-audit?size=200").getResponse().getContentAsString();
        assertThat(all).contains("acme").contains("globex").contains("\"LOGIN\"");
        String onlyGlobex = admin.get("/api/v1/platform/security-audit?tenantId=globex").getResponse().getContentAsString();
        assertThat(onlyGlobex).contains("globex").doesNotContain("acme");
    }

    @Test
    void tenantUsersCannotReadThePlatformAudit() throws Exception {
        SessionClient acme = owner("owner@example.com", "acme");
        assertThat(status(acme.get("/api/v1/platform/security-audit"))).isEqualTo(403);
        assertThat(status(browser().get("/api/v1/platform/security-audit"))).isEqualTo(401);
    }

    @Test
    void aPlatformAdministratorHasNoCompanyAudit() throws Exception {
        SessionClient admin = platformAdmin();
        assertThat(status(admin.inCompany("acme").get("/api/v1/security-audit"))).isEqualTo(403);
    }

    // ---------------------------------------------------------------- retention

    @Test
    void eventsPastTheRetentionPeriodAreDeletedAndRecentOnesAreKept() throws Exception {
        aged("OLD_ONE", 401);
        aged("OLD_TWO", 1000);
        aged("RECENT", 399);
        aged("TODAY", 0);
        int removed = retention.purge();
        assertThat(removed).isEqualTo(2);
        assertThat(jdbc.queryForList("select event_type from security_audit_event where event_type in ('OLD_ONE', 'OLD_TWO', 'RECENT', 'TODAY') order by event_type", String.class))
                .containsExactly("RECENT", "TODAY");
        // the purge is itself on record
        assertThat(jdbc.queryForObject("select detail::text from security_audit_event where event_type = 'SECURITY_AUDIT_PURGED'", String.class)).contains("\"removed\"");
        assertThat(retention.purge()).as("nothing more to remove").isZero();
    }

    @Test
    void theAuditStaysAppendOnlyForEverythingExceptTheRetentionJob() {
        event("KEEP", "SUCCESS", "acme", null, "500");
        assertThatThrownBy(() -> jdbc.update("update security_audit_event set outcome = 'FAILURE' where event_type = 'KEEP'")).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from security_audit_event where event_type = 'KEEP'")).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("select count(*) from security_audit_event where event_type = 'KEEP'", Long.class)).isEqualTo(1L);
    }

    @Test
    void evenInsideTheRetentionJobAnUpdateIsRefused() {
        event("KEEP", "SUCCESS", "acme", null, "500");
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.support.JdbcTransactionManager(jdbc.getDataSource()));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.queryForObject("select set_config('lis.audit_purge', 'on', true)", String.class);
            jdbc.update("update security_audit_event set outcome = 'FAILURE' where event_type = 'KEEP'");
        })).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("select outcome from security_audit_event where event_type = 'KEEP'", String.class)).isEqualTo("SUCCESS");
    }

    @Test
    void thePurgeSettingDoesNotOutliveItsTransaction() throws Exception {
        aged("OLD", 500);
        retention.purge();
        // a later delete on a new connection or transaction is refused again
        event("KEEP", "SUCCESS", "acme", null, "500");
        assertThatThrownBy(() -> jdbc.update("delete from security_audit_event where event_type = 'KEEP'")).isInstanceOf(DataAccessException.class);
    }
}
