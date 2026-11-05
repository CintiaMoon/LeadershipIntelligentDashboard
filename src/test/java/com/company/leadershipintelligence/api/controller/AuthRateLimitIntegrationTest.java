package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * The throttles of the sign-in and account endpoints with small limits (decision D71): 429 with Retry-After, a delay after
 * repeated failures (the same for an unknown email), no email flooding of one address, and every refusal audited. The answers
 * that must not reveal an account stay the same when the allowance is used up.
 */
@org.springframework.test.context.TestPropertySource(properties = {"lis.rate-limit.login-ip-limit=4", "lis.rate-limit.login-failure-limit=2",
        "lis.rate-limit.account-ip-limit=3", "lis.rate-limit.account-email-limit=2", "lis.rate-limit.token-ip-limit=2"})
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class AuthRateLimitIntegrationTest extends PostgresIntegrationTest {


    @Autowired
    MockMvc mvc;

    private static final java.util.concurrent.atomic.AtomicInteger SLOT = new java.util.concurrent.atomic.AtomicInteger();

    @MockitoBean
    JavaMailSender mail;

    final List<SimpleMailMessage> sent = new ArrayList<>();

    @BeforeEach
    void clean() {
        sent.clear();
        doAnswer(call -> {
            sent.add(call.getArgument(0));
            return null;
        }).when(mail).send(any(SimpleMailMessage.class));
        jdbc.execute("truncate app_user, spring_session, spring_session_attributes, security_audit_event cascade");
        // the base class resets the clock for every test, and the counters live as long as the application context: give each
        // test its own stretch of time, so the windows of an earlier test have run out
        clock.set(clock.instant().plus(Duration.ofHours(3L * SLOT.incrementAndGet())));
    }

    private MvcResult call(String path, String json, String ip) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).with(csrf()).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        }).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private static String login(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }


    private static final String REGISTER = "{\"email\":\"ana@example.com\",\"password\":\"correct horse battery\",\"displayName\":\"Ana\"}";

    private static String code(MvcResult r) throws Exception {
        String body = r.getResponse().getContentAsString();
        int i = body.indexOf("\"code\":\"");
        return i < 0 ? "" : body.substring(i + 8, body.indexOf('"', i + 8));
    }

    @Test
    void signInFromOneAddressIsRefusedWith429AndRetryAfterAboveItsLimit() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertThat(call("/api/v1/auth/login", login("nobody" + i + "@example.com", "wrong password here"), "10.0.0.1").getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult over = call("/api/v1/auth/login", login("nobody@example.com", "wrong password here"), "10.0.0.1");
        assertThat(over.getResponse().getStatus()).isEqualTo(429);
        assertThat(code(over)).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(Integer.parseInt(over.getResponse().getHeader("Retry-After"))).isBetween(1, 300);
        MvcResult other = call("/api/v1/auth/login", login("nobody@example.com", "wrong password here"), "10.0.0.2");
        assertThat(other.getResponse().getStatus())
                .as("another address is not affected").isEqualTo(401);
        // every refusal is audited
        assertThat(jdbc.queryForList("select detail::text from security_audit_event where event_type = 'RATE_LIMITED'", String.class))
                .hasSize(1).first().asString().contains("login-ip");
    }

    @Test
    void repeatedFailuresDelaySignInForTheAccountAndTheDelayEnds() throws Exception {
        call("/api/v1/auth/register", REGISTER, "10.0.1.1");
        for (int i = 0; i < 2; i++) {
            assertThat(call("/api/v1/auth/login", login("ana@example.com", "wrong password here"), "10.0.1." + (10 + i)).getResponse().getStatus()).isEqualTo(401);
            assertThat(call("/api/v1/auth/login", login("nobody@example.com", "wrong password here"), "10.0.1." + (20 + i)).getResponse().getStatus()).isEqualTo(401);
        }
        // from now on the answer is the same for a real and for an unknown account, and also for the right password
        MvcResult known = call("/api/v1/auth/login", login("ana@example.com", "correct horse battery"), "10.0.1.30");
        MvcResult unknown = call("/api/v1/auth/login", login("nobody@example.com", "wrong password here"), "10.0.1.31");
        assertThat(known.getResponse().getStatus()).isEqualTo(429).isEqualTo(unknown.getResponse().getStatus());
        assertThat(Integer.parseInt(known.getResponse().getHeader("Retry-After"))).isBetween(1, 30);
        assertThat(jdbc.queryForList("select detail::text from security_audit_event where event_type = 'RATE_LIMITED'", String.class).toString()).contains("login-account");
        // not a permanent lock
        clock.advance(Duration.ofSeconds(31));
        assertThat(call("/api/v1/auth/login", login("nobody@example.com", "wrong password here"), "10.0.1.32").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void anAddressCannotBeFloodedWithAccountEmailsAndTheAnswerIsTheSameForAnyEmail() throws Exception {
        call("/api/v1/auth/register", REGISTER, "10.0.2.1");
        assertThat(call("/api/v1/auth/password/forgot", "{\"email\":\"ana@example.com\"}", "10.0.2.2").getResponse().getStatus()).isEqualTo(202);
        assertThat(call("/api/v1/auth/password/forgot", "{\"email\":\"ana@example.com\"}", "10.0.2.3").getResponse().getStatus()).isEqualTo(202);
        MvcResult known = call("/api/v1/auth/password/forgot", "{\"email\":\"ana@example.com\"}", "10.0.2.4");
        assertThat(known.getResponse().getStatus()).isEqualTo(429);
        assertThat(known.getResponse().getHeader("Retry-After")).isNotNull();
        call("/api/v1/auth/password/forgot", "{\"email\":\"nobody@example.com\"}", "10.0.2.5");
        call("/api/v1/auth/password/forgot", "{\"email\":\"nobody@example.com\"}", "10.0.2.6");
        assertThat(call("/api/v1/auth/password/forgot", "{\"email\":\"nobody@example.com\"}", "10.0.2.7").getResponse().getStatus())
                .as("an unknown address is throttled the same way").isEqualTo(429);
        // the registration and the two calls that were allowed sent an email each; the throttled ones sent nothing
        assertThat(sent).hasSize(3);
        clock.advance(Duration.ofMinutes(61));
        assertThat(call("/api/v1/auth/password/forgot", "{\"email\":\"ana@example.com\"}", "10.0.2.8").getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    void oneAddressCannotMakeManyAccountRequests() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("/api/v1/auth/resend-verification", "{\"email\":\"x" + i + "@example.com\"}", "10.0.3.1").getResponse().getStatus()).isEqualTo(202);
        }
        assertThat(call("/api/v1/auth/resend-verification", "{\"email\":\"y@example.com\"}", "10.0.3.1").getResponse().getStatus()).isEqualTo(429);
        assertThat(call("/api/v1/auth/register", REGISTER, "10.0.3.1").getResponse().getStatus()).as("register is counted on its own").isEqualTo(202);
    }

    @Test
    void usingLinksIsLimitedPerAddress() throws Exception {
        String body = "{\"token\":\"not-a-real-token\",\"password\":\"correct horse battery\"}";
        assertThat(call("/api/v1/auth/verify-email", body, "10.0.4.1").getResponse().getStatus()).isEqualTo(400);
        assertThat(call("/api/v1/auth/verify-email", body, "10.0.4.1").getResponse().getStatus()).isEqualTo(400);
        assertThat(call("/api/v1/auth/verify-email", body, "10.0.4.1").getResponse().getStatus()).isEqualTo(429);
        assertThat(call("/api/v1/auth/password/reset", "{\"token\":\"bad\",\"newPassword\":\"a brand new passphrase\"}", "10.0.4.1").getResponse().getStatus())
                .as("verification and reset links share the allowance").isEqualTo(429);
    }
}
