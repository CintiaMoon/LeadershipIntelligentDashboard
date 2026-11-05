package com.company.leadershipintelligence.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

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
 * Helpers for integration tests that need real people: registering, verifying and signing in an account the way a person does,
 * giving it a membership, and calling the API with a bearer token. Subclasses add {@code @AutoConfigureMockMvc}. Two companies,
 * {@code acme} and {@code globex}, exist for every test.
 */
public abstract class AccountFlowTest extends PostgresIntegrationTest {

    protected static final String PASSWORD = "correct horse battery";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @MockitoBean
    protected JavaMailSender mail;

    protected final List<SimpleMailMessage> sent = new ArrayList<>();

    @BeforeEach
    void cleanAccounts() {
        sent.clear();
        doAnswer(call -> {
            sent.add(call.getArgument(0));
            return null;
        }).when(mail).send(any(SimpleMailMessage.class));
        jdbc.execute("truncate app_user, spring_session, spring_session_attributes, security_audit_event cascade");
        jdbc.update("insert into tenant (id, name) values ('acme', 'Acme Corp') on conflict do nothing");
        jdbc.update("insert into tenant (id, name) values ('globex', 'Globex') on conflict do nothing");
    }

    protected SessionClient browser() {
        return new SessionClient(mvc, json);
    }

    protected String lastToken() {
        Matcher m = TOKEN.matcher(sent.get(sent.size() - 1).getText());
        assertThat(m.find()).as("a link with a token in the last email").isTrue();
        return m.group(1);
    }

    /** Registers, verifies and signs in an account. */
    protected SessionClient signedIn(String email) throws Exception {
        SessionClient b = browser();
        assertThat(status(b.post("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", "Test User")))).isEqualTo(202);
        assertThat(status(b.post("/api/v1/auth/verify-email", Map.of("token", lastToken(), "password", PASSWORD)))).isEqualTo(204);
        assertThat(status(b.post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD)))).isEqualTo(200);
        return b;
    }

    protected UUID userId(String email) {
        return jdbc.queryForObject("select id from app_user where email = ?", UUID.class, email);
    }

    protected UUID member(String tenant, String email, String status, String... roles) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into company_membership (id, tenant_id, user_id, status, created_at, updated_at) values (?, ?, ?, ?, now(), now())",
                id, tenant, userId(email), status);
        for (String role : roles) {
            jdbc.update("insert into company_membership_role (membership_id, role) values (?, ?)", id, role);
        }
        return id;
    }

    protected void restrictTo(UUID membership, String organization) {
        jdbc.update("insert into company_membership_organization (membership_id, organization_id) values (?, ?)", membership, organization);
    }

    protected static String code(JsonNode body) {
        return body.path("code").asText();
    }

    // ---- bearer calls: no cookie, no CSRF, no company header

    protected MvcResult bearerGet(String secret, String path) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + secret)).andReturn();
    }

    protected MvcResult bearerPost(String secret, String path, Object body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path).header("Authorization", "Bearer " + secret)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn();
    }

    protected JsonNode body(MvcResult result) throws Exception {
        String text = result.getResponse().getContentAsString();
        return text.isEmpty() ? json.createObjectNode() : json.readTree(text);
    }

    /** Creates a token through the API as the signed-in owner/administrator and returns the answer. */
    protected JsonNode createToken(SessionClient admin, String company, Map<String, Object> request) throws Exception {
        MvcResult r = admin.inCompany(company).post("/api/v1/api-tokens", request);
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return body(r);
    }
}
