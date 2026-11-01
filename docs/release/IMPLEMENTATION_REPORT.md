# Implementation report

Leadership Intelligence Scoring Engine and executive dashboard. Status at the end of the quality and release phase.

## What was built

**Backend** (Java 21, Spring Boot 3.5, PostgreSQL, Flyway; `src/main/java/com/company/leadershipintelligence/`)

- A deterministic, explainable scoring engine: normalization, signal derivation, coverage checks, four dimensions, imbalances, configurable patterns with evidence and confidence, five penalty strategies with six stacking policies, weighted LIS, interpretation, and a versioned calculation trace. `BigDecimal` only; identical inputs, configuration and engine version give identical results.
- Versioned, immutable configuration (platform default, template, organization, tenant layers; lifecycle, merge, hash, tenant-keyed cache). Active versions and scoring history are protected by database triggers.
- Run persistence with idempotency, tenant isolation, failed-run recording and a reproducible input/configuration identity.
- A REST API under `/api/v1` with JWT security (roles, organizations, tenant from the token only): scoring, results, explanation, dimensions, patterns, penalties, imbalances, recommendations, catalog, history, comparison, trends, dashboard and source answers.
- Observability: twelve metrics (Prometheus), spans, ECS JSON logs, an access audit for explanation and answer reads. No raw answer is ever logged.

**Frontend** (`frontend/`: React 19, Vite, TypeScript 5.9, Tailwind 4, TanStack Query, Zustand, React Hook Form + zod, Recharts)

- OIDC authorization code with PKCE, organization selector, role-aware interface, typed API client generated from the backend OpenAPI document.
- Executive dashboard: LIS hero with band gauge and comparison, four dimension cards, risk overview, pattern overview (group by dimension, severity, confidence) with evidence, penalty overview (applied, suppressed, ineligible, not triggered), recommendations traced to patterns, five trend views.
- Explainability: an eight-step explanation with a waterfall, dimension detail with source-answer drill-down, pattern detail with a readable condition tree, penalty detail (requested against effective), and run comparison with an explicit "what differs" table.

**Operations**: `docker-compose.yml` (PostgreSQL, mock identity provider, app, web UI), a hardened nginx image, a Postman collection of every endpoint (34 requests), Playwright suites against the real stack.

## Principles that held

- The frontend never calculates a score, penalty, confidence or severity. An ESLint rule fails the build on arithmetic with score-like values outside `utils/presentation.ts`; waterfall bars use each stored value and lay earlier values out as invisible spacers instead of summing them.
- Labels, names, descriptions and explanations come from the versioned configuration catalog (decisions D25, D39). Recommendations come from the catalog; nothing is generated.
- A suppressed penalty is always shown, with its requested amount, reason and winner; a candidate is never presented as an applied deduction.
- Runs scored on different configurations, algorithm versions or template versions are never compared without naming the difference (D27).
- Production code contains no test data (ESLint `no-restricted-imports`, plus `npm run check:no-mocks` over sources and the built bundle).

## Decisions made on the way

All recorded in `docs/implementation/ARCHITECTURE_GAPS.md` (D1 to D46). The ones a reviewer should know:

| Decision | Summary |
|---|---|
| D31 | Layered architecture (`api`, `service`, `domain`, `repository`, `config`) instead of hexagonal; enforced by ArchUnit |
| D39 | Versioned presentation catalog (names, descriptions, explanations, score bands) read from the run's own stored configuration |
| D40 | Organization trends computed from stored findings only |
| D42 | Source answers readable by OWNER, ADMIN, ANALYST only, audited without content |
| D43 | Deterministic e2e seed, a sandbox organization for mutating checks, an extra illustrative dev pattern/penalty pair that shows suppression |
| D44 | Optional issuer and audience validation of access tokens |
| D45 | Answers that represent a member, a team or the organization: combined per question after normalization, minimum responses, participation in confidence, one organization result and one result per team. A member is an input scope only: nothing is output, ranked or listed for an individual |

## Answers from several people (added after the first release run)

The engine now accepts `responses` instead of `inputs`: each answer says whether it represents one member, a team or the organization. Answers are normalized one by one and combined per question (median by default); a question with fewer than five responses counts as missing, never zero, and a team left without enough data has no score. Scoring stores one organization run and one run per team in one transaction. Confidence includes a participation factor when respondent counts are known. The dashboard has a scope switcher, a panel that explains how the answers were combined, and a clear message instead of a number for a team below the minimum. Requests with one answer per question behave exactly as before. Everything, including the unvalidated defaults, is in `docs/implementation/RESPONSE_SCOPES_PROPOSAL.md`.

## The platform configuration as a JSON file (added after the first release run)

The scoring configuration is now one JSON document read when the application starts, and an initial version is bundled (`src/main/resources/config/initial-platform-default.json`): 20 questions, 17 signals, 16 patterns with their penalties, an imbalance rule, overall weights and score bands, recommendation text, names and explanations for everything the interface shows, and the rules for combining answers from several people. It is validated like any configuration and activated with an approval record; an invalid file stops the application; an unchanged file changes nothing on restart; a changed file becomes a new version only when asked (`SYNC`). Identifiers and suggested maximum deductions follow the specification's catalog, and the document lists what was authored beyond it. All of it is unvalidated. See `docs/configuration/INITIAL_CONFIGURATION.md`.

## Platform administration (added after the first release run)

A platform administrator, a role that belongs to no tenant, can create tenants and list them, in the product and through `/api/v1/platform/tenants`. A tenant is an id (permanent) and a name. The role is kept out of the tenant `Role` enum on purpose, so no "every role may read" rule can include it; the tenant roles cannot reach these endpoints; creation and list reads are audited. See decision D46.

## Defects found by the later test layers (and fixed)

| Found by | Defect |
|---|---|
| Playwright sign-out test | Removing the user raised an event whose handler removed the user again: an endless loop |
| Real-data e2e run | Boolean evidence flags were rejected by the response schema and the page reported "Cannot reach the service"; unexpected responses now have their own message |
| axe | Chart surfaces were focusable inside `aria-hidden` containers; low-contrast faint text; link names that did not contain their visible text |
| Viewport test | A group-by control and screen-reader-only text inside table wrappers caused horizontal scrolling on phones |
| Header test | The web server and the API both sent security headers, producing duplicates |
| Answers-from-several-people tests | The pattern detail page would have shown one respondent as "100%" and an unknown count as "null"; the validation hook for the request body, named after the field, broke every request until it became a class-level constraint; a limitation I wrote claimed a team without a run vanished from later reads, which a test disproved |

## Review summary

- **Tenant isolation**: enforced at the repository, service and API layers and re-checked end to end (another tenant gets 404 on every run, answer, explanation, comparison and list; empty lists elsewhere).
- **Authorization**: write, evidence and operations roles enforced by the backend and mirrored (not relied on) by the interface.
- **Error handling**: standard error body with a correlation id; server faults never expose internals (checked for stack traces, framework names and SQL fragments); every list, section and page has loading, empty, error and retry states.
- **Privacy**: raw answers appear only through `GET /scoring-runs/{id}/answers` for evidence roles; they are absent from results, traces, logs and the audit table.
- **Determinism**: re-scoring reuses the run; recalculation reproduces every score, hash and finding (tested on the running system).

See `TEST_RESULTS.md` for numbers, `KNOWN_LIMITATIONS.md` for what is not done, `DEPLOYMENT_GUIDE.md` for running it, and `REQUIREMENTS_TRACEABILITY.md` for the brief mapped to components and tests.
