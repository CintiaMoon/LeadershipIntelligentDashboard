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
 * The throttles of the sign-in and account endpoints with small limits: 429 above the per-minute limit, a sign-in lockout
 * after repeated failures (which answers the same for an unknown email), and no email flooding of one address. The answers
 * that must not reveal an account stay the same when the allowance is used up.
 */
@org.springframework.test.context.TestPropertySource(properties = {"lis.identity.ip-requests-per-minute=12", "lis.identity.login-failure-limit=3",
        "lis.identity.emails-per-address-per-hour=2"})
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class AuthRateLimitIntegrationTest extends PostgresIntegrationTest {


    @Autowired
    MockMvc mvc;

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
        // a new minute and hour for every test, since the counters live as long as the application context
        clock.advance(Duration.ofHours(3));
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

    @Test
    void anAddressIsRefusedWith429AboveItsPerMinuteLimit() throws Exception {
        for (int i = 0; i < 12; i++) {
            assertThat(call("/api/v1/auth/resend-verification", "{\"email\":\"x" + i + "@example.com\"}", "10.0.0.1").getResponse().getStatus()).isEqualTo(202);
        }
        MvcResult over = call("/api/v1/auth/resend-verification", "{\"email\":\"x@example.com\"}", "10.0.0.1");
        assertThat(over.getResponse().getStatus()).isEqualTo(429);
        assertThat(over.getResponse().getContentAsString()).contains("TOO_MANY_REQUESTS");
        assertThat(call("/api/v1/auth/resend-verification", "{\"email\":\"x@example.com\"}", "10.0.0.2").getResponse().getStatus())
                .as("another address is not affected").isEqualTo(202);
        clock.advance(Duration.ofSeconds(61));
        assertThat(call("/api/v1/auth/resend-verification", "{\"email\":\"x@example.com\"}", "10.0.0.1").getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    void signInIsRefusedAfterRepeatedFailuresWhetherOrNotTheAccountExists() throws Exception {
        call("/api/v1/auth/register", "{\"email\":\"ana@example.com\",\"password\":\"correct horse battery\",\"displayName\":\"Ana\"}", "10.0.1.1");
        for (int i = 0; i < 3; i++) {
            assertThat(call("/api/v1/auth/login", login("ana@example.com", "wrong password here"), "10.0.1.1").getResponse().getStatus()).isEqualTo(401);
            assertThat(call("/api/v1/auth/login", login("nobody@example.com", "wrong password here"), "10.0.1.1").getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(call("/api/v1/auth/login", login("ana@example.com", "wrong password here"), "10.0.1.1").getResponse().getStatus()).isEqualTo(429);
        assertThat(call("/api/v1/auth/login", login("nobody@example.com", "wrong password here"), "10.0.1.1").getResponse().getStatus()).isEqualTo(429);
        assertThat(jdbc.queryForList("select detail::text from security_audit_event where event_type = 'LOGIN'", String.class).toString()).contains("LOCKED");
        // the same email from another address is not locked out
        assertThat(call("/api/v1/auth/login", login("ana@example.com", "wrong password here"), "10.0.1.2").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void anAddressCannotBeFloodedWithAccountEmails() throws Exception {
        call("/api/v1/auth/register", "{\"email\":\"ana@example.com\",\"password\":\"correct horse battery\",\"displayName\":\"Ana\"}", "10.0.2.1");
        for (int i = 0; i < 5; i++) {
            assertThat(call("/api/v1/auth/resend-verification", "{\"email\":\"ana@example.com\"}", "10.0.2." + (10 + i)).getResponse().getStatus()).isEqualTo(202);
            assertThat(call("/api/v1/auth/password/forgot", "{\"email\":\"ana@example.com\"}", "10.0.2." + (20 + i)).getResponse().getStatus()).isEqualTo(202);
        }
        // two emails an hour: the registration's and one more; every request was answered 202 all the same
        assertThat(sent).hasSizeLessThanOrEqualTo(2);
        clock.advance(Duration.ofMinutes(61));
        call("/api/v1/auth/resend-verification", "{\"email\":\"ana@example.com\"}", "10.0.2.99");
        assertThat(sent).hasSizeGreaterThan(1);
    }
}
