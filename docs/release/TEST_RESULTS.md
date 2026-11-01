# Test results

Every figure below comes from a command run during the release phase on a development machine (Windows 11, JDK 24 with `--release 21`, Node 24, Docker Engine 29). Nothing is estimated.

## Summary

| Layer | Command | Result |
|---|---|---|
| Backend unit and integration | `mvn -B verify` | **640 tests, 0 failures, 0 errors** (integration tests use PostgreSQL 16 in Testcontainers) |
| Frontend static analysis | `npm run lint`, `npm run typecheck` | clean (0 problems) |
| Frontend unit and component | `npm test` | **391 tests in 48 files, all pass** (statements 92.2 %, branches 82.2 %, functions 88.4 %, lines 94.1 %) |
| Frontend build | `npm run build`, `npm run check:no-mocks` | builds; no test-only code in sources or bundle |
| End to end (real stack, real browser) | `npm run e2e` | **67 tests, all pass** |
| API contract | `newman run docs/postman/lis.postman_collection.json` | **109 requests executed (81 in the collection; the rest are token calls), 126 assertions, 0 failures**, run twice in a row |

Answers from several people (decision D45) were added after the first release run: 53 backend tests, 37 frontend unit tests, 4 end-to-end tests and 3 Postman requests (the collection now has 37 requests; newman reports 42 executed requests because it also counts the token calls of the authentication script). The earlier figures were 460, 171, 45 and 40 executed requests.
| Frontend dependencies | `npm audit` | **0 vulnerabilities** (production and development) |

## Backend (640 tests)

Added with questionnaires, the roster, runs and respondents (decisions D47 to D51): **104 backend tests** over three phases. Phase 1 (49): the multi-select type, list-valued answers (input hash order-independent, stored form, request limits, answers view) and the `$replace` marker. Phase 2 (40): the normalizer, the publish validator (every rule), the generator (the layer merged over a platform default passes the configuration parser; no platform identifier leaks; every question kind maps with its direction and scores; the same document always gives the same layer), the copy of the platform default (the bundled 20-question configuration becomes a publishable draft whose layer re-parses), and 17 API tests against PostgreSQL (create, save with assigned identifiers, a draft that can never be published is refused, publishing activates a template layer under the questionnaire id, an assessment of it is scored with its questions and its rule, a refused publish leaves nothing behind, editing a published version opens a draft and republishing supersedes it, a published version cannot be changed or deleted even with direct SQL, archive and duplicate, tenant isolation, roles). Phase 3 (15 integration tests): the roster (unique names and e-mail addresses, scope by organization, audited reads), starting a run (only active people, only chosen teams, every refusal), the respondent view (own runs only, no scoring detail), drafts with every answer checked when given, required questions, one submit, the columns of the answers table (no person, no time), closing (organization and team runs, free text never scored, a team of two has a run without a score), a run closed after its questionnaire was republished (explicit pinned version), and the role matrix (managers, respondents, other roles, anonymous, a token without an e-mail).

Added with platform administration (decision D46): 10 backend integration tests (a platform administrator creates and lists tenants; ids are trimmed; a taken id is refused and changes nothing; ids and names that are not allowed are refused; the five tenant roles get 403 and anonymous callers 401; a token with a tenant role cannot use the platform endpoints; the platform role reaches nothing tenant-scoped; a created tenant scores assessments at once and sees only its own data; creation and list reads are audited without the name and not logged; audit rows without a tenant are invisible to tenants), the migration count, 41 frontend unit tests (token decoding without a tenant and the role not granted by any tenant role, route guard and home redirect, navigation, the list with its states, filter and refresh, the form with every validation, taken-id, server-error, double-submit and cancel cases, the sign-in return address) and 7 browser scenarios with a real identity provider (`platform.spec`: landing and navigation, creating a tenant and finding it through the list and the API, refusals on the field, tenant roles locked out in the interface and the API, the platform role locked out of tenant data, accessibility and phone width). The browser test found a real defect: everyone was sent to the dashboard after sign-in, which would have shown the platform administrator an empty page.

Added with the configuration file: 14 tests of the bundled initial configuration (it parses and passes every domain validation; it uses only catalog identifiers and never exceeds the specification's suggested maxima; every action, question, signal, pattern and penalty has reader text; the best answers give 100 with no findings; the worst give 0 with all 16 patterns and stay within 0 to 100; each enabled pattern is raised by its own answers and its penalty deducts the documented amount; a two-signal pattern needs both signals; overlapping decision findings deduct only the larger; the cross-dimensional pattern lowers only the overall score; the imbalance is reported and never deducted; an incomplete dimension gives no score; counts beyond the range are clamped) and 9 integration tests of the loader against a real database (activates the file on an empty database, leaves an unchanged file alone, ignores formatting, leaves a different active version alone unless told to synchronize, archives the old version when synchronizing, stops with the reason for an invalid or missing file, and scores assessments, including member answers of teams, through the HTTP API with the bundled file). The real application was also started without the dev profile on an empty database: it activated version 1, became healthy, and a restart left it unchanged.

Added with answers from several people: aggregation (median, mean, even and odd counts, direction, order independence, identical answers, minimum responses, declared and unknown counts, signal share, mixed scopes, duplicates, scope validation), the participation factor of confidence, the configuration block, the engine over member answers (a team below the minimum has no score, organization and teams from the same answers, identical results in any order, one answer per question scoring exactly as before), a migration test that upgrades a populated version 5 database to version 6, and API tests for the scoped request (organization and team runs, small teams, answers shown as groups, history unaffected, scoring again reuses and recalculating reproduces, tenant isolation of team runs, nothing identifying a respondent in storage, trace or logs, a required question with too few team answers).

Notable groups: domain unit tests for every pipeline stage (normalization strategies, coverage, patterns and the condition evaluator, every penalty strategy and stacking policy, imbalance, overall score, interpretation, explainability); a 500-input sweep of the global invariants (`ScoringInvariantSweepTest`); `ArchitectureTest` (8 layering rules); configuration parser, merger and canonical-hash tests; PostgreSQL integration tests for schema and migrations on a clean database, configuration lifecycle and precedence, run persistence (rollback, concurrency, idempotency, immutability triggers), the REST API (formats, roles, tenant isolation, errors, redaction), observability (metrics names, spans, structured logs, audit), history, comparison, trends, catalog, recommendations, source answers, query count (15 statements per run for 5 or 55 answers), reproducibility across tenants and months, and an end-to-end configuration journey. Token validation has its own unit tests (validity period, issuer, audience).

Engine performance baseline (`EnginePerformanceBaselineTest`): 0.77 ms per run for 55 questions, 4.4 ms for 1005 questions (linear). V3 section 35.1 defines no absolute latency target.

## Frontend unit and component tests (391)

Added with the restyle and the new screens: tests of the theme store (default, switch, persistence, storage refused), the layout (brand, sidebar, role-based navigation, theme toggle), the design tokens (both themes complete, every text and background pair at 4.5:1, chart colours as custom properties, no colour literal or stock palette class in any component), the builder model, the questionnaire library, the new-questionnaire page, the builder (adding, changing, moving and removing questions and options, rules, save, publish with and without unsaved changes, a refused publish listing every problem, read-only and archived states), the condition editor (comparisons, groups, conversion between them, depth limit, read only), people, the three run pages (list, start, detail with the confirmation before closing), my assessments, the answer form for every question kind (drafts, required questions, submitted and closed states, server refusals), the route guards and the respondent principal. Statements 92.2 %, branches 82.2 %, functions 88.4 %, lines 94.1 %; the figures of earlier runs were not produced under identical conditions, so compare the covered code, not the percentages.

Earlier figures follow.

Coverage with `vitest --coverage` (v8), measured again after answers from several people were added: statements 92.5 %, branches 82.7 %, functions 90.1 %, lines 95.5 %. The first release run reported 82.1 % statements; the two figures were not produced under identical conditions and I did not investigate the difference, so compare the covered code, not the percentages.

The new groups: response summaries and their validation, the combination panel, the scope switcher, source answers for member groups, the dashboard scoped to a team (including a team that was not scored and one below the minimum), the explanation and the pattern confidence for team runs.

Groups: presentation helpers and error mapping; the API client against a mock server (headers, query building, error bodies, 401 handling); token decoding and the role helpers; route guards and the layout; response validation; the trace parser; condition wording; every dashboard section including loading, empty, error, retry, redaction and filtering states; the waterfall; the explanation, dimension, pattern, penalty and comparison pages including role differences and the form validation.

## End to end (67 tests, real stack)

Added with questionnaires, runs and respondents: `assessments` (8 tests, `org-assess` as a scratch organization of its own) and 3 accessibility tests (the sign-in page and every signed-in screen in both themes, and the theme switch from the keyboard). The journey: an administrator builds a questionnaire in the browser, is told what is missing, adds a rule and publishes; builds a team and its five people once; starts a run and sees nobody has started; a respondent signs in, sees only their own assessments, is blocked by a required question, saves a draft, reloads and continues, and submits; the four other respondents answer; the administrator closes the run, the organization result is scored (Team Health 25, the rule's pattern matched) and the explanation opens. Further checks: the participant list holds only name, address, team and status; a respondent token gets 403 on every manager endpoint; a person not on the roster gets 404. Axe (WCAG 2.2 AA) scans the library, the new-questionnaire page, people, the run list, starting a run, an open and a closed run, the builder (read only, and as a draft with every kind of question and a rule), my assessments and the answer form with a missing answer, in the dark and the light theme, with no horizontal scrolling at 390 px. The suite found the builder overflow (fixed) and showed that a test must not write into the seeded organizations.

Earlier suites follow.

| Spec | Tests | What it proves |
|---|---|---|
| `smoke` | 4 | OIDC sign-in with PKCE, organizations from the token, sign-out, unknown address |
| `dashboard` | 6 | score, four dimensions, findings, penalties, recommendations, trends, severity filter in the URL, analyst sees evidence values, viewer does not, another tenant sees nothing, accessible data tables |
| `explainability` | 8 | explanation reaches the same final score as the dashboard; dimension drill-down with source answer for an analyst and none for a viewer; pattern, penalty and comparison pages; another tenant gets "not found"; viewer explanation redacted |
| `journey` | 1 | the ten steps of the brief in order on deterministic data (81.25, 87.50, 67.00, and 67.00 on another template version) including a comparable and a non-comparable comparison |
| `invariants` | 4 | every seeded run satisfies the mathematical invariants; suppressed penalties never reach a score; re-scoring reuses the run and recalculation reproduces everything; too little data gives no score |
| `security` | 6 | tenant isolation through API and UI, organization limits, role matrix, viewer redaction, anonymous access, role-aware interface |
| `accessibility` | 6 | axe (WCAG 2.2 AA tags) on the login page and every signed-in screen; keyboard-only use; colour never the only signal; no horizontal scrolling at 390 px |
| `scopes` | 4 | answers from members of three teams in the scratch organization: the dashboard switches between the organization and each team and shows each score as the API returned it; a team below the minimum shows no number and says why; history, trends and the comparison page never include a team run; member answers appear only as sorted groups and a small group only as a count; axe (WCAG 2.2 AA) and no horizontal scrolling at 390 px on the new views |
| `hardening` | 6 | security headers, caching, no CSP violation or console error in a full journey, tokens only in tab storage, no internals in error bodies, no shared caching of API responses |
| `performance` | 4 | JavaScript budgets, paint timings, API latency, scoring latency |

Invariants checked on real responses (`e2e/invariants.ts`): normalized values, dimension scores and the LIS stay in 0 to 100; a penalty never raises a score; adjusted is raw minus the effective penalty (never below zero); candidate equals effective plus suppressed; only applied penalties move scores and the aggregated deduction equals the sum of the applied ones (12, not 17, when a 5 is suppressed); a dimension with no applied penalty keeps its raw score; the overall penalty is applied once; the final score never exceeds the weighted score; identical inputs and configuration give an identical result.

## Performance measurements (localhost, one machine)

| Measure | Value | Budget |
|---|---|---|
| Login page JavaScript transferred (gzip) | 182 kB in 3 files | below 220 kB |
| Login first contentful paint | 200 ms | below 2.5 s |
| Dashboard additional JavaScript (charts, loaded on demand) | 153 kB | total below 330 kB |
| Dashboard largest contentful paint | 260 ms | below 4 s |
| API calls for one dashboard view | 9 | at most 12 |
| Read endpoints behind the dashboard | median 8 ms, p95 13 ms (35 calls) | p95 below 500 ms |
| Scoring and storing one assessment | 32 ms | below 1.5 s |

These are regression budgets on a fast local network; they say nothing about a production deployment. The initial bundle was 957 kB before splitting; the dashboard route, the chart code and a stable framework chunk are now split, so the login page no longer downloads charts.

## Dependency and security checks

- `npm audit`: 0 vulnerabilities. One advisory (minimatch denial of service, 13 moderate findings through the lint and OpenAPI tooling, development only) was resolved by pinning `minimatch` to a patched version with an npm override; lint, tests, build and API type generation were re-run afterwards.
- Backend dependencies: **not scanned.** The OWASP dependency-check run was attempted and stopped after the NVD download stalled without an API key; there is no result to report (see `KNOWN_LIMITATIONS.md`, item 11).
- Static security review, with a test for each item: tokens only in tab session storage and never in local storage or cookies; no scoring data or raw answers in client storage; a Content Security Policy without `unsafe-inline` scripts and with `connect-src` limited to the app and the identity provider; no stack traces, framework names or SQL in error bodies; tenant taken only from the token; answers and explanation reads audited without content; issuer and audience of access tokens validated when configured.

## Manual accessibility checklist

Automated checks find only part of the problems, so these were reviewed by reading the markup and exercising the screens with the keyboard; they are not a substitute for an assistive-technology audit:

- Headings form a consistent outline on every page (one `h1`, sections `h2`, items `h3` to `h4`).
- Every chart has a text summary and a "View the data" table; the LIS gauge, waterfall and bars have `role="img"` with a full label.
- Severity, penalty state and condition outcome are written out and given a shape; colour is never the only signal.
- Disclosures (`details`), evidence toggles and filters expose their state (`aria-expanded`, `aria-pressed`) and work with Enter and Space.
- A skip link is the first tab stop; focus is visible; reduced-motion is respected.
- Not covered: screen-reader testing with NVDA, JAWS or VoiceOver, zoom to 400 %, and Windows high-contrast mode.
