package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.service.identity.BootstrapAdministrator;
import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import com.company.leadershipintelligence.support.SessionClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.company.leadershipintelligence.support.SessionClient.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Company memberships end to end against a real PostgreSQL (decision D69): the company comes only from the {@code X-Company-Id}
 * header and only with an ACTIVE membership, the roles and organizations come from the membership, access follows a change at the
 * next request, the platform administrator has no company, and a company's first owner is invited and accepts. Every call goes
 * through the real security chain with a real session.
 */
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class MembershipApiIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct horse battery";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    JavaMailSender mail;

    final List<SimpleMailMessage> sent = new ArrayList<>();

    /** A company id of its own for each test: a company, once created, stays in the database. */
    final String co = "initech-" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void clean() {
        sent.clear();
        doAnswer(call -> {
            sent.add(call.getArgument(0));
            return null;
        }).when(mail).send(any(SimpleMailMessage.class));
        jdbc.execute("truncate app_user, spring_session, spring_session_attributes, security_audit_event cascade");
        jdbc.update("insert into tenant (id, name) values ('acme', 'Acme Corp') on conflict do nothing");
        jdbc.update("insert into tenant (id, name) values ('globex', 'Globex') on conflict do nothing");
    }

    // ---------------------------------------------------------------- helpers

    private SessionClient browser() {
        return new SessionClient(mvc, json);
    }

    private String lastToken() {
        Matcher m = TOKEN.matcher(sent.get(sent.size() - 1).getText());
        assertThat(m.find()).as("a link with a token in the last email").isTrue();
        return m.group(1);
    }

    /** Registers, verifies and signs in an account, the way a person does. */
    private SessionClient signedIn(String email) throws Exception {
        SessionClient b = browser();
        assertThat(status(b.post("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", "Test User")))).isEqualTo(202);
        assertThat(status(b.post("/api/v1/auth/verify-email", Map.of("token", lastToken(), "password", PASSWORD)))).isEqualTo(204);
        assertThat(status(b.post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD)))).isEqualTo(200);
        return b;
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("select id from app_user where email = ?", UUID.class, email);
    }

    private UUID member(String tenant, String email, String status, String... roles) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into company_membership (id, tenant_id, user_id, status, created_at, updated_at) values (?, ?, ?, ?, now(), now())",
                id, tenant, userId(email), status);
        for (String role : roles) {
            jdbc.update("insert into company_membership_role (membership_id, role) values (?, ?)", id, role);
        }
        return id;
    }

    private void restrictTo(UUID membership, String organization) {
        jdbc.update("insert into company_membership_organization (membership_id, organization_id) values (?, ?)", membership, organization);
    }

    private static String code(JsonNode body) {
        return body.path("code").asText();
    }

    // ---------------------------------------------------------------- the companies of a user

    @Test
    void aUserSeesOnlyTheCompaniesWhereTheMembershipIsActive() throws Exception {
        SessionClient b = signedIn("ana@example.com");
        member("acme", "ana@example.com", "ACTIVE", "OWNER", "ADMIN");
        jdbc.update("insert into tenant (id, name) values ('invited-co', 'Invited') on conflict do nothing");
        jdbc.update("insert into tenant (id, name) values ('suspended-co', 'Suspended') on conflict do nothing");
        jdbc.update("insert into tenant (id, name) values ('removed-co', 'Removed') on conflict do nothing");
        member("invited-co", "ana@example.com", "INVITED", "VIEWER");
        member("suspended-co", "ana@example.com", "SUSPENDED", "VIEWER");
        member("removed-co", "ana@example.com", "REMOVED", "VIEWER");

        MvcResult companies = b.get("/api/v1/companies");
        assertThat(status(companies)).isEqualTo(200);
        JsonNode list = b.body(companies);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asText()).isEqualTo("acme");
        assertThat(list.get(0).get("name").asText()).isEqualTo("Acme Corp");
        assertThat(list.get(0).get("roles").toString()).contains("OWNER").contains("ADMIN");
        // no organization list: every organization, with the ones that exist listed
        assertThat(list.get(0).get("allOrganizations").asBoolean()).isTrue();
        // the same list comes with the account
        JsonNode me = b.body(b.get("/api/v1/auth/me"));
        assertThat(me.get("companies")).hasSize(1);
        assertThat(me.get("companies").get(0).get("id").asText()).isEqualTo("acme");
    }


    @Test
    void theCompanyListSaysWhichOrganizationsTheUserReaches() throws Exception {
        SessionClient b = signedIn("ana@example.com");
        jdbc.update("insert into organization (id, tenant_id, name) values ('org-list-a-" + co + "', 'acme', 'A') on conflict do nothing");
        jdbc.update("insert into organization (id, tenant_id, name) values ('org-list-b-" + co + "', 'acme', 'B') on conflict do nothing");
        member("acme", "ana@example.com", "ACTIVE", "VIEWER");
        JsonNode all = b.body(b.get("/api/v1/companies")).get(0);
        assertThat(all.get("allOrganizations").asBoolean()).isTrue();
        assertThat(all.get("organizationIds").toString()).contains("org-list-a-" + co).contains("org-list-b-" + co);
        // limited to a list: exactly that list
        jdbc.update("delete from company_membership_organization");
        UUID membership = jdbc.queryForObject("select id from company_membership where tenant_id = 'acme'", UUID.class);
        restrictTo(membership, "org-list-a-" + co);
        JsonNode limited = b.body(b.get("/api/v1/companies")).get(0);
        assertThat(limited.get("allOrganizations").asBoolean()).isFalse();
        assertThat(limited.get("organizationIds").toString()).isEqualTo("[\"org-list-a-" + co + "\"]");
    }
    @Test
    void aUserWithNoCompanyGetsAnEmptyList() throws Exception {
        SessionClient b = signedIn("ana@example.com");
        assertThat(b.body(b.get("/api/v1/companies"))).isEmpty();
    }

    @Test
    void theCompaniesNeedASession() throws Exception {
        assertThat(status(browser().get("/api/v1/companies"))).isEqualTo(401);
    }

    // ---------------------------------------------------------------- the company in the request

    @Test
    void aTenantEndpointNeedsTheCompanyHeader() throws Exception {
        SessionClient b = signedIn("ana@example.com");
        member("acme", "ana@example.com", "ACTIVE", "OWNER");
        MvcResult r = b.get("/api/v1/questionnaires");
        assertThat(status(r)).isEqualTo(400);
        assertThat(code(b.body(r))).isEqualTo("COMPANY_REQUIRED");
    }

    @Test
    void aCompanyWithoutAnActiveMembershipIsRefusedTheSameWayWhetherOrNotItExists() throws Exception {
        SessionClient b = signedIn("ana@example.com");
        member("acme", "ana@example.com", "ACTIVE", "OWNER");
        member("globex", "ana@example.com", "SUSPENDED", "OWNER");
        b.inCompany("globex");
        MvcResult suspended = b.get("/api/v1/questionnaires");
        b.inCompany("no-such-company");
        MvcResult missing = b.get("/api/v1/questionnaires");
        assertThat(status(suspended)).isEqualTo(403).isEqualTo(status(missing));
        assertThat(code(b.body(suspended))).isEqualTo("COMPANY_ACCESS_DENIED").isEqualTo(code(b.body(missing)));
        assertThat(b.body(suspended).path("message").asText()).isEqualTo(b.body(missing).path("message").asText());
        assertThat(jdbc.queryForObject("select count(*) from security_audit_event where event_type = 'COMPANY_ACCESS_DENIED'", Long.class)).isEqualTo(2L);
    }

    @Test
    void theRolesOfTheMembershipDecideWhatTheUserMayDo() throws Exception {
        SessionClient owner = signedIn("owner@example.com");
        member("acme", "owner@example.com", "ACTIVE", "OWNER");
        SessionClient viewer = signedIn("viewer@example.com");
        member("acme", "viewer@example.com", "ACTIVE", "VIEWER");
        // the questionnaire library is for owners and administrators
        assertThat(status(owner.inCompany("acme").get("/api/v1/questionnaires"))).isEqualTo(200);
        MvcResult denied = viewer.inCompany("acme").get("/api/v1/questionnaires");
        assertThat(status(denied)).isEqualTo(403);
        assertThat(code(viewer.body(denied))).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void theSameUserHasTheRolesOfEachCompanyAndNoneOfAnother() throws Exception {
        SessionClient b = signedIn("ana@example.com");
        member("acme", "ana@example.com", "ACTIVE", "OWNER");
        member("globex", "ana@example.com", "ACTIVE", "VIEWER");
        assertThat(status(b.inCompany("acme").get("/api/v1/questionnaires"))).isEqualTo(200);
        assertThat(status(b.inCompany("globex").get("/api/v1/questionnaires"))).isEqualTo(403);
    }

    @Test
    void aSuspendedMembershipLosesAccessAtTheNextRequest() throws Exception {
        SessionClient b = signedIn("ana@example.com");
        UUID membership = member("acme", "ana@example.com", "ACTIVE", "OWNER");
        b.inCompany("acme");
        assertThat(status(b.get("/api/v1/questionnaires"))).isEqualTo(200);
        jdbc.update("update company_membership set status = 'SUSPENDED' where id = ?", membership);
        MvcResult after = b.get("/api/v1/questionnaires");
        assertThat(status(after)).isEqualTo(403);
        assertThat(code(b.body(after))).isEqualTo("COMPANY_ACCESS_DENIED");
        // and a change of role is seen at once too
        jdbc.update("update company_membership set status = 'ACTIVE' where id = ?", membership);
        assertThat(status(b.get("/api/v1/questionnaires"))).isEqualTo(200);
        jdbc.update("delete from company_membership_role where membership_id = ?", membership);
        jdbc.update("insert into company_membership_role (membership_id, role) values (?, 'VIEWER')", membership);
        assertThat(status(b.get("/api/v1/questionnaires"))).isEqualTo(403);
    }

    @Test
    void theOrganizationsOfTheMembershipLimitWhatTheUserCanReach() throws Exception {
        SessionClient b = signedIn("ana@example.com");
        UUID membership = member("acme", "ana@example.com", "ACTIVE", "VIEWER");
        restrictTo(membership, "org-1");
        b.inCompany("acme");
        MvcResult other = b.get("/api/v1/organizations/org-2/dashboard");
        assertThat(status(other)).isEqualTo(403);
        assertThat(code(b.body(other))).isEqualTo("ORGANIZATION_ACCESS_DENIED");
        assertThat(status(b.get("/api/v1/organizations/org-1/dashboard"))).isNotEqualTo(403);
    }


    @Test
    void aMembershipWithoutAnOrganizationListReachesEveryOrganizationIncludingNewOnes() throws Exception {
        // the owner of a new company has no organizations yet and must be able to create the first one by naming it
        SessionClient owner = signedIn("owner@example.com");
        member("acme", "owner@example.com", "ACTIVE", "OWNER");
        String organization = "org-" + co;
        owner.inCompany("acme");
        assertThat(status(owner.post("/api/v1/teams", Map.of("organizationId", organization, "name", "Platform")))).isEqualTo(201);
        assertThat(owner.get("/api/v1/teams").getResponse().getContentAsString()).contains("Platform");
        assertThat(status(owner.get("/api/v1/organizations/" + organization + "/dashboard"))).isNotEqualTo(403);
        assertThat(status(owner.get("/api/v1/organizations/another-new-org/dashboard"))).as("an organization that does not exist yet is not forbidden").isNotEqualTo(403);
        // configuration management lists the organization it now knows
        assertThat(owner.get("/api/v1/configuration/layers").getResponse().getContentAsString()).contains(organization);
    }

    @Test
    void anUnrestrictedMembershipNeverSeesAnotherCompanysOrganizations() throws Exception {
        SessionClient owner = signedIn("owner@example.com");
        member("acme", "owner@example.com", "ACTIVE", "OWNER");
        jdbc.update("insert into organization (id, tenant_id, name) values ('org-g-" + co + "', 'globex', 'G')");
        jdbc.update("insert into team (id, tenant_id, organization_id, name) values ('team-g-" + co + "', 'globex', 'org-g-" + co + "', 'Globex only')");
        owner.inCompany("acme");
        assertThat(owner.get("/api/v1/teams").getResponse().getContentAsString()).doesNotContain("Globex only");
        // naming another company's organization does not reach it: the organization id belongs to that company
        MvcResult clash = owner.post("/api/v1/teams", Map.of("organizationId", "org-g-" + co, "name", "Hijack"));
        assertThat(status(clash)).isGreaterThanOrEqualTo(400);
        assertThat(jdbc.queryForObject("select count(*) from team where name = 'Hijack'", Long.class)).isZero();
    }

    @Test
    void aMembershipWithAnOrganizationListStaysLimitedToIt() throws Exception {
        SessionClient b = signedIn("ana@example.com");
        UUID membership = member("acme", "ana@example.com", "ACTIVE", "ADMIN");
        restrictTo(membership, "org-1-" + co);
        b.inCompany("acme");
        MvcResult other = b.post("/api/v1/teams", Map.of("organizationId", "org-2-" + co, "name", "Elsewhere"));
        assertThat(status(other)).isEqualTo(403);
        assertThat(code(b.body(other))).isEqualTo("ORGANIZATION_ACCESS_DENIED");
        assertThat(status(b.post("/api/v1/teams", Map.of("organizationId", "org-1-" + co, "name", "Mine")))).isEqualTo(201);
        assertThat(b.get("/api/v1/teams").getResponse().getContentAsString()).contains("Mine").doesNotContain("Elsewhere");
    }
    @Test
    void aCompanyInABodyOrQueryCannotReplaceTheHeader() throws Exception {
        SessionClient b = signedIn("ana@example.com");
        member("acme", "ana@example.com", "ACTIVE", "OWNER");
        // the header names the company; asking for another one by query does nothing
        MvcResult r = b.inCompany("acme").get("/api/v1/questionnaires?tenantId=globex&tenant_id=globex");
        assertThat(status(r)).isEqualTo(200);
        assertThat(b.body(r)).isEmpty();
    }

    @Test
    void aDisabledAccountLosesItsSessionAtTheNextRequest() throws Exception {
        SessionClient b = signedIn("ana@example.com");
        member("acme", "ana@example.com", "ACTIVE", "OWNER");
        b.inCompany("acme");
        assertThat(status(b.get("/api/v1/questionnaires"))).isEqualTo(200);
        jdbc.update("update app_user set status = 'DISABLED'");
        assertThat(status(b.get("/api/v1/questionnaires"))).isEqualTo(401);
        assertThat(status(b.get("/api/v1/companies"))).isEqualTo(401);
    }

    // ---------------------------------------------------------------- CSRF on the tenant endpoints

    @Test
    void aStateChangingCallFromASessionNeedsTheCsrfToken() throws Exception {
        SessionClient b = signedIn("ana@example.com");
        member("acme", "ana@example.com", "ACTIVE", "OWNER");
        b.inCompany("acme");
        MvcResult noToken = b.postWithoutCsrf("/api/v1/teams", Map.of("name", "Platform"));
        assertThat(status(noToken)).isEqualTo(403);
        assertThat(code(b.body(noToken))).isEqualTo("CSRF_TOKEN_INVALID");
        assertThat(status(b.post("/api/v1/teams", Map.of("name", "Platform")))).as("with the token it gets past the CSRF check").isNotEqualTo(403);
    }

    @Test
    void aCallWithNeitherSessionNorTokenIsStillUnauthenticated() throws Exception {
        MvcResult r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/teams")
                .contentType("application/json").content("{\"name\":\"x\"}")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
    }

    // ---------------------------------------------------------------- respondents

    @Test
    void aRespondentMembershipReachesTheRespondentFlowAndNothingElse() throws Exception {
        SessionClient b = signedIn("pat@example.com");
        member("acme", "pat@example.com", "ACTIVE", "RESPONDENT");
        b.inCompany("acme");
        assertThat(status(b.get("/api/v1/respond/runs"))).isEqualTo(200);
        assertThat(status(b.get("/api/v1/questionnaires"))).isEqualTo(403);
        assertThat(status(b.get("/api/v1/organizations/org-1/dashboard"))).isEqualTo(403);
    }

    // ---------------------------------------------------------------- the platform administrator

    private SessionClient platformAdmin() throws Exception {
        signedIn("root@example.com").post("/api/v1/auth/logout", null);
        jdbc.update("update app_user set platform_admin = true where email = 'root@example.com'");
        SessionClient b = browser();
        assertThat(status(b.post("/api/v1/auth/login", Map.of("email", "root@example.com", "password", PASSWORD)))).isEqualTo(200);
        return b;
    }

    @Test
    void thePlatformAdministratorCreatesACompanyAndInvitesItsOwner() throws Exception {
        SessionClient admin = platformAdmin();
        sent.clear();
        MvcResult created = admin.post("/api/v1/platform/tenants", Map.of("id", co, "name", "Initech", "ownerEmail", "Boss@Example.com"));
        assertThat(status(created)).isEqualTo(201);
        // an INVITED OWNER membership for the normalized email, no account needed yet
        Map<String, Object> row = jdbc.queryForMap("select status, invited_email, user_id from company_membership where tenant_id = '" + co + "'");
        assertThat(row.get("status")).isEqualTo("INVITED");
        assertThat(row.get("invited_email")).isEqualTo("boss@example.com");
        assertThat(row.get("user_id")).isNull();
        assertThat(jdbc.queryForList("select role from company_membership_role where membership_id = (select id from company_membership where tenant_id = '" + co + "')", String.class))
                .containsExactly("OWNER");
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getTo()).containsExactly("boss@example.com");
        assertThat(sent.get(0).getText()).contains("/invitations/accept?token=").contains("Initech");
        // only a hash of the token is stored
        assertThat(jdbc.queryForObject("select token_hash from membership_invitation", String.class)).doesNotContain(lastToken()).hasSize(64);
        assertThat(jdbc.queryForObject("select count(*) from security_audit_event where event_type = 'MEMBERSHIP_INVITED' and tenant_id = '" + co + "'", Long.class)).isEqualTo(1L);
    }

    @Test
    void anInvalidOwnerEmailCreatesNoCompany() throws Exception {
        SessionClient admin = platformAdmin();
        sent.clear();
        MvcResult r = admin.post("/api/v1/platform/tenants", Map.of("id", co, "name", "Initech", "ownerEmail", "not-an-email"));
        assertThat(status(r)).isEqualTo(422);
        assertThat(jdbc.queryForObject("select count(*) from tenant where id = '" + co + "'", Long.class)).isZero();
        assertThat(sent).isEmpty();
    }

    @Test
    void thePlatformAdministratorHasNoCompany() throws Exception {
        SessionClient admin = platformAdmin();
        member("acme", "root@example.com", "ACTIVE", "OWNER");
        // tenant endpoints stay closed to it, with or without a company header
        assertThat(status(admin.inCompany("acme").get("/api/v1/questionnaires"))).isEqualTo(403);
        assertThat(status(admin.inCompany(null).get("/api/v1/questionnaires"))).isEqualTo(403);
        // and a company header is ignored on the platform endpoints
        assertThat(status(admin.inCompany("no-such-company").get("/api/v1/platform/tenants"))).isEqualTo(200);
    }

    @Test
    void aTenantUserCannotReachThePlatformEndpoints() throws Exception {
        SessionClient b = signedIn("ana@example.com");
        member("acme", "ana@example.com", "ACTIVE", "OWNER", "ADMIN");
        b.inCompany("acme");
        MvcResult list = b.get("/api/v1/platform/tenants");
        assertThat(status(list)).isEqualTo(403);
        assertThat(status(b.post("/api/v1/platform/tenants", Map.of("id", "evil", "name", "Evil")))).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from tenant where id = 'evil'", Long.class)).isZero();
    }

    @Test
    void theAdministratorFlagIsReadAtEachRequestNotFromTheSession() throws Exception {
        SessionClient admin = platformAdmin();
        assertThat(status(admin.get("/api/v1/platform/tenants"))).isEqualTo(200);
        jdbc.update("update app_user set platform_admin = false");
        assertThat(status(admin.get("/api/v1/platform/tenants"))).isEqualTo(403);
    }

    // ---------------------------------------------------------------- accepting an invitation

    private String inviteOwner(String tenant, String email) throws Exception {
        SessionClient admin = platformAdmin();
        sent.clear();
        assertThat(status(admin.post("/api/v1/platform/tenants", Map.of("id", tenant, "name", "Initech", "ownerEmail", email)))).isEqualTo(201);
        return lastToken();
    }

    @Test
    void theInvitedPersonAcceptsAndTheCompanyAppears() throws Exception {
        String token = inviteOwner(co, "boss@example.com");
        SessionClient boss = signedIn("boss@example.com");
        assertThat(boss.body(boss.get("/api/v1/companies"))).as("not a member before accepting").isEmpty();
        assertThat(status(boss.inCompany(co).get("/api/v1/questionnaires"))).isEqualTo(403);

        assertThat(status(boss.post("/api/v1/auth/invitations/accept", Map.of("token", token)))).isEqualTo(204);
        JsonNode companies = boss.body(boss.get("/api/v1/companies"));
        assertThat(companies).hasSize(1);
        assertThat(companies.get(0).get("id").asText()).isEqualTo(co);
        assertThat(companies.get(0).get("roles").toString()).contains("OWNER");
        assertThat(status(boss.inCompany(co).get("/api/v1/questionnaires"))).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from company_membership where tenant_id = '" + co + "'", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from security_audit_event where event_type = 'MEMBERSHIP_ACCEPTED' and outcome = 'SUCCESS'", Long.class)).isEqualTo(1L);

        // the link works once
        MvcResult again = boss.post("/api/v1/auth/invitations/accept", Map.of("token", token));
        assertThat(status(again)).isEqualTo(400);
        assertThat(code(boss.body(again))).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void anInvitationIsForTheInvitedEmailOnly() throws Exception {
        String token = inviteOwner(co, "boss@example.com");
        SessionClient other = signedIn("intruder@example.com");
        MvcResult r = other.post("/api/v1/auth/invitations/accept", Map.of("token", token));
        assertThat(status(r)).isEqualTo(400);
        assertThat(code(other.body(r))).isEqualTo("INVALID_TOKEN");
        assertThat(jdbc.queryForObject("select status from company_membership where tenant_id = '" + co + "'", String.class)).isEqualTo("INVITED");
        // the refusal did not use the link: the invited person can still accept
        SessionClient boss = signedIn("boss@example.com");
        assertThat(status(boss.post("/api/v1/auth/invitations/accept", Map.of("token", token)))).isEqualTo(204);
    }

    @Test
    void anExpiredOrInventedInvitationIsRefused() throws Exception {
        String token = inviteOwner(co, "boss@example.com");
        SessionClient boss = signedIn("boss@example.com");
        assertThat(status(boss.post("/api/v1/auth/invitations/accept", Map.of("token", "not-a-real-token")))).isEqualTo(400);
        // seven days pass (the expiry is moved back in the database: a session cannot outlive the test clock being moved by days)
        jdbc.update("update membership_invitation set expires_at = now() - interval '1 year'");
        assertThat(status(boss.post("/api/v1/auth/invitations/accept", Map.of("token", token)))).isEqualTo(400);
        assertThat(jdbc.queryForObject("select status from company_membership where tenant_id = '" + co + "'", String.class)).isEqualTo("INVITED");
    }

    @Test
    void acceptingNeedsASession() throws Exception {
        String token = inviteOwner(co, "boss@example.com");
        assertThat(status(browser().post("/api/v1/auth/invitations/accept", Map.of("token", token)))).isEqualTo(401);
    }

    // ---------------------------------------------------------------- the first platform administrator

    @Autowired
    org.springframework.beans.factory.ObjectProvider<BootstrapAdministrator> unused;

    @Autowired
    com.company.leadershipintelligence.repository.persistence.repository.UserStore users;

    @Autowired
    com.company.leadershipintelligence.repository.persistence.repository.AuthTokenStore tokens;

    @Autowired
    com.company.leadershipintelligence.service.identity.PasswordHasher hasher;

    @Autowired
    com.company.leadershipintelligence.service.identity.TokenCodec codec;

    @Autowired
    com.company.leadershipintelligence.service.identity.AccountMessages messages;

    @Autowired
    com.company.leadershipintelligence.service.identity.SecurityAudit audit;

    @Autowired
    org.springframework.transaction.support.TransactionTemplate transaction;

    private BootstrapAdministrator bootstrap(String email) {
        var p = new com.company.leadershipintelligence.config.properties.IdentityProperties(4, java.time.Duration.ofHours(24), java.time.Duration.ofHours(1),
                java.time.Duration.ofHours(8), "http://localhost:3000", "no-reply@localhost", false, "", java.time.Duration.ofDays(7), email);
        return new BootstrapAdministrator(users, tokens, hasher, codec, messages, audit, p, transaction, clock);
    }

    @Test
    void theBootstrapAdministratorIsCreatedOnceAndSetsItsPasswordFromALink() throws Exception {
        bootstrap("Root@Example.com").run(null);
        assertThat(jdbc.queryForMap("select status, platform_admin, email_verified_at is not null as verified from app_user where email = 'root@example.com'"))
                .containsEntry("status", "ACTIVE").containsEntry("platform_admin", true).containsEntry("verified", true);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getText()).contains("/reset-password?token=");
        // nobody can sign in with a guessed password, and the link sets a real one
        SessionClient b = browser();
        assertThat(status(b.post("/api/v1/auth/login", Map.of("email", "root@example.com", "password", PASSWORD)))).isEqualTo(401);
        assertThat(status(b.post("/api/v1/auth/password/reset", Map.of("token", lastToken(), "newPassword", "a brand new passphrase")))).isEqualTo(204);
        assertThat(status(b.post("/api/v1/auth/login", Map.of("email", "root@example.com", "password", "a brand new passphrase")))).isEqualTo(200);
        assertThat(status(b.get("/api/v1/platform/tenants"))).isEqualTo(200);

        // restarting changes nothing
        sent.clear();
        bootstrap("root@example.com").run(null);
        bootstrap("someone-else@example.com").run(null);
        assertThat(jdbc.queryForObject("select count(*) from app_user", Long.class)).isEqualTo(1L);
        assertThat(sent).isEmpty();
    }

    @Test
    void anExistingActiveAccountCanBecomeTheBootstrapAdministrator() throws Exception {
        signedIn("ana@example.com");
        sent.clear();
        bootstrap("ana@example.com").run(null);
        assertThat(jdbc.queryForObject("select platform_admin from app_user where email = 'ana@example.com'", Boolean.class)).isTrue();
        assertThat(sent).isEmpty();
    }

    @Test
    void nothingIsCreatedWithoutAConfiguredEmail() throws Exception {
        bootstrap("").run(null);
        assertThat(jdbc.queryForObject("select count(*) from app_user", Long.class)).isZero();
    }
}
