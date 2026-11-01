# Deployment guide

Read `KNOWN_LIMITATIONS.md` first: items 1 to 4 must be resolved before a production deployment.

## Components

| Component | Image or artifact | Notes |
|---|---|---|
| API | Spring Boot jar (`mvn -B package`), `Dockerfile` in the repository root | Java 21 runtime, port 8080 |
| Database | PostgreSQL 16 | schema is created and validated by Flyway at start (`spring.jpa.hibernate.ddl-auto=validate`) |
| Web UI | `frontend/Dockerfile` (static build served by nginx) | port 80; proxies `/api/` to the API host `app:8080` |
| Identity provider | your OIDC provider | must issue the token contract below; the mock provider in `docker-compose.yml` is for local use only |

## Local stack

```text
docker compose up -d --build        PostgreSQL, mock identity provider (:8081), API (:8080), web UI (:3000)
docker compose down -v              also removes the database (needed after changing the dev sample configuration)
cd frontend && npm run e2e          seeds the demo series, then runs all browser tests
```

The `dev` profile seeds two demo tenants and the illustrative sample configuration. **Never run the `dev` profile in production.**

## API configuration

| Setting (environment variable) | Required | Meaning |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | yes | JDBC URL and credentials |
| `LIS_SECURITY_JWK_SET_URI` | yes | JWK set of the identity provider; without it every token is rejected |
| `LIS_SECURITY_ISSUER` | strongly recommended | exact `iss` value tokens must carry; without it only signature and validity period are checked (a warning is logged) |
| `LIS_SECURITY_AUDIENCE` | recommended | value that must appear in the token `aud` |
| `SPRING_PROFILES_ACTIVE` | no | leave unset in production (never `dev`) |
| `lis.logging.include-tenant-id` | no | `false` by default: tenant ids stay out of logs |
| `management.endpoints.web.exposure.include` | no | `health,info,metrics,prometheus`; metrics and Prometheus are limited to OWNER and ADMIN by the application |
| `lis.engine.version`, `lis.engine.algorithm-version` | no | recorded with every run |

Token contract: signed JWT with `sub`, `tenant_id` (the tenant; taken from nowhere else), `org_ids` (organizations the user may reach) and `roles` (`OWNER`, `ADMIN`, `ENGINEERING_LEADER`, `ANALYST`, `VIEWER`). Writes need OWNER, ADMIN or ENGINEERING_LEADER; source answers need OWNER, ADMIN or ANALYST; evidence values are redacted for VIEWER; choosing an explicit configuration version needs OWNER or ADMIN.

Database: create an empty database and a user that can create tables in it. Flyway applies seven migrations (`V1` to `V7`; `V7` lets the audit hold a row without a tenant; `V6` adds the scopes of answers and runs and keeps every earlier row as an organization-level answer or run, so upgrading a populated database is safe, which was tested). Scoring history and active configuration versions are protected by triggers; back up the database like any system of record. Run migrations from one instance at a time.

## Web UI configuration (build time)

| Build argument | Value |
|---|---|
| `VITE_OIDC_AUTHORITY` | issuer URL of your provider |
| `VITE_OIDC_CLIENT_ID` | the registered public client (authorization code with PKCE) |
| `VITE_DEV_PERSONAS` | must be `false` |

Runtime environment of the nginx container: `IDP_ORIGIN` is the identity provider's origin (scheme, host, port) and becomes `connect-src` in the Content Security Policy. Register `https://<ui-host>/auth/callback` as the redirect URI and `https://<ui-host>/login` as the post-logout URI.

The container sends a Content Security Policy, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy` and `Cross-Origin-Opener-Policy`, gzips text assets, caches fingerprinted assets for a year and never caches the application shell. Terminate TLS in front of it and add `Strict-Transport-Security` there. Adjust the proxy target in `frontend/nginx/default.conf.template` if the API is not reachable as `app:8080`.

## The platform administrator

Creating and listing tenants is for one role, `PLATFORM_ADMIN`, in the `roles` claim. It belongs to no tenant: its token has **no** `tenant_id` and no `org_ids`, so it can reach nothing tenant-scoped, and the tenant roles can reach nothing of platform administration. Grant it in the identity provider only to the few people who operate the platform, never to a tenant's users. In the product they land on the Tenants screen (list, filter, create); the same operations exist as `GET` and `POST /api/v1/platform/tenants`. Each creation and each list read is recorded in `scoring_access_audit` with the subject. A tenant id is permanent: choose the one your identity provider will issue as `tenant_id`. Nothing renames, disables or deletes a tenant (limitation 4).

## Putting a configuration in place

The platform default must be ACTIVE before the first assessment is scored (otherwise scoring returns `CONFIGURATION_VERSION_NOT_FOUND`). In this version it is a JSON file read when the application starts; nothing else is needed to load it.

| Setting (environment variable) | Default | Meaning |
|---|---|---|
| `LIS_CONFIGURATION_INITIAL_ENABLED` | `true` | read the file at startup |
| `LIS_CONFIGURATION_INITIAL_LOCATION` | `classpath:config/initial-platform-default.json` | the bundled initial configuration, or `file:/etc/lis/platform-default.json` for your own |
| `LIS_CONFIGURATION_INITIAL_MODE` | `IF_MISSING` | `IF_MISSING`: activate the file only if nothing is active (a different active version is left alone, with a warning). `SYNC`: also activate a changed file as a new version and archive the old one |

On the first start against an empty database the application validates the file, activates it as version 1 (approver `initial-configuration`) and logs it. A file that is missing, not JSON or invalid stops the application with the reason. On later starts an unchanged file changes nothing. To change the configuration, edit a copy of the file, point `LIS_CONFIGURATION_INITIAL_LOCATION` at it and start once with `LIS_CONFIGURATION_INITIAL_MODE=SYNC`; the previous version is archived and runs already stored keep the version they used. An active version is never edited. Start one instance first when several share a database, as for the Flyway migrations.

The bundled file is an initial, unvalidated proposal: its contents, the choices to review first and how to change them are in `docs/configuration/INITIAL_CONFIGURATION.md`. A configuration document contains `questions`, `signals`, `dimensions`, `patterns`, `penalties`, `imbalances`, `overall` and the optional `recommendations` and `presentation` catalogs and `responses` block (`defaultMethod` MEDIAN or MEAN, `minResponses`, `signalShare`, `fullConfidenceResponses`; each question may set its own `aggregation`; every default is unvalidated and decides how answers from several people are combined, when a question counts as missing, and how participation lowers confidence). `src/main/resources/dev/sample-platform-default.json` is a separate illustrative sample used only by the `dev` profile. Tenants, templates and organizations may override fields in their own layer; a run records the exact layer versions and the resulting hash.

Tenants are created by the platform administrator in the product (see above) or through `POST /api/v1/platform/tenants`. To try the bundled configuration locally without the `dev` profile: `docker compose up -d postgres mock-oauth2`, run `mvn spring-boot:run`, sign in as the `platform-admin` persona and create a tenant (the `dev` profile would seed two demo tenants and use its own sample instead).

## Verifying a deployment

1. `GET /actuator/health` returns `UP`.
2. With a token of a real user: `GET /api/v1/organizations/{org}/dashboard` returns 200 (or an empty dashboard).
3. `newman run docs/postman/lis.postman_collection.json` against a non-production environment (set `baseUrl` and `authUrl`; the persona mechanism of the collection works with the mock provider only, so supply tokens for a real provider).
4. In a browser: sign in, open the dashboard, open the explanation of a run, confirm the notice text appears with the score.
5. `GET /actuator/prometheus` as an OWNER or ADMIN shows `scoring_requests_total` and the `scoring_duration_seconds` timers.

## Operating notes

- **Scaling**: the API is stateless apart from a per-instance configuration cache (see limitation 16). Scale reads freely; scoring is synchronous and takes tens of milliseconds for the sample configuration.
- **Logs**: ECS JSON on stdout. Raw answers are never logged. Correlate with the `X-Correlation-Id` response header, which appears in error bodies and in the interface as a reference.
- **Audit**: `scoring_access_audit` records who read an explanation or the source answers of a run (never the content); configuration actions are audited separately. Tables are append-only.
- **Metrics**: twelve metrics as named in specv3 section 34; tags are low-cardinality (no tenant, organization, assessment or run).
- **Upgrading**: ship new configuration as a new version and recalculate with an explicit version if history needs to be reproduced; engine and algorithm versions are recorded with each run and drive comparability.
- **Rollback**: application images can be rolled back; migrations are forward-only, so take a database backup before upgrading.
- **Secrets**: only database credentials and (optionally) NVD or registry keys are secrets. The web UI contains none.
