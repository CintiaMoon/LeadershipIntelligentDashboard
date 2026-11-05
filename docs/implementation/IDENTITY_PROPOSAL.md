# Proposal D65 to D74: built-in accounts, sessions and company memberships

Status: **DELIVERED** (phases P1 to P7 are merged or in review; see `docs/release/SECURITY_REVIEW_IDENTITY.md`). All eight questions of section 8 were accepted with their defaults, and each phase started only on the go-ahead of the user. `specv3.md` Sections 25 and 33 and decisions D19, D20 and D46 describe a service that
only validates tokens from an external identity provider. This proposal replaces that with accounts, passwords, server-side
sessions and company memberships owned by the application. Under the precedence rule this is new behavior and needs approval first.
No code is written until you approve.

## 1. Decisions already taken by the user

| # | Decision |
|---|---|
| 1 | A **company is the existing tenant**. Organizations stay beneath it. |
| 2 | The tenant stays server-trusted: the company in the URL is accepted only after the server checks an **ACTIVE membership**. |
| 3 | Non-browser callers use a **bearer token**. |
| 4 | The platform administrator is a **flag on the user**. |
| 5 | A respondent is a user with a RESPONDENT-only membership; the role stays outside the `Role` enum; answers stay unlinked from people. |
| 6 | Email is sent through an **SMTP service**. |
| 7 | **Redis** is the rate-limit store. |

## 2. Design principles

1. **The session represents a user, never a company.** It holds the user's UUID and nothing else about access.
2. **Access is derived per request:** the session (or API token) gives the user; the company named by the request gives the
   membership; the membership gives roles and organizations. Nothing about tenant, role or organization is trusted from the client.
3. **The rest of the system does not change.** `CurrentUser(tenantId, subject, roles, organizationIds)` stays the single object that
   controllers and services read. Only how it is built changes (from a verified token before; from user plus membership now).
   Tenant-scoped repositories, the role matrix, redaction, scoring, reports and the anonymity rules are untouched.
4. **No account enumeration.** Registration, login, resend and password-reset answer the same way whether or not the account exists.
5. **Secrets are never stored or logged in clear:** passwords (hashed), verification, reset, invitation and API tokens (SHA-256 of a 256-bit random value).

## 3. Decisions

### D65. Accounts and passwords
- Table `app_user`: `id` (UUID, the principal), `email` (unique, case-insensitive, normalized), `display_name`, `password_hash`,
  `status` (`PENDING_VERIFICATION`, `ACTIVE`, `DISABLED`), `email_verified_at`, `platform_admin` (boolean, D69), timestamps.
- Hashing: Spring's `DelegatingPasswordEncoder` with bcrypt (cost 12) so the algorithm can change later without a forced reset.
  (Argon2id is stronger but needs an extra crypto library that the dependency scan would then track; see question 8.)
- Password policy (NIST style): 12 to 128 characters, no composition rules, a small blocklist of very common passwords, and not
  equal to the email. Passwords are never logged and never returned.
- Registration (`POST /api/v1/auth/register`) creates a `PENDING_VERIFICATION` user and **no company**. Always answers `202`.
- Login succeeds only when the password matches, `status = ACTIVE` and the email is verified. A wrong password, an unknown email, a
  pending or a disabled account all give the same `401` body and take comparable time (a dummy hash is verified for unknown emails).

### D66. Email verification and outgoing email
- Verification token: 256-bit random, stored as a hash, valid 24 hours, single use. `POST /auth/verify-email` activates the account.
  `POST /auth/resend-verification` always answers `202` and is rate limited.
- Email goes through Spring `JavaMailSender` and SMTP (`spring.mail.*`; credentials only in environment variables). Locally the
  compose stack adds Mailpit (SMTP on 1025, inbox on 8025). Messages are plain text with an HTML alternative, sent asynchronously;
  a failed send is logged and audited without the token, and the user can ask again.
- Links point at the web UI (`lis.web.base-url`), for example `/verify-email?token=...`.

### D67. Server-side sessions
- Spring Session JDBC on the existing PostgreSQL (tables created by Flyway). The cookie `LIS_SESSION` is `HttpOnly`, `Secure`
  (not in the local dev profile), `SameSite=Lax`, path `/`. The browser keeps no token.
- The session id is **rotated at login** (session fixation). Defaults: 30 minutes idle, 8 hours absolute. The principal name is the user UUID.
- `POST /auth/login`, `POST /auth/logout` (invalidates the session; idempotent), `GET /auth/me` (user, platform flag, memberships list).
- Changing or resetting a password **invalidates all sessions and API sessions of that user** except, on a change, the current one.
- Suspending or removing a membership takes effect on the next request because the membership is read on every request (one
  indexed lookup); there is no cached copy to go stale.

### D68. CSRF
- Session-authenticated requests use Spring's cookie token repository: cookie `XSRF-TOKEN` (readable by the SPA), header
  `X-XSRF-TOKEN` on every `POST`, `PUT`, `PATCH`, `DELETE`. The login request is protected too (the SPA first calls `GET /auth/csrf`).
- Requests authenticated with a bearer token are exempt: they carry no ambient credential. A request with both is treated as a session request.
- Allowed origins are an explicit list (`lis.web.allowed-origins`); no wildcard with credentials.

### D69. Companies, memberships and the company in the URL
- Table `company_membership`: `id`, `tenant_id`, `user_id`, `status` (`INVITED`, `ACTIVE`, `SUSPENDED`, `REMOVED`), timestamps, unique
  on tenant and user. Child tables hold the **roles** (a set, as today) and the **organization ids** of the membership. Roles may be the five
  `Role` values or the separate RESPONDENT value; the enum does not change.
- After login the UI reads `GET /auth/me` / `GET /api/v1/companies`, which return **only companies with an ACTIVE membership**.
  One company: go straight to its dashboard. Several: show a picker. None: a clear "you have no company yet" page.
- **The company is in the application URL:** `/c/{tenantId}/...` for every tenant screen (for example `/c/acme/dashboard`).
- **How the server uses it (this replaces "tenant only from the token", D19/D20/specv3 25.1):** the SPA sends the URL's company as
  header `X-Company-Id` on every API call. A filter resolves it for session requests: no ACTIVE membership for this user and that company
  gives `403 COMPANY_ACCESS_DENIED` (the same answer for a company that does not exist, so existence is not revealed); otherwise it builds
  `CurrentUser` from the membership. A missing header on a tenant endpoint is `400`. Organizations are checked against the membership's
  organizations exactly as `org_ids` is checked today. A body, query string or path can never override it.
- The platform administrator (flag on the user, D46 unchanged) gets the `PLATFORM_ADMIN` authority and **no** company: it reaches
  `/api/v1/platform/**` only, and a company header is ignored for it. Tenant endpoints stay closed to it.
- **Alternative:** put the company in every API path (`/api/v1/companies/{id}/...`). It is more explicit but renames every endpoint,
  the OpenAPI contract, the Postman collection and the tests (about 6 to 8 more days). Recommended: the header (question 2).
- A tenant is created by the platform administrator as today, now **with an owner email**: the platform sends the owner an invitation (D72).
  There is no self-service company creation (question 3).

### D70. Bearer tokens for non-browser callers
- The platform issues **opaque API tokens** (not JWTs): `POST /api/v1/api-tokens` (name, role, optional expiry, optional organizations),
  `GET` to list, `DELETE` to revoke. OWNER and ADMIN only; the secret is shown **once**; only a hash is stored.
- A token belongs to **one company** and to the user who created it, with a role no higher than the creator's, never OWNER, and never
  PLATFORM_ADMIN or RESPONDENT. Using it needs no `X-Company-Id` and no CSRF token. Its use writes the same access audit as a session.
- Tokens are checked on every request (hash lookup), honour revocation and expiry at once, and stop working if the creator's membership
  is no longer ACTIVE. They are rate limited separately (D71).
- The old external-JWT path (`lis.security.jwk-set-uri`) is kept behind a profile during the migration and removed in the last phase (section 6).
- If you meant "keep accepting JWTs from another issuer" instead, say so (question 1); it would reuse the current decoder and add no API-token screens.

### D71. Authentication abuse prevention (Redis)
- Redis is added to the compose stack and to the deployment guide (`spring-boot-starter-data-redis`, Lettuce). It holds **counters only**;
  sessions stay in PostgreSQL, so Redis is not a source of truth.
- Fixed starting limits, all in configuration and **not calibrated**:

| Endpoint | Key | Limit |
|---|---|---|
| login | per IP | 20 per 5 minutes |
| login | per account (email hash) | 5 failures per 15 minutes, then a growing delay (no permanent lock, to avoid locking out the real owner) |
| register, resend verification, forgot password | per IP and per email hash | 5 per hour each |
| password reset and email verification (token use) | per IP | 20 per hour |
| API token use | per token | 600 per minute |

- Over the limit: `429` with `Retry-After`, same shape as other errors. Every limit hit is audited.
- If Redis is unreachable, the limiter falls back to a conservative **in-process** counter and raises a warning metric; the credential
  endpoints do not fail open without any limit (question 6). The client IP comes from a trusted-proxy setting, never from a raw header.

### D72. Membership management and invitations
- `GET/POST /api/v1/members`, `PATCH /api/v1/members/{id}` (roles, organizations, status), `DELETE` (remove), and
  `POST /api/v1/members/{id}/resend-invitation`. The `Members` screen is for OWNER and ADMIN.
- Invitation: an INVITED membership plus an emailed single-use token (7 days). An existing user accepts after login; a new user registers
  with the token, which proves the mailbox, so the email is verified and the membership is activated in one step.
- Rules: OWNER may grant and remove every role. ADMIN may manage ENGINEERING_LEADER, ANALYST, VIEWER and RESPONDENT but not OWNER or
  ADMIN. Nobody changes their own roles or status. **A company always keeps at least one ACTIVE OWNER**; removing or demoting the last one is refused.
- A roster person can be invited as a respondent (RESPONDENT-only membership). The respondent is still matched to the roster by the
  **verified** email; the matching and the anonymity rules do not change. The respondent flow keeps no link between a person and an answer.

### D73. Password reset
- `POST /auth/password/forgot` always answers `202`. Token: 256-bit, hashed, valid 1 hour, single use; asking again invalidates earlier tokens.
- `POST /auth/password/reset` sets a new password under the same policy, **invalidates every session and API session of the user**,
  and sends a "your password was changed" email. A pending account that asks for a reset is sent a verification link instead.
- `POST /auth/password/change` (logged in; needs the current password) does the same except it keeps the current session.

### D74. Security audit
- Table `security_audit_event` (append-only, same style as the existing audit tables): time, event type, user id (when known), tenant id
  (when relevant), outcome, client IP, truncated user agent, and a small JSON detail. **Never** passwords, tokens or cookies. For an
  unknown email only a keyed hash is stored.
- Events: registered, verified, verification resent, login success and failure, logout, session expired, password reset requested and
  completed, password changed, membership invited, accepted, role changed, suspended, removed, company opened, access denied for a company,
  API token created, used for the first time in a day and revoked, rate limit hit, tenant created.
- OWNER and ADMIN read their own company's events through `GET /api/v1/security-audit`; the platform administrator reads platform events.
  Retention is a setting (default 400 days). The existing `scoring_access_audit` is unchanged; its `actor` becomes the user UUID.
- **Anonymity:** submitting a questionnaire writes **no** security event naming the respondent next to an answer or a run; login and
  logout of a respondent are logged like any other user's. A test asserts no audit or session row can connect a person to an answer.

## 4. What stays as it is

| Existing decision | Effect |
|---|---|
| Layered architecture, `ArchitectureTest` | New code goes to `api`, `service`, `domain` (rules only) and `repository` as before; identity JPA types stay in `repository`. |
| Tenant-scoped repositories and isolation tests | Unchanged; the isolation suite is extended to cover the company header and memberships. |
| Roles OWNER to VIEWER, role matrix, redaction rule (`!hasAnyRole(OWNER, ADMIN, ANALYST)`) | Unchanged. |
| D46 platform administration | Unchanged, except `PLATFORM_ADMIN` comes from the user flag and tenant creation takes an owner email. |
| D47 to D51 questionnaires, roster, runs, respondent anonymity | Unchanged; respondents now sign in through the application. |
| Error contract (`ApiErrorWriter` 401/403) | Kept; new codes are added to the API contract. |
| Scoring, configuration lifecycle, reports, idempotency | Untouched. |

## 5. What this replaces

D19 (resource server), D20 (organizations from `org_ids`; organizations now come from the membership), specv3 Sections 25.1, 25.5 and 33
(wording of "the tenant comes only from the authenticated token"; the intent, that the client cannot choose a tenant it has no right to, is kept),
and the OIDC code, persona picker and mock provider. This closes `KNOWN_LIMITATIONS` items 3, 26, 36 and 45 and replaces roadmap item B.

## 6. Delivery plan

Every phase is its own pull request and ends with a report; none starts before you say so.

| Phase | Scope | Days |
|---|---|---|
| P1 | Accounts, email verification, SMTP and Mailpit, login, logout, Spring Session JDBC, CSRF, password reset and change, audit foundation (D65 to D68, D73, part of D74) | 24 to 29 |
| P2 | Redis and abuse prevention (D71) | 6 to 7 |
| P3 | Memberships, company header filter, `GET /companies`, RBAC from memberships, platform flag, tenant creation with an owner invitation (D69) | 12 to 15 |
| P4 | Bearer API tokens (D70) | 5 to 6 |
| P5 | Membership management, invitations, security audit viewer (D72, rest of D74) | 9 to 12 |
| P6 | Frontend: register, verify, login, reset, company picker, `/c/:tenantId` routing, Members, API tokens, security audit screens; CSRF and 401 handling; OIDC code removed | 20 to 25 |
| P7 | Test migration (a helper that builds an authenticated user and company for the 715 existing tests), end-to-end and Postman rework, removal of the OIDC path and mock provider, docs, deployment guide, security hardening review | 15 to 19 |
| | **Total (plus about 3 days for this proposal)** | **91 to 113** |

The old JWT path stayed behind a profile until P7 so the tests and the end-to-end suite stayed green in P1 to P6; P7 removed it.
P6 can start once the P1 and P3 contracts are fixed and then runs in parallel with P4 and P5. Two engineers: about 10 to 12 weeks.

## 7. Risks

- **Tenant isolation:** the membership check is the new trust boundary. One resolver, one filter, and an extended isolation suite.
- **Cookie sessions:** fixation, CSRF and cookie flags are easy to get subtly wrong; a security review is part of P7.
- **Email deliverability:** production needs SPF, DKIM and a sending domain, which are operations tasks outside the code.
- **Out of scope:** multi-factor authentication, single sign-on, social login, a breached-password service, and self-service company creation.
  Each can be added later without changing this design.

## 8. Questions for you (each has a default that applies unless you object)

1. **Bearer token** means app-issued opaque API tokens (D70). Or do you want to keep accepting JWTs from an external issuer? *Default: API tokens.*
2. **Company in API calls:** header `X-Company-Id` set from the URL (recommended), or the company in every API path (+6 to 8 days)? *Default: header.*
3. **Who creates the first company and its first owner:** the platform administrator, with an owner email invitation. No self-service. *Default: yes.*
4. **First platform administrator:** created at startup from `LIS_BOOTSTRAP_ADMIN_EMAIL` when none exists, and sent a set-password link. *Default: yes.*
5. **Session lengths:** 30 minutes idle, 8 hours absolute. *Default: yes.*
6. **Redis down:** fall back to a conservative in-process limiter (recommended) or reject credential requests (`503`)? *Default: fall back.*
7. **Rate limits in D71** are starting values to tune after real traffic. *Default: yes.*
8. **Password hashing:** bcrypt cost 12 now, or Argon2id with an extra library? *Default: bcrypt.*
