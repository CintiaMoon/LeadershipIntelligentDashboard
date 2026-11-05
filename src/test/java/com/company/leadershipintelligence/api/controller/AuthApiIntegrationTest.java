package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Accounts and sessions end to end against a real PostgreSQL (decisions D65 to D68, D73, D74): registration, email
 * verification, sign-in, the server-side session, sign-out, CSRF, password reset and change, no account enumeration, and
 * the security audit. Emails are captured from a mocked SMTP sender; each {@link Browser} keeps its own cookies.
 */
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class AuthApiIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct horse battery";
    private static final String EMAIL = "ana@example.com";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

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
    }

    /** One browser: its own cookie jar, and the CSRF header filled in the way the web application does it. */
    final class Browser {
        final Map<String, Cookie> jar = new LinkedHashMap<>();

        MvcResult getInCompany(String path, String company) throws Exception {
            return send(MockMvcRequestBuilders.get(path).header("X-Company-Id", company));
        }

        MvcResult get(String path) throws Exception {
            return send(MockMvcRequestBuilders.get(path));
        }

        MvcResult post(String path, Object body) throws Exception {
            if (!jar.containsKey("XSRF-TOKEN")) {
                get("/api/v1/auth/csrf");
            }
            return send(request(path, body).header("X-XSRF-TOKEN", jar.get("XSRF-TOKEN").getValue()));
        }

        MvcResult postWithoutCsrf(String path, Object body) throws Exception {
            return send(request(path, body));
        }

        MvcResult postWithCsrf(String path, Object body, String token) throws Exception {
            return send(request(path, body).header("X-XSRF-TOKEN", token));
        }

        String sessionId() {
            Cookie cookie = jar.get("LIS_SESSION");
            return cookie == null ? null : cookie.getValue();
        }

        private MockHttpServletRequestBuilder request(String path, Object body) throws Exception {
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(path);
            if (body != null) {
                builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
            }
            return builder;
        }

        private MvcResult send(MockHttpServletRequestBuilder builder) throws Exception {
            if (!jar.isEmpty()) {
                builder.cookie(jar.values().toArray(new Cookie[0]));
            }
            MvcResult result = mvc.perform(builder).andReturn();
            for (Cookie cookie : result.getResponse().getCookies()) {
                if (cookie.getMaxAge() == 0) {
                    jar.remove(cookie.getName());
                } else {
                    jar.put(cookie.getName(), cookie);
                }
            }
            return result;
        }
    }

    private static int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private JsonNode body(MvcResult r) throws Exception {
        String text = r.getResponse().getContentAsString();
        return text.isEmpty() ? json.createObjectNode() : json.readTree(text);
    }

    private static Map<String, String> register(String email, String password) {
        return Map.of("email", email, "password", password, "displayName", "Ana Silva");
    }

    private static Map<String, String> login(String email, String password) {
        return Map.of("email", email, "password", password);
    }

    private static Map<String, String> verification(String token) {
        return verification(token, PASSWORD);
    }

    private static Map<String, String> verification(String token, String password) {
        return Map.of("token", token, "password", password);
    }

    private String lastToken() {
        assertThat(sent).isNotEmpty();
        Matcher m = TOKEN.matcher(sent.get(sent.size() - 1).getText());
        assertThat(m.find()).as("a link with a token in the last email").isTrue();
        return m.group(1);
    }

    /** Registers and verifies an account, as a person would. */
    private void activeAccount(String email, String password) throws Exception {
        Browser b = new Browser();
        assertThat(status(b.post("/api/v1/auth/register", register(email, password)))).isEqualTo(202);
        assertThat(status(b.post("/api/v1/auth/verify-email", verification(lastToken(), password)))).isEqualTo(204);
    }

    private Browser signedIn(String email, String password) throws Exception {
        Browser b = new Browser();
        MvcResult r = b.post("/api/v1/auth/login", login(email, password));
        assertThat(status(r)).isEqualTo(200);
        return b;
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    // ---- registration and verification ---------------------------------------------------------------

    @Test
    void registrationCreatesAPendingAccountAndSendsOneLink() throws Exception {
        long tenantsBefore = count("select count(*) from tenant");
        Browser b = new Browser();
        MvcResult r = b.post("/api/v1/auth/register", register("  Ana@Example.com ", PASSWORD));
        assertThat(status(r)).isEqualTo(202);
        assertThat(r.getResponse().getContentAsString()).isEmpty();

        assertThat(jdbc.queryForMap("select email, status, platform_admin, email_verified_at from app_user"))
                .containsEntry("email", EMAIL).containsEntry("status", "PENDING_VERIFICATION").containsEntry("platform_admin", false)
                .containsEntry("email_verified_at", null);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getTo()).containsExactly(EMAIL);
        assertThat(sent.get(0).getText()).contains("/verify-email?token=").doesNotContain(PASSWORD);
        // registration creates no company
        assertThat(count("select count(*) from tenant")).isEqualTo(tenantsBefore);
    }

    @Test
    void thePasswordIsStoredOnlyAsAHashAndTheTokenOnlyAsItsDigest() throws Exception {
        new Browser().post("/api/v1/auth/register", register(EMAIL, PASSWORD));
        String hash = jdbc.queryForObject("select password_hash from app_user", String.class);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        String stored = jdbc.queryForObject("select token_hash from auth_token", String.class);
        assertThat(stored).hasSize(64).isNotEqualTo(lastToken());
    }

    @Test
    void anAccountCannotSignInUntilItsEmailIsVerified() throws Exception {
        Browser b = new Browser();
        b.post("/api/v1/auth/register", register(EMAIL, PASSWORD));
        MvcResult r = b.post("/api/v1/auth/login", login(EMAIL, PASSWORD));
        assertThat(status(r)).isEqualTo(401);
        assertThat(body(r).get("code").asText()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(b.sessionId()).isNull();
    }

    @Test
    void verifyingActivatesTheAccountAndTheLinkWorksOnce() throws Exception {
        Browser b = new Browser();
        b.post("/api/v1/auth/register", register(EMAIL, PASSWORD));
        String token = lastToken();
        assertThat(status(b.post("/api/v1/auth/verify-email", verification(token)))).isEqualTo(204);
        assertThat(jdbc.queryForMap("select status, email_verified_at is not null as verified from app_user"))
                .containsEntry("status", "ACTIVE").containsEntry("verified", true);

        MvcResult again = b.post("/api/v1/auth/verify-email", verification(token));
        assertThat(status(again)).isEqualTo(400);
        assertThat(body(again).get("code").asText()).isEqualTo("INVALID_TOKEN");
        assertThat(status(b.post("/api/v1/auth/login", login(EMAIL, PASSWORD)))).isEqualTo(200);
    }

    @Test
    void anExpiredOrInventedVerificationLinkIsRefused() throws Exception {
        Browser b = new Browser();
        b.post("/api/v1/auth/register", register(EMAIL, PASSWORD));
        String token = lastToken();
        assertThat(status(b.post("/api/v1/auth/verify-email", verification("not-a-real-token")))).isEqualTo(400);
        clock.advance(Duration.ofHours(24).plusSeconds(1));
        assertThat(status(b.post("/api/v1/auth/verify-email", verification(token)))).isEqualTo(400);
        assertThat(jdbc.queryForObject("select status from app_user", String.class)).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void aResentLinkReplacesTheEarlierOne() throws Exception {
        Browser b = new Browser();
        b.post("/api/v1/auth/register", register(EMAIL, PASSWORD));
        String first = lastToken();
        assertThat(status(b.post("/api/v1/auth/resend-verification", Map.of("email", EMAIL)))).isEqualTo(202);
        String second = lastToken();
        assertThat(second).isNotEqualTo(first);
        assertThat(status(b.post("/api/v1/auth/verify-email", verification(first)))).isEqualTo(400);
        assertThat(status(b.post("/api/v1/auth/verify-email", verification(second)))).isEqualTo(204);
    }

    @Test
    void invalidRegistrationsAreRefusedBeforeAnythingIsStored() throws Exception {
        Browser b = new Browser();
        MvcResult badEmail = b.post("/api/v1/auth/register", register("not-an-email", PASSWORD));
        assertThat(status(badEmail)).isEqualTo(422);
        assertThat(body(badEmail).get("code").asText()).isEqualTo("INVALID_ACCOUNT_REQUEST");

        MvcResult weak = b.post("/api/v1/auth/register", register(EMAIL, "short"));
        assertThat(status(weak)).isEqualTo(422);
        assertThat(body(weak).get("code").asText()).isEqualTo("WEAK_PASSWORD");
        assertThat(body(weak).toString()).doesNotContain("short\"");

        assertThat(status(b.post("/api/v1/auth/register", register(EMAIL, "Password12345")))).isEqualTo(422);
        assertThat(status(b.post("/api/v1/auth/register", Map.of("email", EMAIL, "password", PASSWORD)))).isEqualTo(400);
        assertThat(status(b.post("/api/v1/auth/register", Map.of("email", EMAIL, "password", PASSWORD, "displayName", "x", "role", "OWNER")))).isEqualTo(400);
        assertThat(count("select count(*) from app_user")).isZero();
        assertThat(sent).isEmpty();
    }

    // ---- no account enumeration ----------------------------------------------------------------------

    @Test
    void registeringAKnownAddressLooksTheSameAndChangesNothing() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        sent.clear();
        Browser b = new Browser();
        MvcResult r = b.post("/api/v1/auth/register", register(EMAIL, "another long passphrase"));
        assertThat(status(r)).isEqualTo(202);
        assertThat(r.getResponse().getContentAsString()).isEmpty();
        assertThat(count("select count(*) from app_user")).isEqualTo(1);
        // the owner of the address is told instead, and the old password still works
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getSubject()).contains("already have an account");
        assertThat(status(b.post("/api/v1/auth/login", login(EMAIL, "another long passphrase")))).isEqualTo(401);
        assertThat(status(b.post("/api/v1/auth/login", login(EMAIL, PASSWORD)))).isEqualTo(200);
    }

    @Test
    void anAddressCannotBeTakenOverByRegisteringItFirst() throws Exception {
        Browser attacker = new Browser();
        attacker.post("/api/v1/auth/register", register(EMAIL, "attacker chosen passphrase"));
        String attackerLink = lastToken();
        // the real owner receives that link and clicks it, but does not know the attacker's password
        Browser owner = new Browser();
        MvcResult refused = owner.post("/api/v1/auth/verify-email", verification(attackerLink, PASSWORD));
        assertThat(status(refused)).isEqualTo(400);
        assertThat(body(refused).get("code").asText()).isEqualTo("INVALID_TOKEN");
        assertThat(jdbc.queryForObject("select status from app_user", String.class)).isEqualTo("PENDING_VERIFICATION");
        assertThat(jdbc.queryForList("select detail::text from security_audit_event where event_type = 'EMAIL_VERIFIED'", String.class).toString())
                .contains("BAD_PASSWORD");

        // the owner registers the address themselves: the latest registration wins and the earlier link is retired
        assertThat(status(owner.post("/api/v1/auth/register", register(EMAIL, PASSWORD)))).isEqualTo(202);
        String ownerLink = lastToken();
        assertThat(count("select count(*) from app_user")).isEqualTo(1);
        assertThat(status(attacker.post("/api/v1/auth/verify-email", verification(attackerLink, "attacker chosen passphrase")))).isEqualTo(400);
        assertThat(status(owner.post("/api/v1/auth/verify-email", verification(ownerLink)))).isEqualTo(204);
        assertThat(status(owner.post("/api/v1/auth/login", login(EMAIL, "attacker chosen passphrase")))).isEqualTo(401);
        assertThat(status(owner.post("/api/v1/auth/login", login(EMAIL, PASSWORD)))).isEqualTo(200);
    }

    @Test
    void aWrongPasswordDoesNotUseUpTheVerificationLink() throws Exception {
        Browser b = new Browser();
        b.post("/api/v1/auth/register", register(EMAIL, PASSWORD));
        String token = lastToken();
        assertThat(status(b.post("/api/v1/auth/verify-email", verification(token, "not the password at all")))).isEqualTo(400);
        assertThat(status(b.post("/api/v1/auth/verify-email", verification(token)))).isEqualTo(204);
    }

    @Test
    void signInFailuresAreIdenticalWhateverTheReason() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = new Browser();
        MvcResult unknown = b.post("/api/v1/auth/login", login("nobody@example.com", PASSWORD));
        MvcResult wrong = b.post("/api/v1/auth/login", login(EMAIL, "wrong password here"));
        MvcResult malformed = b.post("/api/v1/auth/login", login("not-an-email", PASSWORD));
        jdbc.update("update app_user set status = 'DISABLED'");
        MvcResult disabled = b.post("/api/v1/auth/login", login(EMAIL, PASSWORD));
        for (MvcResult r : List.of(unknown, wrong, malformed, disabled)) {
            assertThat(status(r)).isEqualTo(401);
            assertThat(body(r).get("code").asText()).isEqualTo("AUTHENTICATION_FAILED");
            assertThat(body(r).get("message")).isEqualTo(body(unknown).get("message"));
        }
    }

    @Test
    void askingForAResetAnswersTheSameForUnknownAndKnownAddresses() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        sent.clear();
        Browser b = new Browser();
        MvcResult unknown = b.post("/api/v1/auth/password/forgot", Map.of("email", "nobody@example.com"));
        MvcResult garbage = b.post("/api/v1/auth/password/forgot", Map.of("email", "garbage"));
        assertThat(sent).isEmpty();
        MvcResult known = b.post("/api/v1/auth/password/forgot", Map.of("email", EMAIL));
        for (MvcResult r : List.of(unknown, garbage, known)) {
            assertThat(status(r)).isEqualTo(202);
            assertThat(r.getResponse().getContentAsString()).isEmpty();
        }
        assertThat(sent).hasSize(1);
    }

    // ---- sign-in, session, sign-out -------------------------------------------------------------------

    @Test
    void signingInStartsAServerSideSessionForTheUserOnly() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = new Browser();
        MvcResult r = b.post("/api/v1/auth/login", login("ANA@example.com", PASSWORD));
        assertThat(status(r)).isEqualTo(200);
        JsonNode me = body(r);
        assertThat(me.get("email").asText()).isEqualTo(EMAIL);
        assertThat(me.get("displayName").asText()).isEqualTo("Ana Silva");
        assertThat(me.get("platformAdmin").asBoolean()).isFalse();
        assertThat(me.get("companies")).isEmpty();
        assertThat(me.has("passwordHash") || me.has("password")).isFalse();

        Cookie cookie = b.jar.get("LIS_SESSION");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(r.getResponse().getHeaders("Set-Cookie").stream().filter(h -> h.startsWith("LIS_SESSION")).findFirst().orElseThrow())
                .containsIgnoringCase("SameSite=Lax");

        // the principal of the session is the user's id, and the session holds no company
        String userId = jdbc.queryForObject("select id::text from app_user", String.class);
        assertThat(jdbc.queryForObject("select principal_name from spring_session", String.class)).isEqualTo(userId);
        assertThat(body(b.get("/api/v1/auth/me")).get("id").asText()).isEqualTo(userId);
    }

    @Test
    void anonymousAndRefusedRequestsNeverCreateASession() throws Exception {
        Browser b = new Browser();
        b.get("/api/v1/auth/me");
        b.get("/api/v1/auth/csrf");
        b.post("/api/v1/auth/login", login("nobody@example.com", PASSWORD));
        b.postWithoutCsrf("/api/v1/auth/login", login("nobody@example.com", PASSWORD));
        b.post("/api/v1/auth/password/change", Map.of("currentPassword", PASSWORD, "newPassword", "a brand new passphrase"));
        Browser stale = new Browser();
        stale.jar.put("LIS_SESSION", new Cookie("LIS_SESSION", "ZGVhZC1zZXNzaW9u"));
        stale.get("/api/v1/auth/me");
        assertThat(count("select count(*) from spring_session")).isZero();
        assertThat(b.sessionId()).isNull();
    }

    @Test
    void theCurrentUserNeedsASession() throws Exception {
        MvcResult r = new Browser().get("/api/v1/auth/me");
        assertThat(status(r)).isEqualTo(401);
        assertThat(body(r).get("code").asText()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void everySignInGetsANewSessionAndEndsTheOldOne() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = signedIn(EMAIL, PASSWORD);
        String first = b.sessionId();
        assertThat(status(b.post("/api/v1/auth/login", login(EMAIL, PASSWORD)))).isEqualTo(200);
        assertThat(b.sessionId()).isNotEqualTo(first);
        assertThat(count("select count(*) from spring_session")).isEqualTo(1);

        Browser stale = new Browser();
        stale.jar.put("LIS_SESSION", new Cookie("LIS_SESSION", first));
        assertThat(status(stale.get("/api/v1/auth/me"))).isEqualTo(401);
    }

    @Test
    void aSessionIdOfferedBeforeSignInIsNeverReused() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser attacker = new Browser();
        attacker.jar.put("LIS_SESSION", new Cookie("LIS_SESSION", "ATTACKER-CHOSEN-SESSION-ID"));
        assertThat(status(attacker.post("/api/v1/auth/login", login(EMAIL, PASSWORD)))).isEqualTo(200);
        assertThat(attacker.sessionId()).isNotEqualTo("ATTACKER-CHOSEN-SESSION-ID");
    }

    @Test
    void signingOutEndsTheSessionAndIsIdempotent() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = signedIn(EMAIL, PASSWORD);
        String session = b.sessionId();
        assertThat(status(b.post("/api/v1/auth/logout", null))).isEqualTo(204);
        assertThat(count("select count(*) from spring_session")).isZero();
        assertThat(status(b.get("/api/v1/auth/me"))).isEqualTo(401);

        // the old cookie is dead even if the browser keeps sending it
        Browser replay = new Browser();
        replay.jar.put("LIS_SESSION", new Cookie("LIS_SESSION", session));
        assertThat(status(replay.get("/api/v1/auth/me"))).isEqualTo(401);
        assertThat(status(new Browser().post("/api/v1/auth/logout", null))).isEqualTo(204);
    }

    @Test
    void aDisabledAccountLosesItsSessionAtOnce() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = signedIn(EMAIL, PASSWORD);
        jdbc.update("update app_user set status = 'DISABLED'");
        assertThat(status(b.get("/api/v1/auth/me"))).isEqualTo(401);
        assertThat(status(b.get("/api/v1/auth/me"))).isEqualTo(401);
    }

    @Test
    void aSessionCookieAloneReachesNothingTenantScoped() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = signedIn(EMAIL, PASSWORD);
        // a session names a user, not a company: without a company header a tenant endpoint is refused (400), and the company
        // named by the header must be one where the user has an ACTIVE membership (this user has none)
        for (String path : new String[]{"/api/v1/assessments", "/api/v1/scoring-runs", "/api/v1/configuration", "/api/v1/teams"}) {
            MvcResult none = b.get(path);
            assertThat(status(none)).as(path).isEqualTo(400);
            assertThat(body(none).get("code").asText()).isEqualTo("COMPANY_REQUIRED");
            MvcResult named = b.getInCompany(path, "acme");
            assertThat(status(named)).as(path).isEqualTo(403);
            assertThat(body(named).get("code").asText()).isEqualTo("COMPANY_ACCESS_DENIED");
        }
        // and the platform endpoints need the platform flag
        assertThat(status(b.get("/api/v1/platform/tenants"))).isEqualTo(403);
    }

    // ---- CSRF -------------------------------------------------------------------------------------------

    @Test
    void stateChangingRequestsNeedTheCsrfToken() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = new Browser();
        MvcResult none = b.postWithoutCsrf("/api/v1/auth/login", login(EMAIL, PASSWORD));
        assertThat(status(none)).isEqualTo(403);
        assertThat(body(none).get("code").asText()).isEqualTo("CSRF_TOKEN_INVALID");
        b.get("/api/v1/auth/csrf");
        assertThat(status(b.postWithCsrf("/api/v1/auth/login", login(EMAIL, PASSWORD), "forged-token"))).isEqualTo(403);
        assertThat(count("select count(*) from spring_session")).isZero();
    }

    @Test
    void signingOutAndChangingAPasswordNeedTheCsrfTokenToo() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = signedIn(EMAIL, PASSWORD);
        assertThat(status(b.postWithoutCsrf("/api/v1/auth/logout", null))).isEqualTo(403);
        assertThat(status(b.postWithoutCsrf("/api/v1/auth/password/change", Map.of("currentPassword", PASSWORD, "newPassword", "a brand new passphrase")))).isEqualTo(403);
        assertThat(status(b.get("/api/v1/auth/me"))).isEqualTo(200);
    }

    @Test
    void theCsrfCookieIsReadableByScriptButSameSiteAndSecureLikeTheSessionCookie() throws Exception {
        Browser b = new Browser();
        MvcResult r = b.get("/api/v1/auth/csrf");
        Cookie cookie = r.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void theCsrfTokenIsRenewedWhenTheUserSignsIn() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = new Browser();
        b.get("/api/v1/auth/csrf");
        String before = b.jar.get("XSRF-TOKEN").getValue();
        b.post("/api/v1/auth/login", login(EMAIL, PASSWORD));
        assertThat(b.jar.get("XSRF-TOKEN").getValue()).isNotEqualTo(before);
        assertThat(status(b.post("/api/v1/auth/logout", null))).isEqualTo(204);
    }

    @Test
    void theCsrfEndpointGivesTheHeaderNameAndTheToken() throws Exception {
        Browser b = new Browser();
        JsonNode csrf = body(b.get("/api/v1/auth/csrf"));
        assertThat(csrf.get("headerName").asText()).isEqualTo("X-XSRF-TOKEN");
        assertThat(csrf.get("token").asText()).isEqualTo(b.jar.get("XSRF-TOKEN").getValue()).isNotBlank();
    }

    // ---- password reset and change ----------------------------------------------------------------------

    @Test
    void aResetLinkSetsANewPasswordEndsEverySessionAndWorksOnce() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser phone = signedIn(EMAIL, PASSWORD);
        Browser laptop = signedIn(EMAIL, PASSWORD);
        sent.clear();

        Browser b = new Browser();
        assertThat(status(b.post("/api/v1/auth/password/forgot", Map.of("email", EMAIL)))).isEqualTo(202);
        String token = lastToken();
        assertThat(sent.get(0).getSubject()).contains("Reset");
        sent.clear();
        assertThat(status(b.post("/api/v1/auth/password/reset", Map.of("token", token, "newPassword", "a brand new passphrase")))).isEqualTo(204);

        assertThat(status(phone.get("/api/v1/auth/me"))).isEqualTo(401);
        assertThat(status(laptop.get("/api/v1/auth/me"))).isEqualTo(401);
        assertThat(count("select count(*) from spring_session")).isZero();
        assertThat(status(b.post("/api/v1/auth/login", login(EMAIL, PASSWORD)))).isEqualTo(401);
        assertThat(status(b.post("/api/v1/auth/login", login(EMAIL, "a brand new passphrase")))).isEqualTo(200);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getSubject()).contains("changed");

        MvcResult reuse = b.post("/api/v1/auth/password/reset", Map.of("token", token, "newPassword", "yet another passphrase"));
        assertThat(status(reuse)).isEqualTo(400);
        assertThat(body(reuse).get("code").asText()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void askingAgainRetiresTheEarlierResetLinkAndTimeRetiresTheLatest() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = new Browser();
        b.post("/api/v1/auth/password/forgot", Map.of("email", EMAIL));
        String first = lastToken();
        b.post("/api/v1/auth/password/forgot", Map.of("email", EMAIL));
        String second = lastToken();
        assertThat(status(b.post("/api/v1/auth/password/reset", Map.of("token", first, "newPassword", "a brand new passphrase")))).isEqualTo(400);
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        assertThat(status(b.post("/api/v1/auth/password/reset", Map.of("token", second, "newPassword", "a brand new passphrase")))).isEqualTo(400);
        assertThat(status(b.post("/api/v1/auth/login", login(EMAIL, PASSWORD)))).isEqualTo(200);
    }

    @Test
    void aWeakNewPasswordDoesNotUseUpTheLink() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = new Browser();
        b.post("/api/v1/auth/password/forgot", Map.of("email", EMAIL));
        String token = lastToken();
        MvcResult weak = b.post("/api/v1/auth/password/reset", Map.of("token", token, "newPassword", "tooshort"));
        assertThat(status(weak)).isEqualTo(422);
        assertThat(body(weak).get("code").asText()).isEqualTo("WEAK_PASSWORD");
        // the new password may not be the address itself; that rule needs the owner, which the link reveals
        MvcResult address = b.post("/api/v1/auth/password/reset", Map.of("token", token, "newPassword", EMAIL));
        assertThat(status(address)).isEqualTo(422);
        assertThat(status(b.post("/api/v1/auth/password/reset", Map.of("token", token, "newPassword", "a brand new passphrase")))).isEqualTo(204);
    }

    @Test
    void aPendingAccountThatAsksForAResetGetsAVerificationLinkInstead() throws Exception {
        Browser b = new Browser();
        b.post("/api/v1/auth/register", register(EMAIL, PASSWORD));
        sent.clear();
        assertThat(status(b.post("/api/v1/auth/password/forgot", Map.of("email", EMAIL)))).isEqualTo(202);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getText()).contains("/verify-email?token=");
        assertThat(sent.get(0).getText()).doesNotContain("/reset-password");
    }

    @Test
    void aVerificationLinkCannotResetAPasswordAndViceVersa() throws Exception {
        Browser b = new Browser();
        b.post("/api/v1/auth/register", register(EMAIL, PASSWORD));
        String verification = lastToken();
        assertThat(status(b.post("/api/v1/auth/password/reset", Map.of("token", verification, "newPassword", "a brand new passphrase")))).isEqualTo(400);
        b.post("/api/v1/auth/verify-email", verification(verification));
        b.post("/api/v1/auth/password/forgot", Map.of("email", EMAIL));
        assertThat(status(b.post("/api/v1/auth/verify-email", verification(lastToken())))).isEqualTo(400);
    }

    @Test
    void changingThePasswordKeepsTheCurrentSessionAndEndsTheOthers() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser here = signedIn(EMAIL, PASSWORD);
        Browser elsewhere = signedIn(EMAIL, PASSWORD);
        sent.clear();

        MvcResult wrong = here.post("/api/v1/auth/password/change", Map.of("currentPassword", "not my password", "newPassword", "a brand new passphrase"));
        assertThat(status(wrong)).isEqualTo(422);
        assertThat(status(here.post("/api/v1/auth/password/change", Map.of("currentPassword", PASSWORD, "newPassword", "short")))).isEqualTo(422);
        assertThat(sent).isEmpty();

        assertThat(status(here.post("/api/v1/auth/password/change", Map.of("currentPassword", PASSWORD, "newPassword", "a brand new passphrase")))).isEqualTo(204);
        assertThat(status(here.get("/api/v1/auth/me"))).isEqualTo(200);
        assertThat(status(elsewhere.get("/api/v1/auth/me"))).isEqualTo(401);
        assertThat(count("select count(*) from spring_session")).isEqualTo(1);
        assertThat(sent).hasSize(1);
        assertThat(status(new Browser().post("/api/v1/auth/login", login(EMAIL, "a brand new passphrase")))).isEqualTo(200);
    }

    @Test
    void changingThePasswordNeedsASignedInUser() throws Exception {
        MvcResult r = new Browser().post("/api/v1/auth/password/change", Map.of("currentPassword", PASSWORD, "newPassword", "a brand new passphrase"));
        assertThat(status(r)).isEqualTo(401);
    }

    // ---- security audit ----------------------------------------------------------------------------------

    @Test
    void theAuditRecordsTheFlowWithoutSecretsAndWithoutAnUnknownEmail() throws Exception {
        Browser b = new Browser();
        b.post("/api/v1/auth/register", register(EMAIL, PASSWORD));
        String token = lastToken();
        b.post("/api/v1/auth/verify-email", verification(token));
        b.post("/api/v1/auth/login", login("stranger@example.com", PASSWORD));
        b.post("/api/v1/auth/login", login(EMAIL, "wrong password here"));
        b.post("/api/v1/auth/login", login(EMAIL, PASSWORD));
        b.post("/api/v1/auth/logout", null);

        List<Map<String, Object>> events = jdbc.queryForList("select event_type, outcome, detail::text as detail, subject_hash, user_id, client_ip from security_audit_event order by id");
        assertThat(events).extracting(e -> e.get("event_type") + ":" + e.get("outcome")).containsExactly(
                "REGISTERED:SUCCESS", "EMAIL_VERIFIED:SUCCESS", "LOGIN:FAILURE", "LOGIN:FAILURE", "LOGIN:SUCCESS", "LOGOUT:SUCCESS");
        assertThat(events.get(2).get("detail").toString()).contains("UNKNOWN_ACCOUNT");
        assertThat(events.get(2).get("user_id")).isNull();
        assertThat(events.get(2).get("subject_hash").toString()).hasSize(64);
        assertThat(events.get(3).get("detail").toString()).contains("BAD_PASSWORD");
        assertThat(events.get(3).get("user_id")).isNotNull();

        String everything = jdbc.queryForList("select (e.*)::text from security_audit_event e", String.class).toString();
        assertThat(everything).doesNotContain(PASSWORD).doesNotContain("wrong password here").doesNotContain(token).doesNotContain("stranger@example.com");
    }

    @Test
    void thePasswordResetFlowIsAudited() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = new Browser();
        b.post("/api/v1/auth/password/forgot", Map.of("email", EMAIL));
        b.post("/api/v1/auth/password/reset", Map.of("token", lastToken(), "newPassword", "a brand new passphrase"));
        b.post("/api/v1/auth/password/reset", Map.of("token", "bad", "newPassword", "a brand new passphrase"));
        assertThat(jdbc.queryForList("select event_type || ':' || outcome from security_audit_event where event_type like 'PASSWORD%' order by id", String.class))
                .containsExactly("PASSWORD_RESET_REQUESTED:SUCCESS", "PASSWORD_RESET_COMPLETED:SUCCESS", "PASSWORD_RESET_COMPLETED:FAILURE");
    }

    @Test
    void aWeakNewPasswordWithAValidResetLinkIsAuditedAndKeepsTheLink() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = new Browser();
        b.post("/api/v1/auth/password/forgot", Map.of("email", EMAIL));
        String token = lastToken();
        // long enough to pass the first check, but it is the account's own email, which only the second check can see
        assertThat(status(b.post("/api/v1/auth/password/reset", Map.of("token", token, "newPassword", EMAIL)))).isEqualTo(422);
        assertThat(jdbc.queryForList("select detail::text from security_audit_event where event_type = 'PASSWORD_RESET_COMPLETED' and outcome = 'FAILURE'", String.class).toString())
                .contains("WEAK_PASSWORD");
        assertThat(status(b.post("/api/v1/auth/password/reset", Map.of("token", token, "newPassword", "a brand new passphrase")))).isEqualTo(204);
    }

    @Test
    void theSuccessfulSignInIsRecordedOnlyOnceTheSessionExists() throws Exception {
        activeAccount(EMAIL, PASSWORD);
        Browser b = new Browser();
        assertThat(status(b.post("/api/v1/auth/login", login(EMAIL, PASSWORD)))).isEqualTo(200);
        assertThat(b.sessionId()).isNotNull();
        assertThat(count("select count(*) from security_audit_event where event_type = 'LOGIN' and outcome = 'SUCCESS'")).isEqualTo(1);
        assertThat(count("select count(*) from spring_session")).isEqualTo(1);
    }

    @Test
    void aFailedSignInIsStillRecordedAlthoughTheRequestFails() throws Exception {
        new Browser().post("/api/v1/auth/login", login("nobody@example.com", PASSWORD));
        assertThat(count("select count(*) from security_audit_event where event_type = 'LOGIN' and outcome = 'FAILURE'")).isEqualTo(1);
    }

    @Test
    void auditRowsCannotBeChangedOrDeleted() throws Exception {
        new Browser().post("/api/v1/auth/login", login("nobody@example.com", PASSWORD));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("update security_audit_event set outcome = 'SUCCESS'")).hasMessageContaining("immutable");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("delete from security_audit_event")).hasMessageContaining("immutable");
    }

    @Test
    void theAuditKeepsTheClientAddressAndATruncatedUserAgent() throws Exception {
        Browser b = new Browser();
        b.get("/api/v1/auth/csrf");
        MockHttpServletRequestBuilder login = MockMvcRequestBuilders.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(login("nobody@example.com", PASSWORD)))
                .cookie(b.jar.values().toArray(new Cookie[0])).header("X-XSRF-TOKEN", b.jar.get("XSRF-TOKEN").getValue())
                .header("User-Agent", "x".repeat(500)).with(r -> {
                    r.setRemoteAddr("203.0.113.9");
                    return r;
                });
        mvc.perform(login);
        Map<String, Object> row = jdbc.queryForMap("select client_ip, user_agent from security_audit_event");
        assertThat(row.get("client_ip")).isEqualTo("203.0.113.9");
        assertThat(row.get("user_agent").toString()).hasSize(200);
    }

    // ---- mail ---------------------------------------------------------------------------------------------

    @Test
    void aMailServerFailureDoesNotBreakRegistration() throws Exception {
        doAnswer(call -> {
            throw new org.springframework.mail.MailSendException("smtp is down");
        }).when(mail).send(any(SimpleMailMessage.class));
        MvcResult r = new Browser().post("/api/v1/auth/register", register(EMAIL, PASSWORD));
        assertThat(status(r)).isEqualTo(202);
        assertThat(count("select count(*) from app_user")).isEqualTo(1);
        // the person can ask for another link
        doAnswer(call -> {
            sent.add(call.getArgument(0));
            return null;
        }).when(mail).send(any(SimpleMailMessage.class));
        new Browser().post("/api/v1/auth/resend-verification", Map.of("email", EMAIL));
        assertThat(sent).hasSize(1);
    }

    @Test
    void theApiKeepsRejectingUnknownAuthPaths() throws Exception {
        Browser b = new Browser();
        assertThat(status(b.get("/api/v1/auth/unknown"))).isIn(401, 403, 404);
        assertThat(status(b.postWithoutCsrf("/api/v1/auth/unknown", null))).isIn(401, 403, 404);
    }
}
