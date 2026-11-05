package com.company.leadershipintelligence.config.beans;

import com.company.leadershipintelligence.repository.persistence.repository.UserStore;
import com.company.leadershipintelligence.service.identity.PasswordHasher;
import com.company.leadershipintelligence.support.AccountFlowTest;
import com.company.leadershipintelligence.support.SessionClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static com.company.leadershipintelligence.support.SessionClient.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demo accounts of the local development stack (profile {@code dev}): every kind of person exists, confirmed and a member of the
 * demo companies, and signs in with the published password through the real sign-in. The seeder is run by hand here, because the
 * tests do not run with the development profile.
 */
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class DevDemoAccountsIntegrationTest extends AccountFlowTest {

    @Autowired
    UserStore users;

    @Autowired
    PasswordHasher hasher;

    @Autowired
    TransactionTemplate transaction;

    private DevDemoAccounts seeder() {
        return new DevDemoAccounts(jdbc, users, hasher, transaction, clock);
    }

    @BeforeEach
    void seed() {
        seeder().run(null);
    }

    private SessionClient signIn(String name) throws Exception {
        SessionClient b = browser();
        assertThat(status(b.post("/api/v1/auth/login", Map.of("email", name + "@demo.test", "password", DevDemoAccounts.PASSWORD)))).as(name).isEqualTo(200);
        return b;
    }

    @Test
    void everyKindOfPersonExistsOnceAndRunningAgainChangesNothing() {
        long accounts = jdbc.queryForObject("select count(*) from app_user", Long.class);
        long memberships = jdbc.queryForObject("select count(*) from company_membership", Long.class);
        assertThat(accounts).isEqualTo(DevDemoAccounts.ACCOUNTS.size());
        assertThat(memberships).isEqualTo(DevDemoAccounts.ACCOUNTS.stream().filter(a -> a.tenant() != null).count());
        seeder().run(null);
        assertThat(jdbc.queryForObject("select count(*) from app_user", Long.class)).isEqualTo(accounts);
        assertThat(jdbc.queryForObject("select count(*) from company_membership", Long.class)).isEqualTo(memberships);
        assertThat(jdbc.queryForObject("select count(*) from app_user where status = 'ACTIVE' and email_verified_at is not null", Long.class)).isEqualTo(accounts);
        assertThat(jdbc.queryForObject("select count(*) from app_user where platform_admin", Long.class)).isEqualTo(3L);
    }

    @Test
    void theOwnerReachesTheDemoCompanyWithItsTwoOrganizations() throws Exception {
        SessionClient owner = signIn("owner");
        JsonNode companies = owner.body(owner.get("/api/v1/companies"));
        assertThat(companies).hasSize(1);
        assertThat(companies.get(0).get("id").asText()).isEqualTo("demo-tenant");
        assertThat(companies.get(0).get("roles").toString()).isEqualTo("[\"OWNER\"]");
        assertThat(companies.get(0).get("organizationIds").toString()).isEqualTo("[\"org-1\",\"org-2\"]");
        assertThat(companies.get(0).get("allOrganizations").asBoolean()).isFalse();
        assertThat(status(owner.inCompany("demo-tenant").get("/api/v1/questionnaires"))).isEqualTo(200);
    }

    @Test
    void theViewerOfOneOrganizationIsLimitedToIt() throws Exception {
        SessionClient viewer = signIn("org1-viewer").inCompany("demo-tenant");
        assertThat(viewer.body(viewer.get("/api/v1/companies")).get(0).get("organizationIds").toString()).isEqualTo("[\"org-1\"]");
        assertThat(status(viewer.get("/api/v1/organizations/org-2/dashboard"))).isEqualTo(403);
        assertThat(status(viewer.get("/api/v1/questionnaires"))).isEqualTo(403);
    }

    @Test
    void aRespondentIsMatchedToTheRosterByTheDemoAddress() throws Exception {
        SessionClient respondent = signIn("respondent1").inCompany("demo-tenant");
        assertThat(jdbc.queryForObject("select email from app_user where email like 'respondent1%'", String.class)).isEqualTo("respondent1@demo.test");
        assertThat(status(respondent.get("/api/v1/respond/runs"))).isEqualTo(200);
        assertThat(status(respondent.get("/api/v1/assessments"))).isEqualTo(403);
    }

    @Test
    void anAdministratorOfAnotherCompanyIsInThatCompanyOnly() throws Exception {
        SessionClient other = signIn("other-admin");
        assertThat(other.body(other.get("/api/v1/companies")).get(0).get("id").asText()).isEqualTo("other-tenant");
        MvcRefusal.assertDenied(other.inCompany("demo-tenant").get("/api/v1/questionnaires"));
    }

    @Test
    void thePlatformAdministratorsHaveNoCompany() throws Exception {
        for (String name : new String[]{"platform-admin", "platform-admin-2", "platform-admin-3"}) {
            SessionClient admin = signIn(name);
            assertThat(status(admin.get("/api/v1/platform/tenants"))).as(name).isEqualTo(200);
            assertThat(status(admin.inCompany("demo-tenant").get("/api/v1/questionnaires"))).as(name).isEqualTo(403);
        }
    }

    /** A refused company is the same answer whether or not it exists. */
    private static final class MvcRefusal {
        static void assertDenied(org.springframework.test.web.servlet.MvcResult result) throws Exception {
            assertThat(status(result)).isEqualTo(403);
            assertThat(result.getResponse().getContentAsString()).contains("COMPANY_ACCESS_DENIED");
        }
    }
}
