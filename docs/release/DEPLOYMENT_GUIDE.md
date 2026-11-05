# Deployment guide

Read `KNOWN_LIMITATIONS.md` first: items 1, 2 and 4 must be resolved before a production deployment, and `SECURITY_REVIEW_IDENTITY.md` lists what the identity design leaves to the operator.

## Components

| Component | Image or artifact | Notes |
|---|---|---|
| API | Spring Boot jar (`mvn -B package`), `Dockerfile` in the repository root | Java 21 runtime, port 8080 |
| Database | PostgreSQL 16 | schema is created and validated by Flyway at start (`spring.jpa.hibernate.ddl-auto=validate`) |
| Web UI | `frontend/Dockerfile` (static build served by nginx) | port 80; proxies `/api/` to the API host `app:8080` |
| Mail server, Redis | an SMTP server and, for more than one API instance, Redis | accounts need email (confirmation, reset, invitations); Redis holds only the sign-in throttle counters |

## Local stack

```text
docker compose up -d --build        PostgreSQL, Redis, Mailpit (inbox :8025), API (:8080), web UI (:3000)
docker compose down -v              also removes the database (needed after changing the dev sample configuration)
cd frontend && npm run e2e          seeds the demo series, then runs the browser tests (signed in as the demo accounts)
```

The `dev` profile seeds two demo tenants, the illustrative sample configuration and demo accounts (`<name>@demo.test`: owner, admin, leader, analyst, viewer, org1-viewer, other-admin, sandbox-admin, sandbox-leader, assess-admin, assess-owner, respondent1 to respondent6, platform-admin, platform-admin-2, platform-admin-3; all with the password `demo-password-1234`). **Never run the `dev` profile in production.**

## API configuration

| Setting (environment variable) | Required | Meaning |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | yes | JDBC URL and credentials |
| `SPRING_PROFILES_ACTIVE` | no | leave unset in production (never `dev`) |
| `lis.logging.include-tenant-id` | no | `false` by default: tenant ids stay out of logs |
| `management.endpoints.web.exposure.include` | no | `health,info,metrics,prometheus`; metrics and Prometheus are read with an API token of the ADMIN role (a session always has a company, a scraper has none) |
| `lis.engine.version`, `lis.engine.algorithm-version` | no | recorded with every run |
| `lis.configuration.approval.require-second-person` | no | `true` by default: a configuration version must be approved by someone who did not create, edit or submit it. With one platform administrator the platform default cannot be changed in the product; appoint two, or set this to `false` knowingly |
| `lis.configuration.preview.use-recent-assessments` | no | `true` by default: owners and administrators may preview a draft against their tenant's recent assessments (in memory, audited). `false` previews fixtures only |
| `lis.configuration.preview.max-assessments` | no | `50`: how many recent assessments a preview reads |

Roles (a membership holds one or more of them): `OWNER`, `ADMIN`, `ENGINEERING_LEADER`, `ANALYST`, `VIEWER`; see "Respondents" below for the role of people who answer questionnaires. Building questionnaires, keeping the roster and running assessments need OWNER or ADMIN. Writes need OWNER, ADMIN or ENGINEERING_LEADER; source answers need OWNER, ADMIN or ANALYST; evidence values are redacted for VIEWER; choosing an explicit configuration version needs OWNER or ADMIN. The company comes from the `X-Company-Id` header and is checked against an active membership on every request; the organizations a member may reach are part of the membership.

Database: create an empty database and a user that can create tables in it. Flyway applies eleven migrations (`V1` to `V11`; `V11` adds accounts, one-time tokens, the security audit and the session store; `V10` adds the review record of a configuration version and the fixtures of a layer; `V8` adds the questionnaire library with a trigger that keeps a published version immutable; `V9` adds teams, people, assessment runs, participants, drafts and the table of submitted answers, which has no column that names a person; `V7` lets the audit hold a row without a tenant; `V6` adds the scopes of answers and runs and keeps every earlier row as an organization-level answer or run, so upgrading a populated database is safe, which was tested). Scoring history and active configuration versions are protected by triggers; back up the database like any system of record. Run migrations from one instance at a time.

## Web UI configuration

The image takes no build arguments and no runtime settings: the browser talks only to its own origin, and nginx proxies `/api/` to the API host `app:8080`.

The content security policy is `default-src 'self'` with `connect-src 'self'`: no other origin is allowed.

The container sends a Content Security Policy, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy` and `Cross-Origin-Opener-Policy`, gzips text assets, caches fingerprinted assets for a year and never caches the application shell. Terminate TLS in front of it and add `Strict-Transport-Security` there. Adjust the proxy target in `frontend/nginx/default.conf.template` if the API is not reachable as `app:8080`.

## Accounts, sessions and email

The application owns accounts, sessions and company memberships (see `docs/implementation/IDENTITY_PROPOSAL.md`, decisions D65 to D74); callers that are not a browser use API tokens. Migration `V11` adds `app_user`, `auth_token`, `security_audit_event` (append-only) and the two tables of Spring Session.

| Setting (environment variable) | Default | Meaning |
|---|---|---|
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | unset | the SMTP server; with no host, no email is sent (a warning is logged). Add `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true` and `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true` for a server that needs them |
| `LIS_WEB_BASE_URL` | `http://localhost:3000` | where the web application is served; the links in emails start with this |
| `LIS_MAIL_FROM` | `no-reply@localhost` | sender address; set up SPF and DKIM for its domain or the messages will land in spam |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | `true` | the session cookie is sent over HTTPS only; set `false` only for plain-HTTP local use |
| `SPRING_SESSION_TIMEOUT` | `30m` | idle limit of a session |
| `LIS_RATE_LIMIT_STORE` | `memory` | where the sign-in and account throttle counters live: `memory` (this process, one instance) or `redis` (shared by every instance) |
| `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD` | unset | the Redis server for `LIS_RATE_LIMIT_STORE=redis` (also `SPRING_DATA_REDIS_SSL_ENABLED=true` for TLS). Redis holds counters only, needs no persistence, and is not part of the health check: when it is down the limits keep applying per instance, a warning is logged and `auth.ratelimit.fallback` counts the calls. Tune the limits with `lis.rate-limit.*` (see `API_CONTRACT.md`) |
| `LIS_FORWARD_HEADERS` | `none` | set `framework` behind a reverse proxy so the client address (audit, throttles) and the cookie Secure flag come from `X-Forwarded-*`; leave `none` when clients reach the app directly |
| `LIS_BOOTSTRAP_ADMIN_EMAIL` | unset | creates the first platform administrator: when no active one exists at startup, this account gets the flag (created already verified, with a password nobody knows, and sent a link to choose one that works for 1 hour; `forgot password` issues a new one). Does nothing once an administrator exists, so it is safe to leave set |
| `LIS_IDENTITY_SESSION_ABSOLUTE` | `8h` | longest life of a session |
| `LIS_IDENTITY_PASSWORD_STRENGTH` | `12` | bcrypt cost; raise it as hardware gets faster, never lower it in production |
| `LIS_IDENTITY_VERIFICATION_TTL`, `LIS_IDENTITY_RESET_TTL` | `24h`, `1h` | how long the emailed links work |
| `LIS_AUDIT_HASH_KEY` | random per start | secret that keys the hash stored for an email that has no account; set it to compare across restarts |

Serve the application and the web UI from the **same origin** (the bundled nginx proxies `/api`), because the session and CSRF cookies are not shared across origins. Terminate TLS in front and forward the original client address. The local compose stack adds Mailpit: every email is caught at http://localhost:8025.

Every screen of a company lives under `/c/<company>/...`; the address of a company screen (`/c/acme/dashboard`) is served by the same `index.html` fallback as every other address, the content security policy needs no change, and switching company is a full page load. Set `LIS_WEB_BASE_URL` to the address people open, because the links in confirmation, reset and invitation emails start with it.

## The platform administrator

Creating and listing tenants is for the platform administrator: an account with the platform flag. It belongs to no company (no membership), so it reaches nothing tenant-scoped, and the tenant roles reach nothing of platform administration. The first one is created at startup from `LIS_BOOTSTRAP_ADMIN_EMAIL` (when no active one exists), and that person sets a password through the emailed link; give the flag only to the few people who operate the platform. In the product they land on the Tenants screen (list, filter, create, and invite the first owner); the same operations exist as `GET` and `POST /api/v1/platform/tenants`. Each creation and each list read is recorded in `scoring_access_audit` with the account. A tenant id is permanent. Nothing renames, disables or deletes a tenant (limitation 4).

## Respondents

People who answer questionnaires are not managers of the product. Owners and administrators invite them from the People screen ("Invite to answer"): the person receives an email, registers or signs in with that address, confirms it, and joins the company with the role `RESPONDENT` and no organizations. The product matches the account to the roster entry whose e-mail address equals the **confirmed** address of the account (lower-cased) in that company, so:

- an address is trusted only after the application confirmed it by a link sent to it, so nobody can answer as another person by typing their address (this closed limitation 45);
- a respondent reaches `/api/v1/respond/**` and nothing else, and the tenant roles do not reach it; a person who also administers has two memberships in the same company only if invited twice, so prefer a separate address;
- a person who is not on the roster has nothing to answer and sees an empty list.

Owners and administrators keep the roster in the product (People) and start runs (Assessment runs). The roster holds names and e-mail addresses, which are personal data with no retention rule yet (limitation 44). The `dev` profile has the demo accounts `respondent1` to `respondent6` (`respondentN@demo.test`) and `assess-admin` (an administrator of the scratch organization `org-assess`), for the development stack and the browser tests only.

## Changing the configuration while it runs

Scoring configuration can be changed in the product without a restart (decisions D52 to D58). The platform administrator changes the platform default through `/api/v1/platform/configuration`; owners and administrators change their tenant override and the overrides of their organizations through `/api/v1/configuration` (analysts may read). A change is a **draft**, which is validated (every problem is listed), previewed against the layer's **fixtures** and, where allowed, the tenant's recent assessments, and submitted; a **different person** approves it with a note and, ideally, a calibration reference. Approval is refused while a fixture fails. Active versions are never edited: a mistake is rolled back by copying an earlier version as a new draft. Scores already stored never change.

Things to arrange: **two platform administrators** (accounts with the platform flag for at least two people, never for tenant users), at least two owners or administrators per tenant that will change its own configuration, and a decision about the calibration reference (a dataset, pilot or review) that a version should name before it is trusted; a version without one is shown as unvalidated. The initial file is no longer the only way in: `SYNC` still activates a changed file as a new version, without a second person, as the startup system actor, so prefer the product for changes after the first start. The starter fixtures beside the bundled file (`initial-platform-default.fixtures.json`) are added once; edit them to match your own platform file, because a fixture that no longer holds blocks approval. The configuration cache is per instance (limitation 16), so another instance can serve an older version for up to five minutes after an approval.

## Putting a configuration in place

The platform default must be ACTIVE before the first assessment is scored (otherwise scoring returns `CONFIGURATION_VERSION_NOT_FOUND`). In this version it is a JSON file read when the application starts; nothing else is needed to load it.

| Setting (environment variable) | Default | Meaning |
|---|---|---|
| `LIS_CONFIGURATION_INITIAL_ENABLED` | `true` | read the file at startup |
| `LIS_CONFIGURATION_INITIAL_LOCATION` | `classpath:config/initial-platform-default.json` | the bundled initial configuration, or `file:/etc/lis/platform-default.json` for your own |
| `LIS_CONFIGURATION_INITIAL_MODE` | `IF_MISSING` | `IF_MISSING`: activate the file only if nothing is active (a different active version is left alone, with a warning). `SYNC`: also activate a changed file as a new version and archive the old one |

On the first start against an empty database the application validates the file, activates it as version 1 (approver `initial-configuration`) and logs it. A file that is missing, not JSON or invalid stops the application with the reason. On later starts an unchanged file changes nothing. To change the configuration, edit a copy of the file, point `LIS_CONFIGURATION_INITIAL_LOCATION` at it and start once with `LIS_CONFIGURATION_INITIAL_MODE=SYNC`; the previous version is archived and runs already stored keep the version they used. An active version is never edited. Start one instance first when several share a database, as for the Flyway migrations.

The bundled file is an initial, unvalidated proposal: its contents, the choices to review first and how to change them are in `docs/configuration/INITIAL_CONFIGURATION.md`. A configuration document contains `questions`, `signals`, `dimensions`, `patterns`, `penalties`, `imbalances`, `overall` and the optional `recommendations` and `presentation` catalogs and `responses` block (`defaultMethod` MEDIAN or MEAN, `minResponses`, `signalShare`, `fullConfidenceResponses`; each question may set its own `aggregation`; every default is unvalidated and decides how answers from several people are combined, when a question counts as missing, and how participation lowers confidence). `src/main/resources/dev/sample-platform-default.json` is a separate illustrative sample used only by the `dev` profile. Tenants, templates and organizations may override fields in their own layer; a run records the exact layer versions and the resulting hash.

Tenants are created by the platform administrator in the product (see above) or through `POST /api/v1/platform/tenants`. To try the bundled configuration locally without the `dev` profile: `docker compose up -d postgres mailpit redis`, run `LIS_BOOTSTRAP_ADMIN_EMAIL=you@example.test mvn spring-boot:run` (with `SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT=1025`), open the reset link that arrives in Mailpit (http://localhost:8025), set a password, sign in and create a tenant (the `dev` profile would seed two demo tenants and use its own sample instead).

## Verifying a deployment

1. `GET /actuator/health` returns `UP`.
2. Signed in as a member (or with an API token): `GET /api/v1/organizations/{org}/dashboard` returns 200 (or an empty dashboard). As an administrator, `GET /api/v1/questionnaires` returns 200; as a respondent, `GET /api/v1/respond/runs` returns 200 and `GET /api/v1/runs` returns 403. Without a session or token every `/api/v1` call except sign-in and registration returns 401.
3. `newman run docs/postman/lis.postman_collection.json` against a non-production environment started with the `dev` profile (the collection signs in as the demo accounts, which exist only there).
4. In a browser: sign in, open the dashboard, open the explanation of a run, confirm the notice text appears with the score.
5. `GET /actuator/prometheus` with an API token of the ADMIN role (create it on the API tokens screen) shows `scoring_requests_total` and the `scoring_duration_seconds` timers; with a session it is refused, because a session always has a company.

## Operating notes

- **Scaling**: the API is stateless apart from a per-instance configuration cache (see limitation 16). Scale reads freely; scoring is synchronous and takes tens of milliseconds for the sample configuration.
- **Logs**: ECS JSON on stdout. Raw answers are never logged. Correlate with the `X-Correlation-Id` response header, which appears in error bodies and in the interface as a reference.
- **Audit**: `scoring_access_audit` records who read an explanation or the source answers of a run (never the content); configuration actions are audited separately. Tables are append-only.
- **Metrics**: twelve metrics as named in specv3 section 34; tags are low-cardinality (no tenant, organization, assessment or run).
- **Upgrading**: ship new configuration as a new version and recalculate with an explicit version if history needs to be reproduced; engine and algorithm versions are recorded with each run and drive comparability.
- **Rollback**: application images can be rolled back; migrations are forward-only, so take a database backup before upgrading.
- **Secrets**: only database credentials and (optionally) NVD or registry keys are secrets. The web UI contains none.
