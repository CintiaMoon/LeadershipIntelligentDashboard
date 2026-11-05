package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.support.AccountFlowTest;
import com.company.leadershipintelligence.support.SessionClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static com.company.leadershipintelligence.support.SessionClient.status;
import static org.assertj.core.api.Assertions.assertThat;

/** The call limit of an API token (decisions D70, D71): 429 with Retry-After above it, per token, and invalid tokens are counted per address. */
@org.springframework.test.context.TestPropertySource(properties = {"lis.rate-limit.api-token-limit=3", "lis.rate-limit.api-token-failure-limit=3"})
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class ApiTokenRateLimitIntegrationTest extends AccountFlowTest {

    @Test
    void aTokenIsLimitedPerMinuteAndAnotherTokenIsNotAffected() throws Exception {
        SessionClient owner = signedIn("owner@example.com");
        member("acme", "owner@example.com", "ACTIVE", "OWNER");
        String one = createToken(owner, "acme", Map.of("name", "one", "role", "VIEWER")).get("secret").asText();
        String two = createToken(owner, "acme", Map.of("name", "two", "role", "VIEWER")).get("secret").asText();
        for (int i = 0; i < 3; i++) {
            assertThat(status(bearerGet(one, "/api/v1/assessments"))).isEqualTo(200);
        }
        MvcResult over = bearerGet(one, "/api/v1/assessments");
        assertThat(status(over)).isEqualTo(429);
        assertThat(code(body(over))).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(Integer.parseInt(over.getResponse().getHeader("Retry-After"))).isBetween(1, 60);
        assertThat(status(bearerGet(two, "/api/v1/assessments"))).as("another token has its own allowance").isEqualTo(200);
        assertThat(jdbc.queryForList("select detail::text from security_audit_event where event_type = 'RATE_LIMITED'", String.class).toString()).contains("api-token");
    }

    @Test
    void anAddressThatKeepsPresentingInvalidTokensIsRefused() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(status(bearerGet("lis_" + "x".repeat(40) + i, "/api/v1/assessments"))).isEqualTo(401);
        }
        MvcResult over = bearerGet("lis_" + "x".repeat(41), "/api/v1/assessments");
        assertThat(status(over)).isEqualTo(429);
        assertThat(over.getResponse().getHeader("Retry-After")).isNotNull();
    }
}
