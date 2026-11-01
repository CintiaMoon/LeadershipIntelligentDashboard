package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.company.leadershipintelligence.service.ConfigurationService;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Creating and listing tenants belongs to the platform administrator only (decision D46). The administrator has no tenant,
 * the tenant roles cannot reach these endpoints, and a created tenant is usable at once.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PlatformTenantsApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ConfigurationService configurations;

    /** The list of tenants is the whole table, so each test starts from none (other tests create their own tenants as they need them). */
    @org.junit.jupiter.api.BeforeEach
    void noTenants() {
        jdbc.execute("truncate tenant cascade");
    }

    /** A platform administrator: the role, a subject, and no tenant. */
    private static RequestPostProcessor platformAdmin() {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("platform-admin-user");
            j.claim("roles", List.of("PLATFORM_ADMIN"));
        }).authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }

    private static RequestPostProcessor tenantUser(String tenant, String... roles) {
        SimpleGrantedAuthority[] authorities = java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toArray(SimpleGrantedAuthority[]::new);
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("user-1");
            j.claim("tenant_id", tenant);
            j.claim("org_ids", List.of("org-1"));
            j.claim("roles", List.of(roles));
        }).authorities(authorities);
    }

    private static String body(String id, String name) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
    }

    private JsonNode create(String id, String name) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/platform/tenants").with(platformAdmin()).contentType(MediaType.APPLICATION_JSON).content(body(id, name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void aPlatformAdministratorCreatesATenantAndSeesItInTheList() throws Exception {
        JsonNode created = create("acme", "Acme Corporation");
        assertThat(created.get("id").asText()).isEqualTo("acme");
        assertThat(created.get("name").asText()).isEqualTo("Acme Corporation");
        assertThat(created.get("createdAt").asText()).isNotEmpty();

        create("globex", "Globex");
        JsonNode list = json.readTree(mvc.perform(get("/api/v1/platform/tenants").with(platformAdmin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        list.get("items").forEach(t -> ids.add(t.get("id").asText()));
        assertThat(ids).containsExactly("acme", "globex");
    }

    @Test
    void idsAreTrimmedAndNamesKeptAsGiven() throws Exception {
        JsonNode created = json.readTree(mvc.perform(post("/api/v1/platform/tenants").with(platformAdmin()).contentType(MediaType.APPLICATION_JSON)
                .content(body("  acme-2  ", "  Acme 2  "))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(created.get("id").asText()).isEqualTo("acme-2");
        assertThat(created.get("name").asText()).isEqualTo("Acme 2");
    }

    @Test
    void aSecondTenantWithTheSameIdIsRefusedAndNothingChanges() throws Exception {
        create("acme", "Acme");
        mvc.perform(post("/api/v1/platform/tenants").with(platformAdmin()).contentType(MediaType.APPLICATION_JSON).content(body("acme", "Other name")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TENANT_ALREADY_EXISTS"));
        assertThat(jdbc.queryForObject("select name from tenant where id = 'acme'", String.class)).isEqualTo("Acme");
        assertThat(jdbc.queryForObject("select count(*) from tenant", Integer.class)).isEqualTo(1);
    }

    @Test
    void idsAndNamesThatAreNotAllowedAreRefused() throws Exception {
        for (String id : List.of("A", "Acme", "acme corp", "-acme", "acme_corp", "a".repeat(64), "acme/..", "acme;drop")) {
            mvc.perform(post("/api/v1/platform/tenants").with(platformAdmin()).contentType(MediaType.APPLICATION_JSON).content(body(id, "Name")))
                    .andExpect(status().is4xxClientError());
        }
        mvc.perform(post("/api/v1/platform/tenants").with(platformAdmin()).contentType(MediaType.APPLICATION_JSON).content(body("acme", "")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/platform/tenants").with(platformAdmin()).contentType(MediaType.APPLICATION_JSON).content(body("acme", "x".repeat(201))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/platform/tenants").with(platformAdmin()).contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"acme\",\"name\":\"A\",\"extra\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/platform/tenants").with(platformAdmin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from tenant", Integer.class)).isZero();
    }

    @Test
    void theTenantRolesCannotCreateOrListTenantsAndAnonymousCallersAreUnauthorized() throws Exception {
        tenant("existing");
        for (String role : List.of("OWNER", "ADMIN", "ENGINEERING_LEADER", "ANALYST", "VIEWER")) {
            mvc.perform(get("/api/v1/platform/tenants").with(tenantUser("existing", role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/platform/tenants").with(tenantUser("existing", role)).contentType(MediaType.APPLICATION_JSON).content(body("sneaky", "Sneaky")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/platform/tenants")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/platform/tenants").contentType(MediaType.APPLICATION_JSON).content(body("sneaky", "Sneaky"))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from tenant where id = 'sneaky'", Integer.class)).isZero();
    }

    @Test
    void aTokenCannotClaimTheRoleForAnotherTenantsBenefitByCarryingBoth() throws Exception {
        // the role is checked on the verified token; having a tenant as well changes nothing about what the endpoints return
        tenant("existing");
        RequestPostProcessor both = SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject("both");
            j.claim("tenant_id", "existing");
            j.claim("roles", List.of("VIEWER"));
        }).authorities(new SimpleGrantedAuthority("ROLE_VIEWER"));
        mvc.perform(get("/api/v1/platform/tenants").with(both)).andExpect(status().isForbidden());
    }

    @Test
    void thePlatformAdministratorCanReachNothingThatBelongsToATenant() throws Exception {
        tenant("existing");
        mvc.perform(get("/api/v1/scoring-runs").with(platformAdmin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/organizations/org-1/dashboard").with(platformAdmin())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/calculate").with(platformAdmin()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").with(platformAdmin())).andExpect(status().isForbidden());
    }

    @Test
    void aCreatedTenantCanScoreAssessmentsAtOnce() throws Exception {
        Caller platform = Caller.platform("ops");
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "platform", ConfigurationSamples.sampleText());
        configurations.submitForValidation(platform, draft.id());
        configurations.activate(platform, draft.id(), new Approval("approver", null));

        create("newco", "New Company");
        String request = "{\"assessment\":{\"organizationId\":\"org-1\",\"templateId\":\"tpl-1\",\"templateVersion\":\"1\",\"status\":\"COMPLETED\","
                + "\"completedAt\":\"2026-11-01T09:00:00Z\"},\"inputs\":[{\"questionId\":\"Q01\",\"answer\":4},{\"questionId\":\"Q02\",\"answer\":\"OFTEN\"},"
                + "{\"questionId\":\"Q03\",\"answer\":true},{\"questionId\":\"Q04\",\"answer\":\"fine\"},{\"questionId\":\"Q05\",\"answer\":60}]}";
        mvc.perform(post("/api/v1/calculate").with(tenantUser("newco", "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value("newco"));
        // and the new tenant sees nothing of anyone else
        mvc.perform(get("/api/v1/scoring-runs").with(tenantUser("newco", "ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void creationsAndListReadsAreAuditedWithWhoDidThem(CapturedOutput output) throws Exception {
        create("acme", "Acme Secret Name");
        mvc.perform(get("/api/v1/platform/tenants").with(platformAdmin())).andExpect(status().isOk());

        assertThat(jdbc.queryForList("select action || '/' || actor || '/' || coalesce(tenant_id, '-') from scoring_access_audit order by id", String.class))
                .containsExactly("TENANT_CREATED/platform-admin-user/acme", "TENANTS_LISTED/platform-admin-user/-");
        assertThat(jdbc.queryForObject("select detail->>'count' from scoring_access_audit where action = 'TENANTS_LISTED'", String.class)).isEqualTo("1");
        // the audit names tenants by id only and neither it nor the log carries the display name
        assertThat(String.join(" ", jdbc.queryForList("select coalesce(detail::text, '') from scoring_access_audit", String.class))).doesNotContain("Secret");
        assertThat(output.getAll().lines().filter(l -> l.startsWith("{\"@timestamp\"")).collect(java.util.stream.Collectors.joining(" "))).doesNotContain("Secret");
    }

    @Test
    void auditRowsWithoutATenantAreNotVisibleThroughATenantsAudit() throws Exception {
        tenant("existing");
        mvc.perform(get("/api/v1/platform/tenants").with(platformAdmin())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from scoring_access_audit where tenant_id = 'existing'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from scoring_access_audit where tenant_id is null", Integer.class)).isEqualTo(1);
    }
}
