# Security review of the identity design (identity phase P7)

Scope: accounts, sessions, company memberships, API tokens, the security audit and the throttles of decisions D65 to D74, after the
external identity provider was removed. Date of the checks: 2026-11-05, against the compose stack on a clean database (profile `dev`,
so the session cookie is not `Secure` in the live probes; the unit and integration tests run with the production default).

This is a review by the authors, with tests and probes against the running stack. It is **not** a penetration test and not an
independent audit. What it did not cover is listed at the end.

## What was checked, and what was found

| # | Check | How | Result |
|---|---|---|---|
| 1 | Session cookie flags | integration test (`AuthApiIntegrationTest`) and a live request | `LIS_SESSION`: `HttpOnly`, `SameSite=Lax`, `Secure` by default (`server.servlet.session.cookie.secure=true`; only the local compose stack turns it off) |
| 2 | CSRF cookie flags | live request | **Finding, fixed:** `XSRF-TOKEN` was sent with no `SameSite` and never `Secure`. It is now `SameSite=Lax` and `Secure` whenever the session cookie is (test added). It stays readable by script on purpose: the web application copies it into `X-XSRF-TOKEN` |
| 3 | CSRF coverage | tests and live requests | Every state-changing call on a session needs `X-XSRF-TOKEN`, **including sign-in itself** (403 `CSRF_TOKEN_INVALID` without it). A call with an API token or with no session is not subject to it (the former has no ambient credential, the latter is 401). Safe methods change nothing |
| 4 | Session fixation | test and live requests | Sign-in ends any session that came with the request and starts a new one; the CSRF token is renewed too. Two sign-ins gave two different session ids and the first id answered 401 afterwards. A session id chosen by an attacker is not accepted (test) |
| 5 | User enumeration | live requests | Wrong password and unknown email give the same status and body (`AUTHENTICATION_FAILED`, 401); response times were 364 ms and 333 ms in one sample, i.e. not distinguishable at this resolution (bcrypt is run for unknown addresses too). Register and forgot-password answer 202 for known and unknown addresses |
| 6 | Brute force | live requests | Five wrong passwords for one address, the sixth answered 429 with `Retry-After: 30`; the delay grows with repeated failures (D71). Per-IP and per-address limits for sign-in, registration, reset and token calls |
| 7 | Password rules | live request | Fewer than 12 characters: 422 `WEAK_PASSWORD`. Stored as bcrypt, cost 12 by default |
| 8 | Anonymous reach | live requests | With no session or token, every `/api/v1` path, `/actuator/*` except `health` and `info`, `/v3/api-docs`, `/swagger-ui*`, `/error` and unknown paths answer 401; nothing leaks framework or path details |
| 9 | Cross-origin | live preflight from another origin | No CORS headers are sent; the session is same-origin only (the bundled nginx proxies `/api`). The content security policy has `connect-src 'self'` |
| 10 | Secrets in logs | log search after a run that signed in, failed, was throttled and used tokens | No password, session id, CSRF token, API token, reset link or email address appeared (0 matches each). A failed email delivery is logged without address or link |
| 11 | Default Spring user | log of the test run | **Finding, fixed:** with the resource server gone Spring Boot created a default user and printed its random password at startup. The application checks passwords itself, so `UserDetailsServiceAutoConfiguration` is now excluded and the line is gone |
| 12 | Operations endpoints | live requests | **Behaviour to know, documented:** a session always has a company, and metrics (`/actuator/prometheus`, `/actuator/metrics`) and `/v3/api-docs` belong to none, so a browser session is refused (403). They are read with an API token (ADMIN for metrics); the Postman collection and `gen:api` do this |
| 13 | Bearer values | hardening test and Postman | A bearer value that is not an application API token (`lis_...`) is ignored and the request is 401. A revoked token stops working on the next request. A token is tied to its creator, never has the OWNER role, and cannot reach organizations its creator cannot |
| 14 | Tenant isolation | existing isolation tests, rewritten for the typed caller | The company comes from `X-Company-Id` and is accepted only with an ACTIVE membership, read on every request; a suspended or removed member loses access on the next request. A run, questionnaire or version of another company is 404, not 403 |
| 15 | Browser storage | browser test | Nothing is kept in `localStorage`; `sessionStorage` holds no credential or scoring result; the session cookie is not visible to script |
| 16 | Respondent anonymity | existing tests | Submitted answers carry the team and the question only; a test asserts the columns. Unchanged by P7 |
| 17 | Front-end dependencies | `npm audit --omit=dev` | 0 vulnerabilities |
| 18 | Demo accounts | code and compose | Created only with the `dev` profile (`@Profile("dev")`), never in production; their password is published (`demo-password-1234`) |

## Removed in P7

The external identity provider path: the OAuth2 resource server and its starter, `lis.security.*`, token validation, the OIDC client
and persona picker of the web application, the mock provider and its configuration, and the `CallerTokens` helper. There is now one
typed caller (`AuthenticatedCaller`), built by a session filter or the API token filter; controllers never read claims.

## What the operator must do

- **Terminate TLS** in front of the web container and add `Strict-Transport-Security` there. Set `LIS_FORWARD_HEADERS=framework`
  behind a proxy so the client address (audit, throttles) and the cookie `Secure` flag are correct, and **leave it `none`** when
  clients reach the application directly, or a client could pick its own address.
- Keep `SERVER_SERVLET_SESSION_COOKIE_SECURE` at its default (`true`).
- Use **Redis** for the throttles when more than one instance runs; with `memory` each instance counts alone.
- Provide **SMTP** with SPF and DKIM for the sender. Without it nobody can confirm an address or reset a password.
- Create the first **platform administrator** with `LIS_BOOTSTRAP_ADMIN_EMAIL`, appoint at least two (the second-person rule for
  the platform default needs them) and give the flag only to people who operate the platform.
- Never run the `dev` profile outside a developer machine.
- Set `LIS_AUDIT_HASH_KEY` if the audit's hashes of unknown addresses must be comparable across restarts.
- Scrape metrics with an API token of the ADMIN role; revoke it when the scraper is retired.

## Residual risks and gaps (not fixed in P7)

- **No second factor.** Sign-in is a password only. Not in the approved scope (D65 to D74).
- **No check of passwords against known breaches**, only a minimum length.
- **A session is not bound to a device or address.** A stolen session cookie works until it is ended (idle 30 minutes, absolute 8 hours by default) or the member is suspended.
- **No limit on concurrent sessions** and no screen to end the sessions of an account.
- **Throttle numbers are starting values**, not calibrated against real traffic (limitation 58).
- **The audit can be read, not exported**, and nothing alerts on an event type (limitation 60).
- **One account per person is assumed:** the second-person rule compares accounts, so one person with two addresses counts as two people (limitation 52).

## Not covered by this review

- No penetration test, fuzzing or load test of the sign-in path.
- No scan of the Maven dependencies for known vulnerabilities (the build has no scanner configured and the NVD feed was not used); run one in CI before a release.
- No review of the container image beyond what the `Dockerfile` does.
- No review of the email templates against phishing look-alikes, or of the mail provider.

## How to repeat the live checks

```text
docker compose up -d --build
curl -s -D - -c jar http://localhost:8080/api/v1/auth/csrf              cookie flags
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/env   expect 401
cd frontend && npm audit --omit=dev
mvn -B test -Dtest=AuthApiIntegrationTest                               cookie, fixation, CSRF, throttle and audit tests
cd frontend && npm run e2e && npm run e2e:accounts
npx newman run docs/postman/lis.postman_collection.json
```
