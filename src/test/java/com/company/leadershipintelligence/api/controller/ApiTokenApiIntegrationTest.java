package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.support.AccountFlowTest;
import com.company.leadershipintelligence.support.SessionClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.company.leadershipintelligence.support.SessionClient.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * API tokens end to end against a real PostgreSQL (decision D70): creating one (the secret once, only a hash stored), calling the API
 * with it (no company header, no CSRF, the company and role of the token), the limits on who may create what, and every way a token
 * stops working at the next request. Every call goes through the real security chain.
 */
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class ApiTokenApiIntegrationTest extends AccountFlowTest {

    private SessionClient owner() throws Exception {
        SessionClient b = signedIn("owner@example.com");
        member("acme", "owner@example.com", "ACTIVE", "OWNER");
        return b;
    }

    private static Map<String, Object> request(String name, String role) {
        return Map.of("name", name, "role", role);
    }

    // ---------------------------------------------------------------- creating and listing

    @Test
    void theSecretIsShownOnceAndOnlyItsHashIsStored() throws Exception {
        SessionClient owner = owner();
        JsonNode created = createToken(owner, "acme", request("CI pipeline", "ANALYST"));
        String secret = created.get("secret").asText();
        assertThat(secret).startsWith("lis_").hasSizeGreaterThan(40);
        JsonNode token = created.get("token");
        assertThat(token.get("role").asText()).isEqualTo("ANALYST");
        assertThat(token.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(token.get("prefix").asText()).isEqualTo(secret.substring(0, 12));
        assertThat(created.toString().split(java.util.regex.Pattern.quote(secret), -1)).as("the secret appears once in the answer").hasSize(2);

        // the list never shows the secret, and the database holds only a hash
        MvcResult list = owner.get("/api/v1/api-tokens");
        assertThat(status(list)).isEqualTo(200);
        assertThat(list.getResponse().getContentAsString()).doesNotContain(secret).doesNotContain("secret").contains("CI pipeline");
        String stored = jdbc.queryForObject("select token_hash from api_token", String.class);
        assertThat(stored).hasSize(64).isNotEqualTo(secret).doesNotContain(secret);
        assertThat(jdbc.queryForObject("select (t.*)::text from api_token t", String.class)).doesNotContain(secret);
        assertThat(jdbc.queryForObject("select count(*) from security_audit_event where event_type = 'API_TOKEN_CREATED'", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForList("select (e.*)::text from security_audit_event e", String.class).toString()).doesNotContain(secret);
    }

    @Test
    void onlyOwnersAndAdministratorsMayManageTokens() throws Exception {
        for (String role : List.of("ENGINEERING_LEADER", "ANALYST", "VIEWER", "RESPONDENT")) {
            String email = role.toLowerCase().replace('_', '-') + "@example.com";
            SessionClient b = signedIn(email);
            member("acme", email, "ACTIVE", role);
            assertThat(status(b.inCompany("acme").get("/api/v1/api-tokens"))).as(role).isEqualTo(403);
            assertThat(status(b.post("/api/v1/api-tokens", request("x", "VIEWER")))).as(role).isEqualTo(403);
        }
        assertThat(jdbc.queryForObject("select count(*) from api_token", Long.class)).isZero();
    }

    @Test
    void aTokenCanNeverBeOwnerOrStrongerThanItsCreator() throws Exception {
        SessionClient owner = owner();
        for (String role : List.of("OWNER", "RESPONDENT", "PLATFORM_ADMIN", "NONSENSE")) {
            MvcResult r = owner.inCompany("acme").post("/api/v1/api-tokens", request("x", role));
            assertThat(status(r)).as(role).isEqualTo(422);
            assertThat(code(owner.body(r))).isEqualTo("INVALID_API_TOKEN");
        }
        SessionClient admin = signedIn("admin@example.com");
        member("acme", "admin@example.com", "ACTIVE", "ADMIN");
        assertThat(status(admin.inCompany("acme").post("/api/v1/api-tokens", request("same level", "ADMIN")))).isEqualTo(201);
        assertThat(status(admin.post("/api/v1/api-tokens", request("lower", "VIEWER")))).isEqualTo(201);
        assertThat(status(admin.post("/api/v1/api-tokens", request("owner", "OWNER")))).isEqualTo(422);
        assertThat(jdbc.queryForObject("select count(*) from api_token where role = 'OWNER'", Long.class)).isZero();
    }

    @Test
    void aTokenCannotReachOrganizationsItsCreatorCannotReach() throws Exception {
        SessionClient admin = signedIn("admin@example.com");
        UUID membership = member("acme", "admin@example.com", "ACTIVE", "ADMIN");
        restrictTo(membership, "org-1");
        admin.inCompany("acme");
        assertThat(status(admin.post("/api/v1/api-tokens", request("everything", "VIEWER")))).as("no list means every organization").isEqualTo(422);
        assertThat(status(admin.post("/api/v1/api-tokens", Map.of("name", "other", "role", "VIEWER", "organizationIds", List.of("org-2"))))).isEqualTo(422);
        assertThat(status(admin.post("/api/v1/api-tokens", Map.of("name", "mixed", "role", "VIEWER", "organizationIds", List.of("org-1", "org-2"))))).isEqualTo(422);
        JsonNode ok = createToken(admin, "acme", Map.of("name", "mine", "role", "VIEWER", "organizationIds", List.of("org-1")));
        assertThat(ok.get("token").get("organizationIds")).hasSize(1);
    }

    @Test
    void theNameTheExpiryAndTheNumberOfTokensAreChecked() throws Exception {
        SessionClient owner = owner();
        owner.inCompany("acme");
        assertThat(status(owner.post("/api/v1/api-tokens", request("   ", "VIEWER")))).isEqualTo(400);
        assertThat(status(owner.post("/api/v1/api-tokens", request("x".repeat(101), "VIEWER")))).isEqualTo(400);
        assertThat(status(owner.post("/api/v1/api-tokens", Map.of("name", "past", "role", "VIEWER", "expiresAt", "2020-01-01T00:00:00Z")))).isEqualTo(422);
        assertThat(status(owner.post("/api/v1/api-tokens", Map.of("name", "future", "role", "VIEWER", "expiresAt", Instant.now().plusSeconds(86400).toString())))).isEqualTo(201);
        assertThat(status(owner.post("/api/v1/api-tokens", Map.of("name", "extra", "role", "VIEWER", "secret", "mine")))).as("unknown fields are refused").isEqualTo(400);
        for (int i = 1; i < 50; i++) {
            assertThat(status(owner.post("/api/v1/api-tokens", request("t" + i, "VIEWER")))).isEqualTo(201);
        }
        MvcResult tooMany = owner.post("/api/v1/api-tokens", request("one too many", "VIEWER"));
        assertThat(status(tooMany)).isEqualTo(422);
        assertThat(code(owner.body(tooMany))).isEqualTo("INVALID_API_TOKEN");
    }

    @Test
    void aCompanySeesOnlyItsOwnTokens() throws Exception {
        SessionClient acme = owner();
        SessionClient globex = signedIn("boss@example.com");
        member("globex", "boss@example.com", "ACTIVE", "OWNER");
        createToken(acme, "acme", request("acme token", "VIEWER"));
        JsonNode theirs = createToken(globex, "globex", request("globex token", "VIEWER"));
        JsonNode list = acme.body(acme.get("/api/v1/api-tokens"));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("name").asText()).isEqualTo("acme token");
        // another company's token cannot be revoked, and is not even found
        UUID globexId = UUID.fromString(theirs.get("token").get("id").asText());
        MvcResult r = mvcDelete(acme, "/api/v1/api-tokens/" + globexId);
        assertThat(status(r)).isEqualTo(404);
        assertThat(jdbc.queryForObject("select revoked_at is null from api_token where id = ?", Boolean.class, globexId)).isTrue();
    }

    private MvcResult mvcDelete(SessionClient client, String path) throws Exception {
        return client.delete(path);
    }

    // ---------------------------------------------------------------- calling the API with a token

    @Test
    void aTokenReachesItsCompanyWithItsRoleAndNeedsNoCompanyHeaderOrCsrfToken() throws Exception {
        SessionClient owner = owner();
        String admin = createToken(owner, "acme", request("admin token", "ADMIN")).get("secret").asText();
        String viewer = createToken(owner, "acme", request("viewer token", "VIEWER")).get("secret").asText();
        // the library of questionnaires is for owners and administrators
        assertThat(status(bearerGet(admin, "/api/v1/questionnaires"))).isEqualTo(200);
        MvcResult denied = bearerGet(viewer, "/api/v1/questionnaires");
        assertThat(status(denied)).isEqualTo(403);
        assertThat(code(body(denied))).isEqualTo("ACCESS_DENIED");
        // a state-changing call needs no CSRF token: it is not an ambient credential
        MvcResult write = bearerPost(admin, "/api/v1/teams", Map.of("organizationId", "no-such-org", "name", "T"));
        assertThat(status(write)).as("a token without an organization list may name a new organization").isEqualTo(201);
    }

    @Test
    void aTokenIsLockedToItsCompanyWhateverTheCallerSends() throws Exception {
        SessionClient acmeOwner = owner();
        SessionClient globexOwner = signedIn("boss@example.com");
        member("globex", "boss@example.com", "ACTIVE", "OWNER");
        String acme = createToken(acmeOwner, "acme", request("acme", "ADMIN")).get("secret").asText();
        jdbc.update("insert into organization (id, tenant_id, name) values ('org-acme-" + suffix() + "', 'acme', 'A')");
        jdbc.update("insert into organization (id, tenant_id, name) values ('org-globex-" + suffix() + "', 'globex', 'G')");
        jdbc.update("insert into team (id, tenant_id, organization_id, name) values ('team-acme-" + suffix() + "', 'acme', 'org-acme-" + suffix() + "', 'Acme team')");
        jdbc.update("insert into team (id, tenant_id, organization_id, name) values ('team-globex-" + suffix() + "', 'globex', 'org-globex-" + suffix() + "', 'Globex team')");
        MvcResult teams = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/teams?tenantId=globex")
                .header("Authorization", "Bearer " + acme).header("X-Company-Id", "globex")).andReturn();
        assertThat(status(teams)).isEqualTo(200);
        assertThat(teams.getResponse().getContentAsString()).contains("Acme team").doesNotContain("Globex team");
        assertThat(globexOwner.inCompany("globex").get("/api/v1/teams").getResponse().getContentAsString()).contains("Globex team").doesNotContain("Acme team");
    }

    @Test
    void aTokenIsLimitedToItsOrganizations() throws Exception {
        SessionClient owner = owner();
        String limited = createToken(owner, "acme", Map.of("name", "one org", "role", "VIEWER", "organizationIds", List.of("org-1"))).get("secret").asText();
        MvcResult other = bearerGet(limited, "/api/v1/organizations/org-2/dashboard");
        assertThat(status(other)).isEqualTo(403);
        assertThat(code(body(other))).isEqualTo("ORGANIZATION_ACCESS_DENIED");
        assertThat(status(bearerGet(limited, "/api/v1/organizations/org-1/dashboard"))).isNotEqualTo(403);
    }

    @Test
    void aTokenCannotManageTokensOrUseSessionOnlyEndpoints() throws Exception {
        SessionClient owner = owner();
        // an ADMIN token passes the role rule, but only a signed-in person may manage tokens
        String admin = createToken(owner, "acme", request("admin", "ADMIN")).get("secret").asText();
        assertThat(status(bearerGet(admin, "/api/v1/api-tokens"))).isEqualTo(403);
        assertThat(status(bearerPost(admin, "/api/v1/api-tokens", request("child", "VIEWER")))).isEqualTo(403);
        assertThat(status(bearerGet(admin, "/api/v1/companies"))).isEqualTo(401);
        assertThat(status(bearerGet(admin, "/api/v1/platform/tenants"))).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from api_token", Long.class)).isEqualTo(1L);
    }

    // ---------------------------------------------------------------- when a token stops working

    @Test
    void revokingATokenStopsItAtOnce() throws Exception {
        SessionClient owner = owner();
        JsonNode created = createToken(owner, "acme", request("temp", "ADMIN"));
        String secret = created.get("secret").asText();
        String id = created.get("token").get("id").asText();
        assertThat(status(bearerGet(secret, "/api/v1/questionnaires"))).isEqualTo(200);
        assertThat(status(owner.delete("/api/v1/api-tokens/" + id))).isEqualTo(204);
        MvcResult after = bearerGet(secret, "/api/v1/questionnaires");
        assertThat(status(after)).isEqualTo(401);
        assertThat(code(body(after))).isEqualTo("UNAUTHENTICATED");
        // revoking again is not an error; the list says REVOKED
        assertThat(status(owner.delete("/api/v1/api-tokens/" + id))).isEqualTo(204);
        assertThat(owner.body(owner.get("/api/v1/api-tokens")).get(0).get("status").asText()).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("select count(*) from security_audit_event where event_type = 'API_TOKEN_REVOKED'", Long.class)).isEqualTo(1L);
    }

    @Test
    void anExpiredTokenStopsWorking() throws Exception {
        SessionClient owner = owner();
        String secret = createToken(owner, "acme", Map.of("name", "short", "role", "ADMIN", "expiresAt", Instant.now().plusSeconds(3600).toString())).get("secret").asText();
        assertThat(status(bearerGet(secret, "/api/v1/questionnaires"))).isEqualTo(200);
        // moved back in the database: the application clock of the tests is fixed in the past
        jdbc.update("update api_token set expires_at = '2020-01-01T00:00:00Z'");
        assertThat(status(bearerGet(secret, "/api/v1/questionnaires"))).isEqualTo(401);
        assertThat(owner.body(owner.get("/api/v1/api-tokens")).get(0).get("status").asText()).isEqualTo("EXPIRED");
    }

    @Test
    void aTokenStopsWhenItsCreatorLosesTheCompanyOrTheAccountOrTheAccessItHad() throws Exception {
        SessionClient owner = owner();
        UUID membership = jdbc.queryForObject("select id from company_membership", UUID.class);
        String secret = createToken(owner, "acme", request("t", "ADMIN")).get("secret").asText();
        assertThat(status(bearerGet(secret, "/api/v1/questionnaires"))).isEqualTo(200);

        jdbc.update("update company_membership set status = 'SUSPENDED' where id = ?", membership);
        assertThat(status(bearerGet(secret, "/api/v1/questionnaires"))).as("membership suspended").isEqualTo(401);
        jdbc.update("update company_membership set status = 'ACTIVE' where id = ?", membership);
        assertThat(status(bearerGet(secret, "/api/v1/questionnaires"))).as("and back").isEqualTo(200);

        // the creator is left with less than the token has
        jdbc.update("delete from company_membership_role where membership_id = ?", membership);
        jdbc.update("insert into company_membership_role (membership_id, role) values (?, 'ANALYST')", membership);
        assertThat(status(bearerGet(secret, "/api/v1/questionnaires"))).as("creator demoted below the token").isEqualTo(401);
        jdbc.update("delete from company_membership_role where membership_id = ?", membership);
        jdbc.update("insert into company_membership_role (membership_id, role) values (?, 'OWNER')", membership);
        assertThat(status(bearerGet(secret, "/api/v1/questionnaires"))).isEqualTo(200);

        jdbc.update("update app_user set status = 'DISABLED'");
        assertThat(status(bearerGet(secret, "/api/v1/questionnaires"))).as("account disabled").isEqualTo(401);
        assertThat(jdbc.queryForList("select detail::text from security_audit_event where event_type = 'API_TOKEN_REJECTED'", String.class).toString())
                .contains("CREATOR_NOT_MEMBER").contains("CREATOR_ROLE_LOWER").contains("CREATOR_DISABLED");
    }

    @Test
    void aCreatorWhoseReachNarrowedStopsTheirWiderToken() throws Exception {
        SessionClient admin = signedIn("admin@example.com");
        UUID membership = member("acme", "admin@example.com", "ACTIVE", "ADMIN");
        String secret = createToken(admin, "acme", request("wide", "VIEWER")).get("secret").asText();
        assertThat(status(bearerGet(secret, "/api/v1/organizations/org-1/dashboard"))).isNotEqualTo(401);
        restrictTo(membership, "org-1");
        assertThat(status(bearerGet(secret, "/api/v1/organizations/org-1/dashboard"))).isEqualTo(401);
    }

    @Test
    void anInventedOrMalformedTokenIsAPlain401() throws Exception {
        assertThat(status(bearerGet("lis_" + "a".repeat(43), "/api/v1/questionnaires"))).isEqualTo(401);
        assertThat(status(bearerGet("lis_", "/api/v1/questionnaires"))).isEqualTo(401);
        // a bearer value of the other kind still goes to the external-token path, which has no key here and refuses it
        assertThat(status(bearerGet("not-an-api-token", "/api/v1/questionnaires"))).isEqualTo(401);
        MvcResult r = bearerGet("lis_" + "b".repeat(43), "/api/v1/questionnaires");
        assertThat(code(body(r))).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void aTokenWinsOverASessionCookieOnTheSameRequest() throws Exception {
        SessionClient owner = owner();
        String viewer = createToken(owner, "acme", request("viewer", "VIEWER")).get("secret").asText();
        // the owner's cookie would allow this; the token on the request is what counts
        MvcResult r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/questionnaires")
                .cookie(owner.cookies().values().toArray(new jakarta.servlet.http.Cookie[0]))
                .header("X-Company-Id", "acme").header("Authorization", "Bearer " + viewer)).andReturn();
        assertThat(status(r)).isEqualTo(403);
    }

    // ---------------------------------------------------------------- audit and last use

    @Test
    void theFirstUseOfEachDayIsAuditedAndTheLastUseIsKept() throws Exception {
        SessionClient owner = owner();
        JsonNode created = createToken(owner, "acme", request("audited", "ADMIN"));
        String secret = created.get("secret").asText();
        assertThat(created.get("token").hasNonNull("lastUsedAt")).isFalse();
        for (int i = 0; i < 3; i++) {
            assertThat(status(bearerGet(secret, "/api/v1/questionnaires"))).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("select count(*) from security_audit_event where event_type = 'API_TOKEN_USED'", Long.class)).as("once a day, not per call").isEqualTo(1L);
        assertThat(owner.body(owner.get("/api/v1/api-tokens")).get(0).hasNonNull("lastUsedAt")).isTrue();
        // a use the next day is noted again
        jdbc.update("update api_token set last_used_at = last_used_at - interval '2 days'");
        bearerGet(secret, "/api/v1/questionnaires");
        assertThat(jdbc.queryForObject("select count(*) from security_audit_event where event_type = 'API_TOKEN_USED'", Long.class)).isEqualTo(2L);
    }

    private static final String SUFFIX = UUID.randomUUID().toString().substring(0, 8);

    private String suffix() {
        return SUFFIX;
    }
}
