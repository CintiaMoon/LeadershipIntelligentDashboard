package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.support.AccountFlowTest;
import com.company.leadershipintelligence.support.SessionClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.company.leadershipintelligence.support.SessionClient.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Members and invitations end to end against a real PostgreSQL (decision D72): inviting, accepting or registering with the link, the
 * rules about who may manage whom, changes that apply at the next request, removing and inviting again, and the audit of it all.
 * Every call goes through the real security chain with a real session.
 */
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class MemberApiIntegrationTest extends AccountFlowTest {

    private SessionClient owner() throws Exception {
        SessionClient b = signedIn("owner@example.com");
        member("acme", "owner@example.com", "ACTIVE", "OWNER");
        return b.inCompany("acme");
    }

    private SessionClient admin() throws Exception {
        SessionClient b = signedIn("admin@example.com");
        member("acme", "admin@example.com", "ACTIVE", "ADMIN");
        return b.inCompany("acme");
    }

    private static Map<String, Object> invitation(String email, String... roles) {
        return Map.of("email", email, "roles", List.of(roles));
    }

    private JsonNode invite(SessionClient who, String email, String... roles) throws Exception {
        MvcResult r = who.post("/api/v1/members", invitation(email, roles));
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return who.body(r);
    }

    private String memberId(String email) {
        return jdbc.queryForObject("select m.id::text from company_membership m left join app_user u on u.id = m.user_id "
                + "where m.tenant_id = 'acme' and coalesce(u.email, m.invited_email) = ?", String.class, email);
    }

    private long events(String type) {
        return jdbc.queryForObject("select count(*) from security_audit_event where event_type = ?", Long.class, type);
    }

    // ---------------------------------------------------------------- inviting

    @Test
    void anOwnerInvitesAPersonWhoIsSentASingleUseLink() throws Exception {
        SessionClient owner = owner();
        sent.clear();
        JsonNode invited = invite(owner, "New.Person@Example.com", "ANALYST");
        assertThat(invited.get("status").asText()).isEqualTo("INVITED");
        assertThat(invited.get("email").asText()).isEqualTo("new.person@example.com");
        assertThat(invited.get("roles").toString()).isEqualTo("[\"ANALYST\"]");
        assertThat(invited.hasNonNull("displayName")).isFalse();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getTo()).containsExactly("new.person@example.com");
        assertThat(sent.get(0).getText()).contains("/invitations/accept?token=").contains("Acme Corp");
        // only a hash of the link is stored
        assertThat(jdbc.queryForObject("select token_hash from membership_invitation where membership_id = ?::uuid", String.class, invited.get("id").asText()))
                .hasSize(64).isNotEqualTo(lastToken());
        assertThat(events("MEMBERSHIP_INVITED")).isEqualTo(1L);
        // and the list shows the pending invitation
        JsonNode list = owner.body(owner.get("/api/v1/members"));
        assertThat(list).hasSize(2);
        assertThat(list.toString()).contains("new.person@example.com").contains("INVITED");
    }

    @Test
    void theInvitedPersonRegistersWithTheLinkAndIsAMemberAtOnce() throws Exception {
        SessionClient owner = owner();
        invite(owner, "new@example.com", "VIEWER");
        String token = lastToken();

        SessionClient person = browser();
        MvcResult preview = person.post("/api/v1/auth/invitations/preview", Map.of("token", token));
        assertThat(status(preview)).isEqualTo(200);
        JsonNode offer = person.body(preview);
        assertThat(offer.get("companyName").asText()).isEqualTo("Acme Corp");
        assertThat(offer.get("email").asText()).isEqualTo("new@example.com");
        assertThat(offer.get("accountExists").asBoolean()).isFalse();

        MvcResult weak = person.post("/api/v1/auth/invitations/register", Map.of("token", token, "password", "short", "displayName", "New Person"));
        assertThat(status(weak)).isEqualTo(422);
        assertThat(jdbc.queryForObject("select count(*) from app_user where email = 'new@example.com'", Long.class)).as("nothing was created").isZero();

        assertThat(status(person.post("/api/v1/auth/invitations/register", Map.of("token", token, "password", PASSWORD, "displayName", "New Person")))).isEqualTo(204);
        // the account is verified (the link proved the mailbox) and the membership is active: sign in and use the company
        assertThat(jdbc.queryForObject("select status || ':' || (email_verified_at is not null) from app_user where email = 'new@example.com'", String.class)).isEqualTo("ACTIVE:true");
        assertThat(status(person.post("/api/v1/auth/login", Map.of("email", "new@example.com", "password", PASSWORD)))).isEqualTo(200);
        JsonNode companies = person.body(person.get("/api/v1/companies"));
        assertThat(companies).hasSize(1);
        assertThat(companies.get(0).get("id").asText()).isEqualTo("acme");
        assertThat(companies.get(0).get("roles").toString()).contains("VIEWER");
        assertThat(status(person.inCompany("acme").get("/api/v1/assessments"))).isEqualTo(200);
        assertThat(events("MEMBERSHIP_ACCEPTED")).isEqualTo(1L);

        // the link works once
        MvcResult again = person.post("/api/v1/auth/invitations/register", Map.of("token", token, "password", PASSWORD, "displayName", "Again"));
        assertThat(status(again)).isEqualTo(400);
        assertThat(code(person.body(again))).isEqualTo("INVALID_TOKEN");
        assertThat(status(person.post("/api/v1/auth/invitations/preview", Map.of("token", token)))).isEqualTo(400);
    }

    @Test
    void anAddressThatAlreadyHasAnAccountSignsInAndAccepts() throws Exception {
        SessionClient owner = owner();
        SessionClient ana = signedIn("ana@example.com");
        sent.clear();
        invite(owner, "ana@example.com", "ANALYST");
        String token = lastToken();
        assertThat(ana.body(ana.post("/api/v1/auth/invitations/preview", Map.of("token", token))).get("accountExists").asBoolean()).isTrue();
        MvcResult register = browser().post("/api/v1/auth/invitations/register", Map.of("token", token, "password", PASSWORD, "displayName", "Ana"));
        assertThat(status(register)).isEqualTo(422);
        assertThat(jdbc.queryForObject("select status from company_membership where invited_email = 'ana@example.com'", String.class)).isEqualTo("INVITED");
        assertThat(status(ana.post("/api/v1/auth/invitations/accept", Map.of("token", token)))).isEqualTo(204);
        assertThat(status(ana.inCompany("acme").get("/api/v1/assessments"))).isEqualTo(200);
    }

    @Test
    void anInvitationLinkOfAnotherCompanyOrAnInventedOneOffersNothing() throws Exception {
        assertThat(status(browser().post("/api/v1/auth/invitations/preview", Map.of("token", "nope")))).isEqualTo(400);
        assertThat(status(browser().post("/api/v1/auth/invitations/register", Map.of("token", "nope", "password", PASSWORD, "displayName", "X")))).isEqualTo(400);
    }

    @Test
    void anAddressIsInvitedOnceAndAMemberIsNotInvitedAgain() throws Exception {
        SessionClient owner = owner();
        invite(owner, "new@example.com", "VIEWER");
        MvcResult twice = owner.post("/api/v1/members", invitation("new@example.com", "VIEWER"));
        assertThat(status(twice)).isEqualTo(409);
        assertThat(code(owner.body(twice))).isEqualTo("MEMBER_CONFLICT");
        MvcResult existing = owner.post("/api/v1/members", invitation("owner@example.com", "VIEWER"));
        assertThat(status(existing)).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from company_membership where tenant_id = 'acme'", Long.class)).isEqualTo(2L);
    }

    @Test
    void invitationsAreChecked() throws Exception {
        SessionClient owner = owner();
        for (Map<String, Object> bad : List.of(
                invitation("not-an-email", "VIEWER"),
                invitation("a@example.com", "NONSENSE"),
                invitation("a@example.com", "PLATFORM_ADMIN"),
                invitation("a@example.com", "RESPONDENT", "VIEWER"),
                invitation("a@example.com", "viewer"))) {
            MvcResult r = owner.post("/api/v1/members", bad);
            assertThat(status(r)).as(bad.toString()).isEqualTo(422);
            assertThat(code(owner.body(r))).isEqualTo("INVALID_MEMBER");
        }
        assertThat(status(owner.post("/api/v1/members", Map.of("email", "a@example.com", "roles", List.of())))).isEqualTo(400);
        assertThat(status(owner.post("/api/v1/members", Map.of("email", "a@example.com", "roles", List.of("VIEWER"), "tenantId", "globex")))).as("a company cannot be named").isEqualTo(400);
        assertThat(jdbc.queryForObject("select count(*) from company_membership where tenant_id = 'acme'", Long.class)).isEqualTo(1L);
    }

    // ---------------------------------------------------------------- who may do what

    @Test
    void anAdministratorManagesTheLowerRolesButNotOwnersOrAdministrators() throws Exception {
        SessionClient owner = owner();
        SessionClient admin = admin();
        for (String role : List.of("ENGINEERING_LEADER", "ANALYST", "VIEWER", "RESPONDENT")) {
            assertThat(status(admin.post("/api/v1/members", invitation("invitee-" + role.toLowerCase() + "@example.com", role)))).as(role).isEqualTo(201);
        }
        for (String role : List.of("OWNER", "ADMIN")) {
            MvcResult r = admin.post("/api/v1/members", invitation("x-" + role.toLowerCase() + "@example.com", role));
            assertThat(status(r)).as(role).isEqualTo(403);
            assertThat(code(admin.body(r))).isEqualTo("MEMBER_ACTION_REFUSED");
        }
        // it cannot change, suspend or remove an owner
        String ownerId = memberId("owner@example.com");
        assertThat(status(admin.patch("/api/v1/members/" + ownerId, Map.of("status", "SUSPENDED")))).isEqualTo(403);
        assertThat(status(admin.patch("/api/v1/members/" + ownerId, Map.of("roles", List.of("VIEWER"))))).isEqualTo(403);
        assertThat(status(admin.delete("/api/v1/members/" + ownerId))).isEqualTo(403);
        // an owner may grant an administrator, and then that administrator is out of the other administrators' reach
        JsonNode second = invite(owner, "admin2@example.com", "ADMIN");
        assertThat(status(admin.delete("/api/v1/members/" + second.get("id").asText()))).isEqualTo(403);
        // it cannot promote a lower member to administrator
        String viewerId = memberId("invitee-viewer@example.com");
        assertThat(status(admin.patch("/api/v1/members/" + viewerId, Map.of("roles", List.of("ADMIN"))))).isEqualTo(403);
        assertThat(status(admin.patch("/api/v1/members/" + viewerId, Map.of("roles", List.of("ANALYST"))))).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from company_membership where id = ?::uuid", String.class, ownerId)).isEqualTo("ACTIVE");
    }

    @Test
    void anOwnerGrantsEveryRole() throws Exception {
        SessionClient owner = owner();
        for (String role : List.of("OWNER", "ADMIN", "ENGINEERING_LEADER", "ANALYST", "VIEWER", "RESPONDENT")) {
            assertThat(status(owner.post("/api/v1/members", invitation("invitee-" + role.toLowerCase() + "@example.com", role)))).as(role).isEqualTo(201);
        }
    }

    @Test
    void everyoneElseIsRefusedTheMemberEndpoints() throws Exception {
        owner();
        for (String role : List.of("ENGINEERING_LEADER", "ANALYST", "VIEWER", "RESPONDENT")) {
            String email = role.toLowerCase().replace('_', '-') + "@example.com";
            SessionClient b = signedIn(email);
            member("acme", email, "ACTIVE", role);
            b.inCompany("acme");
            assertThat(status(b.get("/api/v1/members"))).as(role).isEqualTo(403);
            assertThat(status(b.post("/api/v1/members", invitation("x@example.com", "VIEWER")))).as(role).isEqualTo(403);
        }
    }

    @Test
    void nobodyChangesTheirOwnMembership() throws Exception {
        SessionClient owner = owner();
        // a second owner so that the last-owner rule is not what refuses
        invite(owner, "second@example.com", "OWNER");
        String own = memberId("owner@example.com");
        for (MvcResult r : List.of(owner.patch("/api/v1/members/" + own, Map.of("roles", List.of("VIEWER"))),
                owner.patch("/api/v1/members/" + own, Map.of("status", "SUSPENDED")), owner.delete("/api/v1/members/" + own))) {
            assertThat(status(r)).isEqualTo(403);
            assertThat(code(owner.body(r))).isEqualTo("MEMBER_ACTION_REFUSED");
        }
        assertThat(jdbc.queryForObject("select status from company_membership where id = ?::uuid", String.class, own)).isEqualTo("ACTIVE");
    }

    @Test
    void anActorLimitedToOrganizationsOnlyGrantsAndManagesWithinThem() throws Exception {
        SessionClient owner = owner();
        SessionClient admin = signedIn("admin@example.com");
        restrictTo(member("acme", "admin@example.com", "ACTIVE", "ADMIN"), "org-1");
        admin.inCompany("acme");
        assertThat(status(admin.post("/api/v1/members", invitation("a@example.com", "VIEWER")))).as("no list would mean every organization").isEqualTo(403);
        assertThat(status(admin.post("/api/v1/members", Map.of("email", "b@example.com", "roles", List.of("VIEWER"), "organizationIds", List.of("org-2"))))).isEqualTo(403);
        assertThat(status(admin.post("/api/v1/members", Map.of("email", "c@example.com", "roles", List.of("VIEWER"), "organizationIds", List.of("org-1"))))).isEqualTo(201);
        // a member who reaches every organization is outside the reach of an administrator who does not
        JsonNode wide = invite(owner, "wide@example.com", "VIEWER");
        assertThat(status(admin.delete("/api/v1/members/" + wide.get("id").asText()))).isEqualTo(403);
        assertThat(status(admin.patch("/api/v1/members/" + memberId("c@example.com"), Map.of("organizationIds", List.of())))).as("cannot widen").isEqualTo(403);
    }

    @Test
    void anApiTokenCannotManageMembers() throws Exception {
        SessionClient owner = owner();
        String secret = createToken(owner, "acme", Map.of("name", "ci", "role", "ADMIN")).get("secret").asText();
        assertThat(status(bearerGet(secret, "/api/v1/members"))).isEqualTo(403);
        assertThat(status(bearerPost(secret, "/api/v1/members", invitation("x@example.com", "VIEWER")))).isEqualTo(403);
    }

    // ---------------------------------------------------------------- changes apply at the next request

    @Test
    void changingRolesSuspendingAndRemovingTakeEffectAtTheNextRequest() throws Exception {
        SessionClient owner = owner();
        SessionClient ana = signedIn("ana@example.com");
        member("acme", "ana@example.com", "ACTIVE", "ADMIN");
        ana.inCompany("acme");
        String anaId = memberId("ana@example.com");
        assertThat(status(ana.get("/api/v1/questionnaires"))).isEqualTo(200);

        // a lower role
        MvcResult demoted = owner.patch("/api/v1/members/" + anaId, Map.of("roles", List.of("VIEWER")));
        assertThat(status(demoted)).isEqualTo(200);
        assertThat(owner.body(demoted).get("roles").toString()).isEqualTo("[\"VIEWER\"]");
        assertThat(status(ana.get("/api/v1/questionnaires"))).isEqualTo(403);
        assertThat(status(ana.get("/api/v1/assessments"))).isEqualTo(200);

        // suspended, then active again
        assertThat(status(owner.patch("/api/v1/members/" + anaId, Map.of("status", "SUSPENDED")))).isEqualTo(200);
        MvcResult blocked = ana.get("/api/v1/assessments");
        assertThat(status(blocked)).isEqualTo(403);
        assertThat(code(ana.body(blocked))).isEqualTo("COMPANY_ACCESS_DENIED");
        assertThat(ana.body(ana.get("/api/v1/companies"))).as("a suspended company is not offered").isEmpty();
        assertThat(status(owner.patch("/api/v1/members/" + anaId, Map.of("status", "ACTIVE")))).isEqualTo(200);
        assertThat(status(ana.get("/api/v1/assessments"))).isEqualTo(200);

        // limited to an organization
        assertThat(status(owner.patch("/api/v1/members/" + anaId, Map.of("organizationIds", List.of("org-1"))))).isEqualTo(200);
        assertThat(status(ana.get("/api/v1/organizations/org-2/dashboard"))).isEqualTo(403);

        // removed
        assertThat(status(owner.delete("/api/v1/members/" + anaId))).isEqualTo(204);
        assertThat(status(ana.get("/api/v1/assessments"))).isEqualTo(403);
        assertThat(owner.body(owner.get("/api/v1/members")).toString()).as("the removed are not listed").doesNotContain("ana@example.com");
        assertThat(jdbc.queryForObject("select status from company_membership where id = ?::uuid", String.class, anaId)).as("the row is kept").isEqualTo("REMOVED");

        assertThat(events("MEMBERSHIP_ROLE_CHANGED")).isEqualTo(1L);
        assertThat(events("MEMBERSHIP_SUSPENDED")).isEqualTo(1L);
        assertThat(events("MEMBERSHIP_REACTIVATED")).isEqualTo(1L);
        assertThat(events("MEMBERSHIP_SCOPE_CHANGED")).isEqualTo(1L);
        assertThat(events("MEMBERSHIP_REMOVED")).isEqualTo(1L);
        assertThat(jdbc.queryForList("select detail::text from security_audit_event where event_type = 'MEMBERSHIP_ROLE_CHANGED'", String.class).toString())
                .contains("ADMIN").contains("VIEWER");
    }

    @Test
    void removingAMemberEndsTheApiTokensTheyCreated() throws Exception {
        SessionClient owner = owner();
        SessionClient ana = signedIn("ana@example.com");
        member("acme", "ana@example.com", "ACTIVE", "ADMIN");
        String secret = createToken(ana, "acme", Map.of("name", "ana's", "role", "VIEWER")).get("secret").asText();
        assertThat(status(bearerGet(secret, "/api/v1/assessments"))).isEqualTo(200);
        assertThat(status(owner.delete("/api/v1/members/" + memberId("ana@example.com")))).isEqualTo(204);
        assertThat(status(bearerGet(secret, "/api/v1/assessments"))).isEqualTo(401);
    }

    @Test
    void aRemovedMemberCanBeInvitedAgainOnTheSameMembership() throws Exception {
        SessionClient owner = owner();
        SessionClient ana = signedIn("ana@example.com");
        member("acme", "ana@example.com", "ACTIVE", "VIEWER");
        String id = memberId("ana@example.com");
        owner.delete("/api/v1/members/" + id);
        sent.clear();
        JsonNode again = invite(owner, "ana@example.com", "ANALYST");
        assertThat(again.get("id").asText()).as("the history of the membership stays").isEqualTo(id);
        assertThat(again.get("status").asText()).isEqualTo("INVITED");
        assertThat(status(ana.inCompany("acme").get("/api/v1/assessments"))).as("not a member until she accepts").isEqualTo(403);
        assertThat(status(ana.post("/api/v1/auth/invitations/accept", Map.of("token", lastToken())))).isEqualTo(204);
        assertThat(status(ana.get("/api/v1/assessments"))).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from company_membership where tenant_id = 'acme'", Long.class)).isEqualTo(2L);
    }

    @Test
    void removingAnInvitationWithdrawsItsLink() throws Exception {
        SessionClient owner = owner();
        invite(owner, "new@example.com", "VIEWER");
        String token = lastToken();
        assertThat(status(owner.delete("/api/v1/members/" + memberId("new@example.com")))).isEqualTo(204);
        assertThat(status(browser().post("/api/v1/auth/invitations/preview", Map.of("token", token)))).isEqualTo(400);
    }

    @Test
    void changesAreChecked() throws Exception {
        SessionClient owner = owner();
        JsonNode viewer = invite(owner, "v@example.com", "VIEWER");
        String id = viewer.get("id").asText();
        assertThat(status(owner.patch("/api/v1/members/" + id, Map.of()))).as("nothing to change").isEqualTo(422);
        assertThat(status(owner.patch("/api/v1/members/" + id, Map.of("roles", List.of("RESPONDENT", "VIEWER"))))).isEqualTo(422);
        assertThat(status(owner.patch("/api/v1/members/" + id, Map.of("status", "SUSPENDED")))).as("an invitation has no status").isEqualTo(422);
        assertThat(status(owner.patch("/api/v1/members/" + UUID.randomUUID(), Map.of("roles", List.of("VIEWER"))))).isEqualTo(404);
        assertThat(status(owner.delete("/api/v1/members/" + UUID.randomUUID()))).isEqualTo(404);
        assertThat(status(owner.patch("/api/v1/members/" + id, Map.of("unknown", "x")))).isEqualTo(400);
    }

    @Test
    void aMemberOfAnotherCompanyIsNotFound() throws Exception {
        SessionClient owner = owner();
        SessionClient other = signedIn("boss@example.com");
        member("globex", "boss@example.com", "ACTIVE", "OWNER");
        other.inCompany("globex");
        JsonNode theirs = invite(other, "g@example.com", "VIEWER");
        assertThat(status(owner.patch("/api/v1/members/" + theirs.get("id").asText(), Map.of("roles", List.of("ANALYST"))))).isEqualTo(404);
        assertThat(status(owner.delete("/api/v1/members/" + theirs.get("id").asText()))).isEqualTo(404);
        assertThat(status(owner.post("/api/v1/members/" + theirs.get("id").asText() + "/resend-invitation", null))).isEqualTo(404);
        assertThat(owner.body(owner.get("/api/v1/members")).toString()).doesNotContain("g@example.com");
    }

    // ---------------------------------------------------------------- resending

    @Test
    void resendingGivesANewLinkAndRetiresTheOldOne() throws Exception {
        SessionClient owner = owner();
        JsonNode invited = invite(owner, "new@example.com", "VIEWER");
        String first = lastToken();
        sent.clear();
        assertThat(status(owner.post("/api/v1/members/" + invited.get("id").asText() + "/resend-invitation", null))).isEqualTo(204);
        String second = lastToken();
        assertThat(second).isNotEqualTo(first);
        assertThat(status(browser().post("/api/v1/auth/invitations/preview", Map.of("token", first)))).as("the old link is retired").isEqualTo(400);
        assertThat(status(browser().post("/api/v1/auth/invitations/preview", Map.of("token", second)))).isEqualTo(200);
        assertThat(events("MEMBERSHIP_INVITATION_RESENT")).isEqualTo(1L);
        // only a pending invitation can be sent again
        String ownerId = memberId("owner@example.com");
        assertThat(status(owner.post("/api/v1/members/" + ownerId + "/resend-invitation", null))).isIn(403, 409);
    }

    @Test
    void anActiveMemberHasNothingToResend() throws Exception {
        SessionClient owner = owner();
        signedIn("ana@example.com");
        member("acme", "ana@example.com", "ACTIVE", "VIEWER");
        MvcResult r = owner.post("/api/v1/members/" + memberId("ana@example.com") + "/resend-invitation", null);
        assertThat(status(r)).isEqualTo(409);
        assertThat(code(owner.body(r))).isEqualTo("MEMBER_CONFLICT");
    }

    // ---------------------------------------------------------------- respondents from the roster

    private String person(String organization, String email, boolean active) {
        String team = "team-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("insert into organization (id, tenant_id, name) values (?, 'acme', 'O') on conflict do nothing", organization);
        jdbc.update("insert into team (id, tenant_id, organization_id, name) values (?, 'acme', ?, 'T')", team, organization);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into person (id, tenant_id, team_id, name, email, active) values (?, 'acme', ?, 'Pat', ?, ?)", id, team, email, active);
        return id.toString();
    }

    @Test
    void aRosterPersonIsInvitedAsARespondentAndNothingElse() throws Exception {
        SessionClient owner = owner();
        String person = person("org-r-" + UUID.randomUUID().toString().substring(0, 6), "pat@example.com", true);
        sent.clear();
        MvcResult r = owner.post("/api/v1/people/" + person + "/invite", null);
        assertThat(status(r)).isEqualTo(201);
        JsonNode invited = owner.body(r);
        assertThat(invited.get("email").asText()).isEqualTo("pat@example.com");
        assertThat(invited.get("roles").toString()).isEqualTo("[\"RESPONDENT\"]");
        assertThat(sent.get(0).getTo()).containsExactly("pat@example.com");
        // she registers with the link and reaches the respondent flow only
        SessionClient pat = browser();
        assertThat(status(pat.post("/api/v1/auth/invitations/register", Map.of("token", lastToken(), "password", PASSWORD, "displayName", "Pat")))).isEqualTo(204);
        assertThat(status(pat.post("/api/v1/auth/login", Map.of("email", "pat@example.com", "password", PASSWORD)))).isEqualTo(200);
        pat.inCompany("acme");
        assertThat(status(pat.get("/api/v1/respond/runs"))).isEqualTo(200);
        assertThat(status(pat.get("/api/v1/assessments"))).isEqualTo(403);
        assertThat(status(pat.get("/api/v1/members"))).isEqualTo(403);
    }

    @Test
    void anInactiveOrUnknownRosterPersonCannotBeInvitedAndAnOrganizationScopeApplies() throws Exception {
        SessionClient owner = owner();
        assertThat(status(owner.post("/api/v1/people/" + person("org-i-" + UUID.randomUUID().toString().substring(0, 6), "gone@example.com", false) + "/invite", null))).isEqualTo(422);
        assertThat(status(owner.post("/api/v1/people/" + UUID.randomUUID() + "/invite", null))).isEqualTo(404);
        // an administrator limited to one organization cannot reach a person of another
        SessionClient admin = signedIn("admin@example.com");
        restrictTo(member("acme", "admin@example.com", "ACTIVE", "ADMIN"), "org-mine");
        String other = person("org-other-" + UUID.randomUUID().toString().substring(0, 6), "other@example.com", true);
        assertThat(status(admin.inCompany("acme").post("/api/v1/people/" + other + "/invite", null))).isEqualTo(403);
        // a person of its own organization it can invite
        String mine = person("org-mine", "mine-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", true);
        MvcResult ok = admin.post("/api/v1/people/" + mine + "/invite", null);
        assertThat(status(ok)).isEqualTo(201);
        assertThat(admin.body(ok).get("organizationIds").toString()).isEqualTo("[\"org-mine\"]");
    }
}
